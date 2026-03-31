import { llmOrchestrator, type ConversationTurn } from '../llm/orchestrator.js';
import { handwritingRenderer } from '../renderer/handwriting.js';
import { logger } from '../utils/logger.js';
import { createCanvas, loadImage } from 'canvas';

async function scaleDownImage(base64: string, scale: number): Promise<string> {
  const imgData = `data:image/png;base64,${base64}`;
  const img = await loadImage(imgData);
  const newWidth = Math.floor(img.width * scale);
  const newHeight = Math.floor(img.height * scale);
  const canvas = createCanvas(newWidth, newHeight);
  const ctx = canvas.getContext('2d');
  ctx.drawImage(img, 0, 0, newWidth, newHeight);
  return canvas.toDataURL('image/png').substring(22);
}

export interface StreamCoordinator {
  processAndStream(
    sessionId: string,
    imageBase64: string,
    screenWidth: number,
    onToken: (token: string) => void,
    onRenderChunk: (base64: string, progress: number) => void,
    onComplete: (duration: number) => void,
    onError: (error: string) => void,
    persona?: string,
    history?: ConversationTurn[],
    onCancel?: () => boolean
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
    onError: (error: string) => void,
    persona: string = 'tom',
    history: ConversationTurn[] = [],
    onCancel?: () => boolean
  ): Promise<void> {
    const timer = logger.startTimer('stream-coordination', sessionId);
    const startTime = Date.now();
    const TIMEOUT_MS = 120000;

    try {
      logger.info(sessionId, 'Starting stream coordination', { persona, timeout: TIMEOUT_MS });

      // Send clear signal first
      onRenderChunk('', 0);

      let fullText = '';
      let transcription = '';
      let hasReceivedToken = false;
      // Font height = 60% of line spacing (150px) = 90px to prevent overflow with italic font
      const fontSize = 90;
      // Disable scaling - rendered images should be full size for readability
      const renderScale = 1.0;

      const stream = llmOrchestrator.processImageStream(sessionId, imageBase64, persona, history, onCancel);
      
      for await (const event of stream) {
        const elapsed = Date.now() - startTime;
        
        if (elapsed > TIMEOUT_MS) {
          logger.warn(sessionId, 'Stream timeout exceeded', { elapsed, hasReceivedToken });
          onError(`Request timed out after ${TIMEOUT_MS / 1000} seconds. Please try again.`);
          return;
        }

        switch (event.type) {
          case 'token':
            if (!event.data) continue;
            hasReceivedToken = true;
            fullText += event.data;
            break;

          case 'complete':
            const duration = Date.now() - startTime;

            // Extract transcription from the full text
            const transMatch = fullText.match(/\[TRANSCRIPTION\]([\s\S]+)\[\/TRANSCRIPTION\]/);
            if (transMatch) {
              transcription = transMatch[1].trim().replace(/\n+/g, ' ');
              logger.info(sessionId, 'Transcription extracted', { transcriptionLength: transcription.length, transcription });
              
              // Send transcription as a separate message
              onToken(JSON.stringify({ type: 'transcription', text: transcription }));
            } else {
              logger.warn(sessionId, 'No transcription found in response');
            }

            // Extract response text (after transcription)
            const TRANSCRIPTION_END = '[/TRANSCRIPTION]';
            const transEndIndex = fullText.indexOf(TRANSCRIPTION_END);
            let responseText = '';
            if (transEndIndex !== -1) {
              responseText = fullText.substring(transEndIndex + TRANSCRIPTION_END.length).trim();
            }

            // Render all words at once
            if (responseText.length > 0) {
              const words = responseText.split(/\s+/).filter((w: string) => w.length > 0);
              
              // Pre-process: attach standalone punctuation to adjacent words
              const processedWords: string[] = [];
              for (const word of words) {
                if (/^[.,!?;:"'()]+$/.test(word) && processedWords.length > 0) {
                  processedWords[processedWords.length - 1] = processedWords[processedWords.length - 1] + word;
                } else {
                  processedWords.push(word);
                }
              }

              // Render each word
              let wordIndex = 0;
              for (const word of processedWords) {
                try {
                  const results: { base64: string; width: number; height: number }[] = [];
                  for await (const wordResult of handwritingRenderer.renderWordStream(word, { 
                    maxWidth: screenWidth - 80, 
                    fontSize 
                  })) {
                    results.push(wordResult);
                  }
                  let base64 = results[0]?.base64;
                  if (base64 && renderScale < 1) {
                    base64 = await scaleDownImage(base64, renderScale);
                  }
                  if (base64) {
                    onRenderChunk(base64, wordIndex / 100);
                  }
                  wordIndex++;
                } catch (renderError) {
                  logger.error(sessionId, 'Word render failed', renderError as Error);
                }
              }
            }

            logger.info(sessionId, 'Stream coordination completed', {
              totalLength: fullText.length,
              wordCount: responseText.split(/\s+/).filter(w => w.length > 0).length,
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
