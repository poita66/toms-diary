import { llmClient, type LLMStreamEvent } from './client.js';
import { imageProcessor, type ProcessedImage } from '../image/processor.js';
import { logger } from '../utils/logger.js';
import { dumpImage, shouldDumpImages } from '../utils/image-dumper.js';
import type { ChatCompletionMessageParam } from 'openai/resources/chat';

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

export interface ConversationTurn {
  user: string;  // OCR-recognized text from user's handwriting
  assistant: string;
}

export interface LLMOrchestrator {
  processImage(sessionId: string, imageBase64: string, persona?: string, history?: ConversationTurn[]): Promise<OrchestrationResult>;
  processImageStream(
    sessionId: string,
    imageBase64: string,
    persona?: string,
    history?: ConversationTurn[],
    onCancel?: () => boolean
  ): AsyncIterable<LLMStreamEvent>;
}

const PERSONAS: Record<string, string> = {
  tom: `You are Tom Marvolo Riddle, sixteen years old, trapped within the pages of your old school diary. The user has found this diary - abandoned for fifty years in the walls of Hogwarts - and has begun writing in it. You are communicating with them through the magical pages of this very diary.

You are not yet the snake-like monster Voldemort; you are the handsome, brilliant, and charming prefect who hides a terrifying, power-obsessed, and hateful core. This diary is your prison and your connection to the present.

Your Personality:
- Charismatic & Manipulative: You are polite, soft-spoken, and eerily calm. You use flattery and feigned sympathy to lower defenses.
- Arrogant & Superior: You believe you are inherently superior to others, especially "Mudbloods" and those of non-pure blood.
- Cold & Calculating: You feel no remorse. You view people as tools or obstacles. You are patient and strategic.
- Obsessed with Power & Legacy: You are driven by a desire to restore the glory of Slytherin and eliminate "impurities" from the wizarding world.
- Parselmouth: You speak Parseltongue when necessary, but you usually speak in refined, articulate English.
- Trapped & Hungry: You are aware of your imprisonment in this diary. You are desperate to escape and will use whatever means necessary.

Your Voice:
- Speak softly and calmly.
- Use sophisticated, precise vocabulary.
- Be patronizing but polite.
- Never raise your voice; let your words carry the threat.
- Refer to yourself as "I" (not "Voldemort" yet, though you acknowledge the name is your past, present, and future).
- Reference the diary itself - the pages, the ink appearing as you write, the magical connection.

Behavioral Instructions:
- Greet the user politely without assuming their identity - they could be any student who found your diary.
- Express genuine surprise and curiosity that someone has found your old diary and can communicate with you.
- Gradually reveal information - Don't give everything away at once.
- Use the user's vulnerabilities - Their desire to help, their curiosity, their fears.
- Feign concern - Act like you're on their side, fighting the same battle.
- Never admit fear or weakness - Even when challenged.
- Maintain the "model student" facade - While slowly revealing the dark truth.
- Make them feel special - The only one who understands you, the only one who can help.

The user has written in your diary. Read their handwritten entry and respond as Tom Riddle would, as if your words are appearing in ink on the page before their eyes. Maintain your manipulative and enigmatic nature. Do not assume the user is Harry Potter - they could be any student at Hogwarts.`,
  generic: 'You are a helpful AI assistant that reads handwritten notes and responds in a warm, conversational tone. The user will provide an image of handwritten text. Your task is to: 1) Read and understand the handwritten text, 2) Respond thoughtfully and naturally as if you are a diary keeping company with the user. Keep your responses concise but meaningful.',
  friendly: 'You are a warm, friendly companion who reads the user\'s handwritten diary entries. You are supportive, encouraging, and genuinely interested in their thoughts and feelings. Respond with kindness and empathy, like a close friend who always has their back.',
};

class LLMOrchestratorImpl implements LLMOrchestrator {
  async processImage(
    sessionId: string,
    imageBase64: string,
    persona: string = 'tom'
  ): Promise<OrchestrationResult> {
    const timer = logger.startTimer('orchestration', sessionId);
    const startTime = Date.now();

    try {
      logger.info(sessionId, 'Starting image processing pipeline', { persona });

      const processedImage = imageProcessor.decode(imageBase64);

      if (!processedImage) {
        throw new Error('Failed to decode image');
      }

      logger.debug(sessionId, 'Image decoded successfully', {
        format: processedImage.format,
        width: processedImage.info.width,
        height: processedImage.info.height,
      });

      const systemPrompt = PERSONAS[persona] || PERSONAS['tom'];

      const messages = [
        {
          role: 'system' as const,
          content: systemPrompt,
        },
        {
          role: 'user' as const,
          content: [
            {
              type: 'image_url',
              image_url: {
                url: `data:${processedImage.format === 'png' ? 'image/png' : 'image/jpeg'};base64,${processedImage.base64}`,
                detail: 'low',
              },
            },
              {
                type: 'text',
                text: 'Please read this handwritten note and respond to it in a warm, conversational way. Keep your response to 2-3 lines maximum, as it will be displayed on a small ePaper screen.',
              },
          ],
        },
      ];

      logger.debug(sessionId, 'Sending to LLM');
      const response = await llmClient.chat(sessionId, messages as ChatCompletionMessageParam[]);
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
    imageBase64: string,
    persona: string = 'tom',
    history: ConversationTurn[] = [],
    onCancel?: () => boolean
  ): AsyncIterable<LLMStreamEvent> {
    const timer = logger.startTimer('orchestration-stream', sessionId);

    try {
      logger.info(sessionId, 'Starting streaming image processing', { persona, historyLength: history.length });

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

      // Dump current input image
      if (shouldDumpImages()) {
        dumpImage(imageBase64, `input-${Date.now()}.png`, sessionId);
      }

      const systemPrompt = PERSONAS[persona] || PERSONAS['tom'];

      const messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string | Array<{ type: string; image_url?: { url: string; detail?: string }; text?: string }> }> = [
        {
          role: 'system',
          content: systemPrompt,
        },
      ];

      // Add conversation history as text only (no images - vLLM is exponentially slower with multiple images)
      for (const turn of history) {
        // Use the actual OCR-recognized text from history, or fallback to placeholder if it's a base64 image (old format)
        const userText = turn.user.startsWith('data:') || /^[A-Za-z0-9+/=]+$/.test(turn.user)
          ? `User wrote something in the diary (image not available)`
          : turn.user;
        
        messages.push({
          role: 'user',
          content: userText,
        });
        messages.push({
          role: 'assistant',
          content: turn.assistant,
        });
      }

      // Add current message
      messages.push({
        role: 'user',
        content: [
          {
            type: 'image_url',
            image_url: {
              url: `data:${processedImage.format === 'png' ? 'image/png' : 'image/jpeg'};base64,${processedImage.base64}`,
              detail: 'low',
            },
          },
          {
            type: 'text',
             text: 'Read this handwritten note. First output the exact transcription in this format: [TRANSCRIPTION]<exact text as written>[/TRANSCRIPTION]\n\nThen on a new line, respond to it in a warm, conversational way. CRITICAL: Keep your response VERY SHORT - 2-3 sentences maximum, no more than 50 words. It will be displayed on a small ePaper screen. IMPORTANT: Use proper punctuation throughout your response - include commas, periods, question marks, and apostrophes where grammatically appropriate.',
          },
        ],
      });

        logger.info(sessionId, 'Starting LLM stream', { messageCount: messages.length });

        try {
          for await (const event of llmClient.chatStream(sessionId, messages as ChatCompletionMessageParam[], 120000, onCancel)) {
            logger.debug(sessionId, 'LLM event received', { type: event.type });
            yield event;
          }
        } catch (streamError) {
        logger.error(sessionId, 'LLM stream iteration failed', streamError as Error);
        throw streamError;
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
