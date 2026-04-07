import { createCanvas } from 'canvas';
import fs from 'fs';
import path from 'path';

// Load the Caveat font
const fontPath = './fonts/static/Caveat-Regular.ttf';
const fontFamily = 'Caveat';
const fontSize = 90;
const backgroundColor = '#FFFFFF';
const textColor = '#000000';

const testWords = ['shadows', 'Humor', 'write', 'Most', 'fear', 'the', 'you'];

const outputDir = './test-outputs';
if (!fs.existsSync(outputDir)) {
  fs.mkdirSync(outputDir, { recursive: true });
}

for (const word of testWords) {
  const wordCtx = createCanvas(1, 1).getContext('2d');
  wordCtx.font = `${fontSize}px ${fontFamily}`;
  const textMetrics = wordCtx.measureText(word);
  
  const actualWidth = textMetrics.width;
  const italicExtension = actualWidth * 0.25;
  const leftPadding = 15;
  const rightPadding = 15 + italicExtension;
  const paddingY = 10;
  
  const canvasWidth = actualWidth + leftPadding + rightPadding;
  const canvasHeight = fontSize + paddingY * 2;
  
  const wordCanvas = createCanvas(canvasWidth, canvasHeight);
  const drawCtx = wordCanvas.getContext('2d');

  drawCtx.fillStyle = backgroundColor;
  drawCtx.fillRect(0, 0, canvasWidth, canvasHeight);

  drawCtx.fillStyle = textColor;
  drawCtx.font = `${fontSize}px ${fontFamily}`;
  drawCtx.textBaseline = 'alphabetic';

  const x = leftPadding;
  const y = paddingY + fontSize;
  
  drawCtx.fillText(word, x, y);
  
  // Draw a red border to see the canvas edges
  drawCtx.strokeStyle = 'red';
  drawCtx.lineWidth = 2;
  drawCtx.strokeRect(0, 0, canvasWidth, canvasHeight);
  
  // Draw the baseline in blue
  drawCtx.strokeStyle = 'blue';
  drawCtx.beginPath();
  drawCtx.moveTo(0, y);
  drawCtx.lineTo(canvasWidth, y);
  drawCtx.stroke();

  const filename = path.join(outputDir, `${word}-${canvasWidth}x${canvasHeight}.png`);
  const buffer = wordCanvas.toDataURL('image/png').substring(22);
  const imgData = Buffer.from(buffer, 'base64');
  fs.writeFileSync(filename, imgData);
  
  console.log(`Saved ${filename}`);
  console.log(`  Word: "${word}"`);
  console.log(`  actualWidth: ${actualWidth.toFixed(2)}`);
  console.log(`  italicExtension: ${italicExtension.toFixed(2)}`);
  console.log(`  leftPadding: ${leftPadding}`);
  console.log(`  rightPadding: ${rightPadding.toFixed(2)}`);
  console.log(`  canvas: ${canvasWidth.toFixed(2)}x${canvasHeight}`);
  console.log(`  text drawn at: (${x}, ${y})`);
  console.log(`  text extends to: ${x + actualWidth.toFixed(2)}`);
  console.log(`  canvas ends at: ${canvasWidth.toFixed(2)}`);
  console.log(`  margin on right: ${(canvasWidth - (x + actualWidth)).toFixed(2)}`);
  console.log('');
}

console.log('Done! Check ./test-outputs/ directory');
