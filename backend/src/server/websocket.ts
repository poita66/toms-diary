/**
 * WebSocket server for client connections
 */

import { WebSocketServer, WebSocket } from 'ws';
import type { ClientToServerMessage } from '../types/messages.js';

interface ServerOptions {
  port: number;
  host: string;
}

export async function startWebSocketServer(options: ServerOptions): Promise<WebSocketServer> {
  const wss = new WebSocketServer({
    host: options.host,
    port: options.port,
  });

  wss.on('connection', (ws: WebSocket) => {
    console.log('Client connected');

    ws.on('message', (data: Buffer) => {
      try {
        const message: ClientToServerMessage = JSON.parse(data.toString());
        handleMessage(ws, message);
      } catch (error) {
        console.error('Failed to parse message:', error);
        ws.close(4000, 'Invalid message format');
      }
    });

    ws.on('close', () => {
      console.log('Client disconnected');
    });

    ws.on('error', (error: Error) => {
      console.error('WebSocket error:', error);
    });
  });

  return wss;
}

function handleMessage(ws: WebSocket, message: ClientToServerMessage): void {
  switch (message.type) {
    case 'image':
      handleImageMessage(ws, message);
      break;
    default:
      console.warn('Unknown message type:', message.type);
  }
}

function handleImageMessage(ws: WebSocket, message: ClientToServerMessage): void {
  console.log('Received image:', message.metadata);

  // TODO: Process image through VLM pipeline
  // For now, send a placeholder response

  setTimeout(() => {
    if (ws.readyState === 1) {
      ws.send(
        JSON.stringify({
          type: 'complete',
          metadata: {
            sessionId: Date.now().toString(),
            duration: 0,
          },
        })
      );
    }
  }, 100);
}
