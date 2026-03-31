/**
 * Type definitions for the application
 */

export interface ConversationTurn {
  user: string;
  assistant: string;
}

export interface ImageMetadata {
  timestamp: number;
  width: number;
  height: number;
  format: 'png' | 'jpeg';
  persona?: string;
  screenWidth?: number;
}

export interface ImageRequest {
  type: 'image';
  data: string;
  metadata: ImageMetadata;
  history?: ConversationTurn[];
}

export interface PingRequest {
  type: 'ping';
}

export interface CancelRequest {
  type: 'cancel';
}

export type ClientToServerMessage = ImageRequest | PingRequest | CancelRequest;

export interface RenderChunkResponse {
  type: 'render-chunk';
  data: string;
  metadata: {
    chunkIndex: number;
    totalChunks: number;
    progress: number;
  };
}

export interface ClearResponse {
  type: 'clear';
}

export interface ProcessingResponse {
  type: 'processing';
  status: 'received' | 'processing' | 'complete';
  metadata?: Record<string, unknown>;
}

export interface CompleteResponse {
  type: 'complete';
  metadata: {
    sessionId: string;
    duration: number;
  };
}

export interface ErrorResponse {
  type: 'error';
  message: string;
  code: ErrorCode;
  metadata?: Record<string, unknown>;
}

export type ServerToClientMessage =
  | RenderChunkResponse
  | ClearResponse
  | ProcessingResponse
  | CompleteResponse
  | ErrorResponse;

export type ErrorCode =
  | 'INVALID_MESSAGE'
  | 'INVALID_IMAGE'
  | 'PROCESSING_FAILED'
  | 'VLM_ERROR'
  | 'SESSION_EXPIRED'
  | 'RENDERER_ERROR'
  | 'INTERNAL_ERROR';

export interface SessionState {
  sessionId: string;
  createdAt: number;
  lastActivity: number;
  status: 'idle' | 'processing' | 'streaming' | 'error';
  images: Array<{
    timestamp: number;
    size: number;
    processed: boolean;
  }>;
  metadata?: Record<string, unknown>;
}
