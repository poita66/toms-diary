/**
 * Type definitions for LLM interactions
 */

export interface LLMMessageContentPart {
  type: 'image_url' | 'text';
  image_url?: {
    url: string;
    detail?: string;
  };
  text?: string;
}

export interface LLMMessage {
  role: 'system' | 'user' | 'assistant';
  content: string | LLMMessageContentPart[];
}

export interface LLMChatOptions {
  max_completion_tokens?: number;
  chat_template_kwargs?: {
    enable_thinking: boolean;
  };
  include_reasoning?: boolean;
}

export interface LLMStreamOptions {
  stream: true;
  max_completion_tokens?: number;
  chat_template_kwargs?: {
    enable_thinking: boolean;
  };
  include_reasoning?: boolean;
}
