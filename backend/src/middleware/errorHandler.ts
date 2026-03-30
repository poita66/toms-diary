import type { WebSocket } from 'ws';
import type { ErrorResponse, ErrorCode } from '../types/messages.js';
import { logger } from '../utils/logger.js';

interface ErrorContext {
  sessionId?: string;
  operation?: string;
  retryCount?: number;
  [key: string]: unknown;
}

interface ErrorHandler {
  sendError(ws: WebSocket, code: ErrorCode, message: string, context?: ErrorContext): void;
  handleValidationError(ws: WebSocket, error: unknown, context?: ErrorContext): void;
  handleInternalError(ws: WebSocket, error: Error, context?: ErrorContext): void;
  classifyError(error: unknown): ErrorCode;
}

class ErrorHandlerImpl implements ErrorHandler {
  sendError(ws: WebSocket, code: ErrorCode, message: string, context?: ErrorContext): void {
    if (ws.readyState !== 1) {
      logger.warn(context?.sessionId, 'Cannot send error - WebSocket not open', { code });
      return;
    }

    const errorResponse: ErrorResponse = {
      type: 'error',
      code,
      message,
      metadata: context ? { sessionId: context.sessionId, ...context } : undefined,
    };

    try {
      ws.send(JSON.stringify(errorResponse));
      logger.error(context?.sessionId, `Error sent to client: ${code} - ${message}`, undefined, context);
    } catch (sendError) {
      logger.error(context?.sessionId, 'Failed to send error response', sendError as Error, { code });
    }
  }

  handleValidationError(ws: WebSocket, error: unknown, context?: ErrorContext): void {
    const message = error instanceof Error ? error.message : 'Invalid message format';
    this.sendError(ws, 'INVALID_MESSAGE', message, context);
  }

  handleInternalError(ws: WebSocket, error: Error, context?: ErrorContext): void {
    logger.error(context?.sessionId, 'Internal error occurred', error, context);
    this.sendError(ws, 'INTERNAL_ERROR', 'An internal error occurred', {
      ...context,
      errorMessage: error.message,
    });
  }

  classifyError(error: unknown): ErrorCode {
    if (!(error instanceof Error)) {
      return 'INTERNAL_ERROR';
    }

    const message = error.message.toLowerCase();

    if (message.includes('validation') || message.includes('invalid')) {
      return 'INVALID_MESSAGE';
    }

    if (message.includes('image') || message.includes('decode') || message.includes('format')) {
      return 'INVALID_IMAGE';
    }

    if (message.includes('process') || message.includes('pipeline')) {
      return 'PROCESSING_FAILED';
    }

    if (message.includes('vlm') || message.includes('llm') || message.includes('model')) {
      return 'VLM_ERROR';
    }

    if (message.includes('session') || message.includes('expired')) {
      return 'SESSION_EXPIRED';
    }

    if (message.includes('render') || message.includes('draw')) {
      return 'RENDERER_ERROR';
    }

    return 'INTERNAL_ERROR';
  }
}

export const errorHandler = new ErrorHandlerImpl();

export function createErrorContext(sessionId?: string, operation?: string): ErrorContext {
  return {
    sessionId,
    operation,
    timestamp: Date.now(),
  };
}
