import { llmClient, type LLMStreamEvent } from './client.js';
import { imageProcessor, type ProcessedImage } from '../image/processor.js';
import { logger } from '../utils/logger.js';

export interface TranscriptionResult {
  text: string;
  confidence: number;
}

export interface OrchestrationResult {
  transcription: string;
  response: string;
  sessionId: string;
  duration: number;
}

export interface LLMOrchestrator {
  processImage(sessionId: string, imageBase64: string): Promise<OrchestrationResult>;
  processImageStream(
    sessionId: string,
    imageBase64: string
  ): AsyncIterable<LLMStreamEvent>;
}

const SYSTEM_PROMPT =
  'You are a helpful AI assistant that reads handwritten notes and responds in a warm, conversational tone. The user will provide an image of handwritten text. Your task is to: 1) Read and understand the handwritten text, 2) Respond thoughtfully and naturally as if you are a diary keeping company with the user. Keep your responses concise but meaningful.';

class LLMOrchestratorImpl implements LLMOrchestrator {
  async processImage(
    sessionId: string,
    imageBase64: string
  ): Promise<OrchestrationResult> {
    const timer = logger.startTimer('orchestration', sessionId);
    const startTime = Date.now();

    try {
      logger.info(sessionId, 'Starting image processing pipeline');

      const processedImage = imageProcessor.decode(imageBase64);

      if (!processedImage) {
        throw new Error('Failed to decode image');
      }

      logger.debug(sessionId, 'Image decoded successfully', {
        format: processedImage.format,
        width: processedImage.info.width,
        height: processedImage.info.height,
      });

      const messages = [
        {
          role: 'system' as const,
          content: SYSTEM_PROMPT,
        },
        {
          role: 'user' as const,
          content: [
            {
              type: 'image_url',
              image_url: {
                url: `data:${processedImage.format === 'png' ? 'image/png' : 'image/jpeg'};base64,${processedImage.base64}`,
              },
            },
            {
              type: 'text',
              text: 'Please read this handwritten note and respond to it in a warm, conversational way.',
            },
          ],
        },
      ];

      logger.debug(sessionId, 'Sending to LLM');
      const response = await llmClient.chat(sessionId, messages);
      const duration = Date.now() - startTime;

      logger.info(sessionId, 'Orchestration completed', {
        responseLength: response.text.length,
        duration,
      });

      timer.end();

      return {
        transcription: 'Handwritten text recognized',
        response: response.text,
        sessionId: response.sessionId,
        duration,
      };
    } catch (error) {
      logger.error(sessionId, 'Orchestration failed', error as Error);
      timer.end();
      throw error;
    }
  }

  async *processImageStream(
    sessionId: string,
    imageBase64: string
  ): AsyncIterable<LLMStreamEvent> {
    const timer = logger.startTimer('orchestration-stream', sessionId);

    try {
      logger.info(sessionId, 'Starting streaming image processing');

      const processedImage = imageProcessor.decode(imageBase64);

      if (!processedImage) {
        yield {
          type: 'error',
          error: 'Failed to decode image',
        };
        return;
      }

      logger.debug(sessionId, 'Image decoded for streaming', {
        format: processedImage.format,
        width: processedImage.info.width,
        height: processedImage.info.height,
      });

      const messages = [
        {
          role: 'system' as const,
          content: SYSTEM_PROMPT,
        },
        {
          role: 'user' as const,
          content: [
            {
              type: 'image_url',
              image_url: {
                url: `data:${processedImage.format === 'png' ? 'image/png' : 'image/jpeg'};base64,${processedImage.base64}`,
              },
            },
            {
              type: 'text',
              text: 'Please read this handwritten note and respond to it in a warm, conversational way.',
            },
          ],
        },
      ];

      logger.debug(sessionId, 'Starting LLM stream');

      for await (const event of llmClient.chatStream(sessionId, messages)) {
        yield event;
      }
    } catch (error) {
      logger.error(sessionId, 'Stream orchestration failed', error as Error);

      yield {
        type: 'error',
        error: error instanceof Error ? error.message : 'Unknown error',
      };
    } finally {
      timer.end();
    }
  }
}

export const llmOrchestrator = new LLMOrchestratorImpl();
