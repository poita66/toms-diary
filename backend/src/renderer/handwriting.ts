import { createCanvas, type Canvas, type Image, registerFont } from 'canvas';
import { logger } from '../utils/logger.js';
import { join } from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export interface RenderOptions {
  fontFamily?: string;
  fontSize?: number;
  backgroundColor?: string;
  textColor?: string;
  padding?: number;
  maxWidth?: number;
  addVariation?: boolean;
}

export interface RenderResult {
  image: Image;
  width: number;
  height: number;
  base64: string;
  format: 'png' | 'jpeg';
}

export interface TextChunk {
  text: string;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface WordRenderResult {
  base64: string;
  width: number;
  height: number;
  x: number;
  y: number;
}

export interface HandwritingRenderer {
  renderText(text: string, options?: RenderOptions): RenderResult;
  renderTextStream(
    text: string,
    options?: RenderOptions
  ): AsyncIterable<{ chunk: string; base64: string; progress: number }>;
  renderWordStream(
    text: string,
    options?: RenderOptions
  ): AsyncIterable<WordRenderResult>;
  wrapText(text: string, maxWidth: number, options?: RenderOptions): string[];
}

const DEFAULT_FONT = 'Caveat';
const DEFAULT_PADDING = 20;

function calculateFontSize(maxWidth: number): number {
  return Math.floor(maxWidth / 20);
}

class HandwritingRendererImpl implements HandwritingRenderer {
  private canvas: Canvas | null = null;
  private readonly fonts: Map<string, boolean>;
  private fontLoaded: boolean = false;

  constructor() {
    this.canvas = null;
    this.fonts = new Map();
    this.loadCustomFont();
  }

  private loadCustomFont(): void {
    try {
      const fontPath = join(__dirname, '../../fonts/static/Caveat-Regular.ttf');
      registerFont(fontPath, { family: 'Caveat' });
      this.fontLoaded = true;
      logger.info(undefined, 'Custom handwriting font loaded', { font: 'Caveat', path: fontPath });
    } catch (error) {
      logger.error(undefined, 'Failed to load custom font', error as Error);
      this.fontLoaded = false;
    }
  }

  renderText(text: string, options: RenderOptions = {}): RenderResult {
    const timer = logger.startTimer('render-text');

    try {
      const {
        fontFamily = DEFAULT_FONT,
        backgroundColor = '#ffffff',
        textColor = '#000000',
        padding = DEFAULT_PADDING,
        maxWidth = 900,
        addVariation = true,
      } = options;

      const actualFontSize = options.fontSize ?? calculateFontSize(maxWidth);

      const cleanText = this.stripEmojis(text);
      const lines = this.wrapText(cleanText, maxWidth, options);

      const lineHeight = actualFontSize * 1.5;
      const canvas = createCanvas(maxWidth, lines.length * lineHeight + padding * 2);
      const ctx = canvas.getContext('2d');

      ctx.fillStyle = backgroundColor;
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      ctx.fillStyle = textColor;
      ctx.font = `${actualFontSize}px ${fontFamily}`;
      ctx.textBaseline = 'alphabetic';

      let y = padding + actualFontSize;

      for (const line of lines) {
        if (addVariation) {
          const rotation = (Math.random() - 0.5) * 0.02;
          const xOffset = (Math.random() - 0.5) * 2;

          ctx.save();
          ctx.translate(padding + xOffset, y);
          ctx.rotate(rotation);
          ctx.fillText(line, 0, 0);
          ctx.restore();
        } else {
          ctx.fillText(line, padding, y);
        }

        y += lineHeight;
      }

      const base64 = canvas.toDataURL('image/png').substring(22);

      timer.end();

      return {
        image: canvas as unknown as Image,
        width: canvas.width,
        height: canvas.height,
        base64,
        format: 'png',
      };
    } catch (error) {
      logger.error(undefined, 'Text rendering failed', error as Error);
      timer.end();
      throw error;
    }
  }

  private stripEmojis(text: string): string {
    return text.replace(/[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F1E0}-\u{1F1FF}]/gu, '');
  }

  async *renderTextStream(
    text: string,
    options: RenderOptions = {}
  ): AsyncIterable<{ chunk: string; base64: string; progress: number }> {
    const timer = logger.startTimer('render-text-stream');

    try {
      const {
        fontFamily = DEFAULT_FONT,
        backgroundColor = '#ffffff',
        textColor = '#000000',
        padding = DEFAULT_PADDING,
        maxWidth = 900,
        addVariation = true,
      } = options;

      const fontSize = options.fontSize ?? calculateFontSize(maxWidth);

      const words = text.split(' ');
      const totalWords = words.length;
      let currentText = '';
      let chunkIndex = 0;

      for (const word of words) {
        currentText += (chunkIndex > 0 ? ' ' : '') + word;
        chunkIndex++;

        const result = this.renderText(currentText, {
          fontFamily,
          fontSize,
          backgroundColor,
          textColor,
          padding,
          maxWidth,
          addVariation,
        });

        yield {
          chunk: word,
          base64: result.base64,
          progress: chunkIndex / totalWords,
        };
      }
    } catch (error) {
      logger.error(undefined, 'Text stream rendering failed', error as Error);
      throw error;
    } finally {
      timer.end();
    }
  }

  async *renderWordStream(
    text: string,
    options: RenderOptions = {}
  ): AsyncIterable<WordRenderResult> {
    const timer = logger.startTimer('render-word-stream');

    try {
      const {
        fontFamily = DEFAULT_FONT,
        backgroundColor = '#ffffff',
        textColor = '#000000',
        padding = DEFAULT_PADDING,
        maxWidth = 900,
        addVariation = true,
      } = options;

      // Validate maxWidth to prevent canvas size errors
      const validatedMaxWidth = Math.min(maxWidth, 32767);
      if (validatedMaxWidth !== maxWidth) {
        logger.warn(undefined, 'maxWidth exceeded canvas limit, clamping', { original: maxWidth, clamped: validatedMaxWidth });
      }

      const fontSize = options.fontSize ?? calculateFontSize(validatedMaxWidth);

      const canvas = createCanvas(validatedMaxWidth, 200);
      const ctx = canvas.getContext('2d');
      ctx.font = `${fontSize}px ${fontFamily}`;

      const words = text.split(' ');
      let currentX = padding;
      let currentY = padding + fontSize;
      const lineHeight = fontSize * 1.5;

       for (const word of words) {
        const wordWidth = ctx.measureText(word).width;
        const wordHeight = fontSize;

        const wordCtx = createCanvas(1, 1).getContext('2d');
        wordCtx.font = `${fontSize}px ${fontFamily}`;
        const textMetrics = wordCtx.measureText(word);
        
        const actualWidth = textMetrics.width;
        
        // Italic fonts extend beyond measured width due to slant
        // Caveat has ~12 degree italic angle, so add fixed extra width
        const leftPadding = 15;
        const rightPadding = 30; // Fixed padding for consistent spacing
        const topPadding = 10;
        const bottomPadding = 25; // Extra bottom padding for descenders (g, y, p, q, j)
        
        const canvasWidth = actualWidth + leftPadding + rightPadding;
        const canvasHeight = wordHeight + topPadding + bottomPadding;
        
        logger.info(undefined, `Word render: "${word}"`, {
          actualWidth,
          leftPadding,
          rightPadding,
          canvasWidth,
          canvasHeight,
          fontSize
        });
        
        const wordCanvas = createCanvas(canvasWidth, canvasHeight);
        const drawCtx = wordCanvas.getContext('2d');

        drawCtx.fillStyle = backgroundColor;
        drawCtx.fillRect(0, 0, canvasWidth, canvasHeight);

        drawCtx.fillStyle = textColor;
        drawCtx.font = `${fontSize}px ${fontFamily}`;
        drawCtx.textBaseline = 'alphabetic';

        const x = leftPadding;
        const y = topPadding + wordHeight;

        if (addVariation) {
          const rotation = (Math.random() - 0.5) * 0.02;
          const xOffset = (Math.random() - 0.5) * 2;

          drawCtx.save();
          drawCtx.translate(x + xOffset, y);
          drawCtx.rotate(rotation);
          drawCtx.fillText(word, 0, 0);
          drawCtx.restore();
        } else {
          drawCtx.fillText(word, x, y);
        }

        const base64 = wordCanvas.toDataURL('image/png').substring(22);

        yield {
          base64,
          width: wordCanvas.width,
          height: wordCanvas.height,
          x: currentX,
          y: currentY,
        };

        currentX += wordWidth + 10;

        if (currentX + wordWidth > validatedMaxWidth - padding) {
          currentX = padding;
          currentY += lineHeight;
        }
      }
    } catch (error) {
      logger.error(undefined, 'Word stream rendering failed', error as Error);
      throw error;
    } finally {
      timer.end();
    }
  }

  wrapText(text: string, maxWidth: number, options: RenderOptions = {}): string[] {
    const fontFamily = options.fontFamily ?? DEFAULT_FONT;
    const fontSize = options.fontSize ?? calculateFontSize(maxWidth);

    const canvas = createCanvas(maxWidth, 200);
    const ctx = canvas.getContext('2d');
    ctx.font = `${fontSize}px ${fontFamily}`;

    const paragraphs = text.split('\n');
    const lines: string[] = [];

    for (const paragraph of paragraphs) {
      const words = paragraph.trim().split(' ');

      if (words.length === 0) {
        lines.push('');
        continue;
      }

      let currentLine = words[0];

      for (let i = 1; i < words.length; i++) {
        const word = words[i];
        const testLine = currentLine + ' ' + word;
        const width = ctx.measureText(testLine).width;

        if (width < maxWidth - 20) {
          currentLine = testLine;
        } else {
          lines.push(currentLine);
          currentLine = word;
        }
      }

      if (currentLine) {
        lines.push(currentLine);
      }
    }

    return lines;
  }
}

export const handwritingRenderer = new HandwritingRendererImpl();
