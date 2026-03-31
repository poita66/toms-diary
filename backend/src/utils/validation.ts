import { z } from 'zod';
import type {
  ClientToServerMessage,
  ServerToClientMessage,
  ImageRequest,
  PingRequest,
  CancelRequest,
  RenderChunkResponse,
  ProcessingResponse,
  CompleteResponse,
  ErrorResponse,
  ErrorCode,
} from '../types/messages.js';

export const ImageMetadataSchema = z.object({
  timestamp: z.number().int().positive(),
  width: z.number().int().positive().max(10000),
  height: z.number().int().positive().max(10000),
  format: z.enum(['png', 'jpeg']),
});

export const ConversationTurnSchema = z.object({
  user: z.string(),
  assistant: z.string(),
});

export const ImageRequestSchema = z.object({
  type: z.literal('image'),
  data: z.string().min(1, 'Image data is required'),
  metadata: ImageMetadataSchema,
  history: z.array(ConversationTurnSchema).optional(),
});

export const PingRequestSchema = z.object({
  type: z.literal('ping'),
});

export const CancelRequestSchema = z.object({
  type: z.literal('cancel'),
});

export const ClientMessageSchema = z.discriminatedUnion('type', [
  ImageRequestSchema,
  PingRequestSchema,
  CancelRequestSchema,
]);

export const RenderChunkResponseSchema = z.object({
  type: z.literal('render-chunk'),
  data: z.string(),
  metadata: z.object({
    chunkIndex: z.number().int().nonnegative(),
    totalChunks: z.number().int().positive(),
    progress: z.number().min(0).max(1),
  }),
});

export const ProcessingResponseSchema = z.object({
  type: z.literal('processing'),
  status: z.enum(['received', 'processing', 'complete']),
  metadata: z.record(z.unknown()).optional(),
});

export const CompleteResponseSchema = z.object({
  type: z.literal('complete'),
  metadata: z.object({
    sessionId: z.string().uuid(),
    duration: z.number().int().nonnegative(),
  }),
});

export const ErrorResponseSchema = z.object({
  type: z.literal('error'),
  message: z.string().min(1),
  code: z.enum([
    'INVALID_MESSAGE',
    'INVALID_IMAGE',
    'PROCESSING_FAILED',
    'VLM_ERROR',
    'SESSION_EXPIRED',
    'RENDERER_ERROR',
    'INTERNAL_ERROR',
  ]),
  metadata: z.record(z.unknown()).optional(),
});

export const ServerMessageSchema = z.discriminatedUnion('type', [
  RenderChunkResponseSchema,
  ProcessingResponseSchema,
  CompleteResponseSchema,
  ErrorResponseSchema,
]);

export function validateClientMessage(
  data: unknown
): { success: true; data: ImageRequest | PingRequest | CancelRequest } | { success: false; error: z.ZodError } {
  const result = ClientMessageSchema.safeParse(data);
  if (!result.success) {
    return { success: false, error: result.error };
  }
  return { success: true, data: result.data };
}

export function validateServerMessage(
  data: unknown
): { success: true; data: ServerToClientMessage } | { success: false; error: z.ZodError } {
  const result = ServerMessageSchema.safeParse(data);
  if (!result.success) {
    return { success: false, error: result.error };
  }
  return { success: true, data: result.data };
}

export function isImageRequest(msg: ClientToServerMessage): msg is ImageRequest {
  return msg.type === 'image';
}

export function isPingRequest(msg: ClientToServerMessage): msg is PingRequest {
  return msg.type === 'ping';
}

export function isCancelRequest(msg: ClientToServerMessage): msg is CancelRequest {
  return msg.type === 'cancel';
}

export function isValidErrorCode(code: unknown): code is ErrorCode {
  return typeof code === 'string' && [
    'INVALID_MESSAGE',
    'INVALID_IMAGE',
    'PROCESSING_FAILED',
    'VLM_ERROR',
    'SESSION_EXPIRED',
    'RENDERER_ERROR',
    'INTERNAL_ERROR',
  ].includes(code);
}
