import dotenv from 'dotenv';
dotenv.config();

import OpenAI from 'openai';
import type { ChatCompletionMessageParam, ChatCompletionCreateParamsNonStreaming, ChatCompletionCreateParamsStreaming } from 'openai/resources/chat';
import { logger } from '../utils/logger.js';
import { loadConfig } from '../config.js';

const config = loadConfig();

export interface LLMResponse {
  text: string;
  sessionId: string;
  duration: number;
}

export interface LLMStreamEvent {
  type: 'token' | 'complete' | 'error';
  data?: string;
  error?: string;
}

export interface LLMClient {
  chat(sessionId: string, messages: ChatCompletionMessageParam[]): Promise<LLMResponse>;
  chatStream(sessionId: string, messages: ChatCompletionMessageParam[], timeoutMs?: number, onCancel?: () => boolean): AsyncIterable<LLMStreamEvent>;
}

class LLMClientImpl implements LLMClient {
  private client: OpenAI;
  private readonly model: string;

  constructor() {
    this.model = config.vllmModel;

    this.client = new OpenAI({
      baseURL: config.vllmBaseUrl,
      apiKey: config.vllmApiKey || 'placeholder',
    });

    logger.info(undefined, 'LLM client initialized', {
      baseUrl: config.vllmBaseUrl,
      model: this.model,
    });
  }

  async chat(
    sessionId: string,
    messages: ChatCompletionMessageParam[]
  ): Promise<LLMResponse> {
    const timer = logger.startTimer('llm-chat', sessionId);
    const startTime = Date.now();

    try {
      logger.debug(sessionId, 'Sending message to LLM', {
        messageCount: messages.length,
        model: this.model,
      });

      const response = await this.client.chat.completions.create({
        model: this.model,
        messages,
        max_completion_tokens: 16 * 1024,
        chat_template_kwargs: { enable_thinking: false },
        include_reasoning: false,
      } as any);

      const text = response.choices[0]?.message?.content || '';
      const duration = Date.now() - startTime;

      logger.info(sessionId, 'LLM response received', {
        length: text.length,
        duration,
      });

      timer.end();

      return {
        text,
        sessionId,
        duration,
      };
    } catch (error) {
      logger.error(sessionId, 'LLM chat failed', error as Error);
      timer.end();
      throw error;
    }
  }

  async *chatStream(
    sessionId: string,
    messages: ChatCompletionMessageParam[],
    timeoutMs: number = 120000,
    onCancel?: () => boolean
  ): AsyncIterable<LLMStreamEvent> {
    const timer = logger.startTimer('llm-chat-stream', sessionId);

    try {
      logger.info(sessionId, 'Starting LLM stream', {
        messageCount: messages.length,
        model: this.model,
        timeoutMs,
      });

      const checkInterval = 500;
      let stream: any;
      let isCancelled = false;
      let cancelInterval: NodeJS.Timeout | null = null;

      if (onCancel) {
        cancelInterval = setInterval(() => {
          if (onCancel()) {
            isCancelled = true;
            if (cancelInterval) {
              clearInterval(cancelInterval);
            }
          }
        }, checkInterval);
      }

      try {
        stream = await Promise.race([
          this.client.chat.completions.create({
            model: this.model,
            messages,
            stream: true,
            max_completion_tokens: 16 * 1024,
            chat_template_kwargs: { enable_thinking: false },
            include_reasoning: false,
          } as any),
          new Promise<never>((_, reject) => {
            setTimeout(() => {
              reject(new Error(`LLM stream timeout after ${timeoutMs}ms`));
            }, timeoutMs);
          }),
        ]);
      } finally {
        if (cancelInterval) {
          clearInterval(cancelInterval);
        }
      }

      if (isCancelled) {
        logger.info(sessionId, 'Stream cancelled before starting');
        return;
      }

      let fullText = '';
      let chunkCount = 0;
      const streamStartTime = Date.now();

      for await (const chunk of stream) {
        const elapsed = Date.now() - streamStartTime;
        chunkCount++;

        if (chunkCount === 1) {
          logger.info(sessionId, 'First chunk received', { elapsed });
        }

        const token = chunk.choices[0]?.delta?.content || '';

        if (token) {
          fullText += token;
          yield {
            type: 'token',
            data: token,
          };
        }
      }

      logger.info(sessionId, 'Stream loop completed', {
        chunkCount,
        totalElapsed: Date.now() - streamStartTime,
      });

      logger.info(sessionId, 'LLM stream completed', {
        length: fullText.length,
      });

      yield {
        type: 'complete',
      };
    } catch (error) {
      logger.error(sessionId, 'LLM stream failed', error as Error);

      yield {
        type: 'error',
        error: error instanceof Error ? error.message : 'Unknown error',
      };
    } finally {
      timer.end();
    }
  }
}

export const llmClient = new LLMClientImpl();
