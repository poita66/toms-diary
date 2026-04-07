import dotenv from 'dotenv';
dotenv.config();

import OpenAI from 'openai';
import type { ChatCompletion, ChatCompletionChunk } from 'openai/resources/chat';
import { logger } from '../utils/logger.js';
import { loadConfig } from '../config.js';
import type { LLMMessage, LLMChatOptions, LLMStreamOptions } from './types.js';

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
  chat(sessionId: string, messages: LLMMessage[]): Promise<LLMResponse>;
  chatStream(sessionId: string, messages: LLMMessage[], timeoutMs?: number, onCancel?: () => boolean): AsyncIterable<LLMStreamEvent>;
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
    messages: LLMMessage[]
  ): Promise<LLMResponse> {
    const timer = logger.startTimer('llm-chat', sessionId);
    const startTime = Date.now();

    try {
      logger.debug(sessionId, 'Sending message to LLM', {
        messageCount: messages.length,
        model: this.model,
      });

      const chatOptions: LLMChatOptions = {
        max_completion_tokens: 16 * 1024,
        chat_template_kwargs: { enable_thinking: false },
        include_reasoning: false,
      };

      const response = await this.client.chat.completions.create({
        model: this.model,
        messages,
        ...chatOptions,
      } as Parameters<typeof this.client.chat.completions.create>[0]);

      const chatResponse = response as unknown as ChatCompletion;
      const text = chatResponse.choices[0]?.message?.content || '';
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
    messages: LLMMessage[],
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
        const streamOptions: LLMStreamOptions = {
          stream: true,
          max_completion_tokens: 16 * 1024,
          chat_template_kwargs: { enable_thinking: false },
          include_reasoning: false,
        };

        const streamResponse = await Promise.race<
          ReturnType<typeof this.client.chat.completions.create>
          | Promise<never>
        >([
          this.client.chat.completions.create({
            model: this.model,
            messages,
            ...streamOptions,
          } as Parameters<typeof this.client.chat.completions.create>[0]),
          new Promise<never>((_, reject) => {
            setTimeout(() => {
              reject(new Error(`LLM stream timeout after ${timeoutMs}ms`));
            }, timeoutMs);
          }),
        ]);

        // Type guard to ensure we have a stream (check for [Symbol.asyncIterator])
        if (!('next' in streamResponse)) {
          throw new Error('Expected stream response');
        }

        const stream = streamResponse as unknown as AsyncGenerator<ChatCompletionChunk>;

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
      } finally {
        if (cancelInterval) {
          clearInterval(cancelInterval);
        }
      }
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
