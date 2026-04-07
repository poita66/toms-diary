/**
 * Backend service for Tom's Diary
 */

import dotenv from 'dotenv';
dotenv.config();

import { startWebSocketServer } from './server/websocket.js';
import { loadConfig } from './config.js';

const config = loadConfig();

async function main(): Promise<void> {
  console.log('Starting Tom\'s Diary Backend...');
  console.log(`Environment: ${process.env.NODE_ENV ?? 'development'}`);

  await startWebSocketServer({
    port: config.port,
    host: config.host,
  });

  console.log(`WebSocket server running on ws://${config.host}:${config.port}`);
}

main().catch((error) => {
  console.error('Failed to start server:', error);
  process.exit(1);
});
