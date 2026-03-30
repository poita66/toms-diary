import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { llmClient } from '../llm/client.js';
import { imageProcessor } from '../image/processor.js';
import { handwritingRenderer } from '../renderer/handwriting.js';
import { validateClientMessage } from '../utils/validation.js';
import { sessionManager } from '../session/manager.js';
import { WebSocketServer } from 'ws';

describe('Backend Integration Tests', () => {
  describe('Image Processor', () => {
    it('should validate PNG image data', () => {
      const pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
      const result = imageProcessor.decode(pngBase64);
      
      expect(result).not.toBeNull();
      expect(result?.format).toBe('png');
    });

    it('should handle data URI prefix', () => {
      const dataUri = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
      const result = imageProcessor.decode(dataUri);
      
      expect(result).not.toBeNull();
    });

    it('should reject invalid image data', () => {
      const invalid = 'not-a-valid-base64-image';
      const result = imageProcessor.decode(invalid);
      
      expect(result).toBeNull();
    });
  });

  describe('Validation', () => {
    it('should validate image request', () => {
      const message = {
        type: 'image',
        data: 'test-base64-data',
        metadata: {
          timestamp: Date.now(),
          width: 100,
          height: 100,
          format: 'png' as const,
        },
      };

      const result = validateClientMessage(message);
      
      expect(result.success).toBe(true);
    });

    it('should reject invalid message', () => {
      const invalid = { type: 'unknown' };
      const result = validateClientMessage(invalid);
      
      expect(result.success).toBe(false);
    });
  });

  describe('Handwriting Renderer', () => {
    it('should render text to image', () => {
      const result = handwritingRenderer.renderText('Hello World');
      
      expect(result.base64).toBeTruthy();
      expect(result.format).toBe('png');
      expect(result.width).toBeGreaterThan(0);
      expect(result.height).toBeGreaterThan(0);
    });

    it('should wrap long text', () => {
      const longText = 'This is a very long text that should be wrapped across multiple lines when rendered';
      const lines = handwritingRenderer.wrapText(longText, 200);
      
      expect(lines.length).toBeGreaterThan(1);
    });
  });

  describe('Session Manager', () => {
    let mockWs: any;

    beforeAll(() => {
      mockWs = {
        on: () => {},
        readyState: 1,
      };
    });

    it('should create and retrieve session', () => {
      const sessionId = sessionManager.createSession(mockWs as any);
      const session = sessionManager.getSession(sessionId);

      expect(sessionId).toBeTruthy();
      expect(session).not.toBeNull();
      expect(session?.status).toBe('idle');
    });

    it('should update session', () => {
      const sessionId = sessionManager.createSession(mockWs as any);
      const success = sessionManager.updateSession(sessionId, { status: 'processing' as const });
      const session = sessionManager.getSession(sessionId);

      expect(success).toBe(true);
      expect(session?.status).toBe('processing');
    });

    it('should delete session', () => {
      const sessionId = sessionManager.createSession(mockWs as any);
      const success = sessionManager.deleteSession(sessionId);
      const session = sessionManager.getSession(sessionId);

      expect(success).toBe(true);
      expect(session).toBeNull();
    });
  });

  describe('LLM Client (Integration)', () => {
    it('should connect to vLLM and generate text', async () => {
      const messages = [
        {
          role: 'user' as const,
          content: 'What is 2 + 2? Answer with just the number.',
        },
      ];

      const response = await llmClient.chat('test-session', messages);

      expect(response.text).toBeTruthy();
      expect(response.sessionId).toBe('test-session');
      expect(response.duration).toBeGreaterThan(0);
    }, 30000);

    it('should stream tokens from vLLM', async () => {
      const messages = [
        {
          role: 'user' as const,
          content: 'Say hello briefly.',
        },
      ];

      const tokens: string[] = [];
      let complete = false;

      for await (const event of llmClient.chatStream('test-stream', messages)) {
        if (event.type === 'token' && event.data) {
          tokens.push(event.data);
        }
        if (event.type === 'complete') {
          complete = true;
          break;
        }
      }

      expect(complete).toBe(true);
      expect(tokens.length).toBeGreaterThan(0);
    }, 30000);
  });
});
