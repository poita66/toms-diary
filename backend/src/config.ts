/**
 * Application configuration
 */

export interface Config {
  port: number;
  host: string;
  vllmBaseUrl: string;
  vllmModel: string;
  vllmApiKey: string | null;
  logLevel: string;
}

export function loadConfig(): Config {
  const vllmHost = process.env.VLLM_HOST || 'http://localhost:8000/v1';
  
  return {
    port: parseInt(process.env.PORT || '8080', 10),
    host: process.env.HOST || '0.0.0.0',
    vllmBaseUrl: vllmHost,
    vllmModel: process.env.VLLM_MODEL || 'default',
    vllmApiKey: process.env.VLLM_API_KEY || null,
    logLevel: process.env.LOG_LEVEL || 'info',
  };
}
