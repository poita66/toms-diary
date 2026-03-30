import dotenv from 'dotenv';
dotenv.config();

import { handwritingRenderer } from './dist/renderer/handwriting.js';
import { writeFileSync } from 'fs';

console.log('Generating handwriting samples...\n');

const samples = [
  'Hello! How are you today?',
  'The quick brown fox jumps over the lazy dog.',
  'Dear Diary,\n\nToday was a wonderful day. I learned something new and felt inspired.',
];

samples.forEach((text, index) => {
  console.log(`Rendering sample ${index + 1}...`);
  
  const result = handwritingRenderer.renderText(text, {
    fontSize: 48,
    padding: 30,
    maxWidth: 800,
    addVariation: true,
  });

  const buffer = Buffer.from(result.base64, 'base64');
  const filename = `sample-${index + 1}.png`;
  
  writeFileSync(filename, buffer);
  console.log(`✓ Saved: ${filename} (${result.width}x${result.height})\n`);
});

console.log('✅ All samples generated!');
console.log('Files saved to: ./sample-1.png, ./sample-2.png, ./sample-3.png');
