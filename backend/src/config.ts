/**
 * Application configuration
 */

export interface Config {
  port: number;
  host: string;
  vllmHost: string;
  vllmPort: number;
  vllmModel: string;
  logLevel: string;
}

export function loadConfig(): Config {
  return {
    port: parseInt(process.env.PORT || '8080', 10),
    host: process.env.HOST || '0.0.0.0',
    vllmHost: process.env.VLLM_HOST || 'localhost',
    vllmPort: parseInt(process.env.VLLM_PORT || '8000', 10),
    vllmModel: process.env.VLLM_MODEL || 'Qwen/Qwen3.5-27b-4bit-AWQ',
    logLevel: process.env.LOG_LEVEL || 'info',
  };
}
