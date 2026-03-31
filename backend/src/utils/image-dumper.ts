import fs from 'fs';
import path from 'path';
import { loadConfig } from '../config.js';

const config = loadConfig();

// Create dump directory if it doesn't exist
const DUMP_DIR = './image-dumps';
if (!fs.existsSync(DUMP_DIR)) {
  fs.mkdirSync(DUMP_DIR, { recursive: true });
}

export function dumpImage(base64Data: string, filename: string, sessionId?: string): string {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const sessionPrefix = sessionId ? `${sessionId}-` : '';
  const fullFilename = `${sessionPrefix}${timestamp}-${filename}`;
  const filepath = path.join(DUMP_DIR, fullFilename);
  
  try {
    // Remove data URL prefix if present
    const base64Content = base64Data.replace(/^data:image\/(png|jpeg|jpg);base64,/, '');
    const buffer = Buffer.from(base64Content, 'base64');
    
    fs.writeFileSync(filepath, buffer);
    console.log(`[DUMP] Image saved to ${filepath} (${buffer.length} bytes)`);
    
    return filepath;
  } catch (error) {
    console.error(`[DUMP] Failed to save image:`, error);
    return '';
  }
}

export function shouldDumpImages(): boolean {
  return config.dumpImages;
}
