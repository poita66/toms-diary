import { WebSocketServer, WebSocket } from 'ws';
import { validateClientMessage, isImageRequest, isPingRequest } from '../utils/validation.js';
import { logger } from '../utils/logger.js';
import { sessionManager } from '../session/manager.js';
import { errorHandler, createErrorContext } from '../middleware/errorHandler.js';
import { streamCoordinator } from '../stream/coordinator.js';
import type { ClientToServerMessage } from '../types/messages.js';

interface ServerOptions {
  port: number;
  host: string;
}

interface ActiveSession {
  sessionId: string;
  ws: WebSocket;
  cancelToken: boolean;
}

const activeSessions = new Map<string, ActiveSession>();

export async function startWebSocketServer(options: ServerOptions): Promise<WebSocketServer> {
  const wss = new WebSocketServer({
    host: options.host,
    port: options.port,
  });

  wss.on('connection', (ws: WebSocket) => {
    handleConnection(ws);
  });

  wss.on('error', (error: Error) => {
    logger.error(undefined, 'WebSocket server error', error);
  });

  return wss;
}

function handleConnection(ws: WebSocket): void {
  const sessionId = sessionManager.createSession(ws);

  const activeSession: ActiveSession = {
    sessionId,
    ws,
    cancelToken: false,
  };

  activeSessions.set(sessionId, activeSession);
  logger.info(sessionId, 'Client connected');

  ws.on('message', (data: Buffer) => {
    handleClientMessage(sessionId, ws, data);
  });

  ws.on('close', (code, reason) => {
    activeSession.cancelToken = true;
    activeSessions.delete(sessionId);
    logger.info(sessionId, 'Client disconnected', { code, reason: reason.toString() });
  });

  ws.on('error', (error: Error) => {
    logger.error(sessionId, 'WebSocket error', error);
  });

  ws.on('ping', (data: Buffer) => {
    logger.debug(sessionId, 'Received ping');
    ws.pong(data);
  });

  ws.on('pong', (data: Buffer) => {
    logger.debug(sessionId, 'Received pong', { size: data.length });
  });
}

function handleClientMessage(sessionId: string, ws: WebSocket, data: Buffer): void {
  const timer = logger.startTimer('message-handling', sessionId);

  try {
    const rawData = JSON.parse(data.toString());
    const validation = validateClientMessage(rawData);

    if (!validation.success) {
      errorHandler.handleValidationError(ws, validation.error, createErrorContext(sessionId));
      timer.end();
      return;
    }

    const message: ClientToServerMessage = validation.data;
    logger.debug(sessionId, 'Message received', { type: message.type });

    if (isImageRequest(message)) {
      handleImageMessage(sessionId, ws, message);
    } else if (isPingRequest(message)) {
      handlePingMessage(sessionId, ws);
    } else if ((message as { type: string }).type === 'cancel') {
      handleCancelMessage(sessionId, ws);
    } else {
      logger.warn(sessionId, 'Unknown message type', { type: (message as { type: string }).type });
    }
  } catch (parseError) {
    errorHandler.handleInternalError(ws, parseError as Error, createErrorContext(sessionId, 'parse'));
  }

  timer.end();
}

function handleImageMessage(sessionId: string, ws: WebSocket, message: ClientToServerMessage): void {
  if (!isImageRequest(message)) {
    logger.error(sessionId, 'Invalid image message structure');
    return;
  }

  const activeSession = activeSessions.get(sessionId);
  if (!activeSession) {
    logger.error(sessionId, 'Session not found');
    return;
  }

  activeSession.cancelToken = false;

  logger.info(sessionId, 'Image received', {
    width: message.metadata.width,
    height: message.metadata.height,
    format: message.metadata.format,
    dataSize: message.data.length,
  });

  sessionManager.updateSession(sessionId, {
    status: 'processing',
    images: [
      ...sessionManager.getSession(sessionId)?.images || [],
      {
        timestamp: message.metadata.timestamp,
        size: message.data.length,
        processed: false,
      },
    ],
  });

  sendProcessingStatus(ws, sessionId, 'received');

  const chunkIndex = { current: 0 };
  let accumulatedText = '';

  const screenWidth = message.metadata.screenWidth || message.metadata.width || 1000;
  const persona = message.metadata.persona || 'tom';
  const history = message.history || [];
  
  logger.info(sessionId, 'Starting stream', { historyLength: history.length });
  
  streamCoordinator.processAndStream(
    sessionId,
    message.data,
    screenWidth,
    (token) => {
      if (activeSession?.cancelToken || ws.readyState !== WebSocket.OPEN) return;

      accumulatedText += token;
      logger.debug(sessionId, 'Token received', { length: token.length });
      
      try {
        const tokenMessage = {
          type: 'token' as const,
          data: token,
        };
        ws.send(JSON.stringify(tokenMessage));
      } catch (sendError) {
        logger.error(sessionId, 'Failed to send token', sendError as Error);
      }
    },
    (base64: string, progress: number) => {
      if (activeSession?.cancelToken || ws.readyState !== WebSocket.OPEN) return;

      try {
        // First empty chunk is the clear signal - send as empty render-chunk
        if (base64 === '' && progress === 0) {
          const clearMessage = {
            type: 'render-chunk' as const,
            data: '',
            metadata: {
              chunkIndex: -1,
              totalChunks: 1,
              progress: 0,
            },
          };
          ws.send(JSON.stringify(clearMessage));
          logger.debug(sessionId, 'Clear signal sent as empty render-chunk');
          return;
        }

        const renderMessage = {
          type: 'render-chunk' as const,
          data: base64,
          metadata: {
            chunkIndex: chunkIndex.current++,
            totalChunks: Math.ceil(1 / Math.max(progress, 0.1)),
            progress: Math.min(progress, 1.0),
          },
        };

        ws.send(JSON.stringify(renderMessage));
        logger.debug(sessionId, 'Render chunk sent', { chunk: chunkIndex.current - 1, progress });
      } catch (sendError) {
        logger.error(sessionId, 'Failed to send render chunk', sendError as Error);
      }
    },
    (duration: number) => {
      if (activeSession?.cancelToken || ws.readyState !== WebSocket.OPEN) return;

      try {
        sessionManager.updateSession(sessionId, {
          status: 'idle',
          images: sessionManager.getSession(sessionId)?.images.map(img => ({ ...img, processed: true })) || [],
        });

        const completeMessage = {
          type: 'complete' as const,
          metadata: {
            sessionId,
            duration,
          },
        };

        ws.send(JSON.stringify(completeMessage));
        logger.info(sessionId, 'Processing completed', { duration, totalTextLength: accumulatedText.length });
      } catch (sendError) {
        logger.error(sessionId, 'Failed to send completion', sendError as Error);
      }
    },
    (error: string) => {
      if (activeSession?.cancelToken || ws.readyState !== WebSocket.OPEN) return;

      try {
        sessionManager.updateSession(sessionId, { status: 'error' });
        errorHandler.sendError(ws, 'PROCESSING_FAILED', error, createErrorContext(sessionId));
        logger.error(sessionId, 'Processing failed', undefined, { error });
      } catch (sendError) {
        logger.error(sessionId, 'Failed to send error', sendError as Error);
      }
    },
    persona,
    history,
    () => activeSession?.cancelToken || false
  );
}

function handlePingMessage(sessionId: string, ws: WebSocket): void {
  logger.debug(sessionId, 'Ping received');

  if (ws.readyState === WebSocket.OPEN) {
    try {
      ws.send(
        JSON.stringify({
          type: 'processing',
          status: 'complete',
        })
      );
    } catch (error) {
      logger.error(sessionId, 'Failed to send pong', error as Error);
    }
  }
}

function sendProcessingStatus(ws: WebSocket, sessionId: string, status: 'received' | 'processing' | 'complete'): void {
  if (ws.readyState !== WebSocket.OPEN) return;

  try {
    ws.send(
      JSON.stringify({
        type: 'processing',
        status,
      })
    );
    logger.debug(sessionId, 'Processing status sent', { status });
  } catch (error) {
    logger.error(sessionId, 'Failed to send processing status', error as Error);
  }
}

function handleCancelMessage(sessionId: string, _ws: WebSocket): void {
  logger.info(sessionId, 'Cancel received');
  
  const activeSession = activeSessions.get(sessionId);
  if (activeSession) {
    activeSession.cancelToken = true;
    logger.info(sessionId, 'Session cancelled');
  }
}
