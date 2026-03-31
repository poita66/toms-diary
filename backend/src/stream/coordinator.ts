import { llmOrchestrator } from '../llm/orchestrator.js';
import { handwritingRenderer } from '../renderer/handwriting.js';
import { logger } from '../utils/logger.js';

export interface StreamCoordinator {
  processAndStream(
    sessionId: string,
    imageBase64: string,
    screenWidth: number,
    onToken: (token: string) => void,
    onRenderChunk: (base64: string, progress: number) => void,
    onComplete: (duration: number) => void,
    onError: (error: string) => void
  ): Promise<void>;
}

class StreamCoordinatorImpl implements StreamCoordinator {
  async processAndStream(
    sessionId: string,
    imageBase64: string,
    screenWidth: number,
    onToken: (token: string) => void,
    onRenderChunk: (base64: string, progress: number) => void,
    onComplete: (duration: number) => void,
    onError: (error: string) => void
  ): Promise<void> {
    const timer = logger.startTimer('stream-coordination', sessionId);
    const startTime = Date.now();

    try {
      logger.info(sessionId, 'Starting stream coordination');

      let fullText = '';
      const RENDER_THRESHOLD = 3;

      for await (const event of llmOrchestrator.processImageStream(sessionId, imageBase64)) {
        switch (event.type) {
          case 'token':
            if (!event.data) continue;

            fullText += event.data;

            onToken(event.data);

            if (fullText.split(' ').length >= RENDER_THRESHOLD) {
              try {
                const result = handwritingRenderer.renderText(fullText, { maxWidth: screenWidth - 40 });

                onRenderChunk(result.base64, fullText.length / 200);
              } catch (renderError) {
                logger.error(sessionId, 'Render failed during stream', renderError as Error);
              }
            }

            break;

          case 'complete':
            const duration = Date.now() - startTime;

            if (fullText) {
              try {
                logger.info(sessionId, 'Final render text', { text: fullText.substring(0, 100) + (fullText.length > 100 ? '...' : '') });
                const result = handwritingRenderer.renderText(fullText, { maxWidth: screenWidth - 40 });
                logger.info(sessionId, 'Final render result', { width: result.width, height: result.height });
                onRenderChunk(result.base64, 1.0);
              } catch (renderError) {
                logger.error(sessionId, 'Final render failed', renderError as Error);
              }
            }

            logger.info(sessionId, 'Stream coordination completed', {
              totalLength: fullText.length,
              duration,
            });

            onComplete(duration);
            break;

          case 'error':
            logger.error(sessionId, 'Stream error', undefined, { error: event.error });
            onError(event.error || 'Unknown error');
            break;
        }
      }
    } catch (error) {
      logger.error(sessionId, 'Stream coordination failed', error as Error);
      onError(error instanceof Error ? error.message : 'Unknown error');
    } finally {
      timer.end();
    }
  }
}

export const streamCoordinator = new StreamCoordinatorImpl();
