import dotenv from 'dotenv';
dotenv.config();

import OpenAI from 'openai';
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
  chat(sessionId: string, messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string | Array<{ type: string; image_url?: { url: string }; text?: string }> }>): Promise<LLMResponse>;
  chatStream(sessionId: string, messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string | Array<{ type: string; image_url?: { url: string }; text?: string }> }>): AsyncIterable<LLMStreamEvent>;
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
    messages: Array<{
      role: 'system' | 'user' | 'assistant';
      content: string | Array<{ type: string; image_url?: { url: string }; text?: string }>;
    }>
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
        messages: messages as never,
      });

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
    messages: Array<{
      role: 'system' | 'user' | 'assistant';
      content: string | Array<{ type: string; image_url?: { url: string }; text?: string }>;
    }>
  ): AsyncIterable<LLMStreamEvent> {
    const timer = logger.startTimer('llm-chat-stream', sessionId);

    try {
      logger.debug(sessionId, 'Starting LLM stream', {
        messageCount: messages.length,
        model: this.model,
      });

      const stream = await this.client.chat.completions.create({
        model: this.model,
        messages: messages as never,
        stream: true,
      });

      let fullText = '';

      for await (const chunk of stream) {
        const token = chunk.choices[0]?.delta?.content || '';

        if (token) {
          fullText += token;
          yield {
            type: 'token',
            data: token,
          };
        }
      }

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
