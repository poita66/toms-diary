/**
 * Type definitions for the application
 */

export interface ImageData {
  data: string;
  metadata: {
    timestamp: number;
    width: number;
    height: number;
    format: string;
  };
}

export interface RenderChunk {
  data: string;
  metadata: {
    chunkIndex: number;
    totalChunks: number;
    progress: number;
  };
}

export interface WebSocketMessage {
  type: string;
  data?: string;
  metadata?: Record<string, unknown>;
  message?: string;
  code?: string;
}

export interface ClientToServerMessage {
  type: 'image';
  data: string;
  metadata: {
    timestamp: number;
    width: number;
    height: number;
    format: 'png' | 'jpeg';
  };
}

export interface ServerToClientMessage {
  type: 'render-chunk' | 'complete' | 'error';
  data?: string;
  metadata?: Record<string, unknown>;
  message?: string;
  code?: string;
}
