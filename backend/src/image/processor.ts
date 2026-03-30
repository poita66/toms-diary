import { logger } from '../utils/logger.js';

export interface ImageInfo {
  format: 'png' | 'jpeg';
  width: number;
  height: number;
  size: number;
}

export interface ProcessedImage {
  base64: string;
  format: 'png' | 'jpeg';
  info: ImageInfo;
}

export interface ImageProcessor {
  decode(base64Data: string): ProcessedImage | null;
  validate(base64Data: string, expectedFormat?: 'png' | 'jpeg'): boolean;
  getBase64WithoutPrefix(base64Data: string): string;
  getImageFormatFromBase64(base64Data: string): 'png' | 'jpeg' | null;
}

class ImageProcessorImpl implements ImageProcessor {
  private readonly SUPPORTED_FORMATS = new Set(['png', 'jpeg', 'jpg']);

  decode(base64Data: string): ProcessedImage | null {
    const timer = logger.startTimer('image-decode');

    try {
      const cleanBase64 = this.getBase64WithoutPrefix(base64Data);
      const format = this.getImageFormatFromBase64(base64Data);

      if (!format) {
        logger.error(undefined, 'Unable to determine image format');
        return null;
      }

      const buffer = Buffer.from(cleanBase64, 'base64');
      const info = this.extractInfo(buffer, format);

      if (!info) {
        logger.error(undefined, 'Unable to extract image info');
        return null;
      }

      timer.end();

      return {
        base64: cleanBase64,
        format,
        info,
      };
    } catch (error) {
      logger.error(undefined, 'Image decode failed', error as Error);
      timer.end();
      return null;
    }
  }

  validate(base64Data: string, expectedFormat?: 'png' | 'jpeg'): boolean {
    try {
      const cleanBase64 = this.getBase64WithoutPrefix(base64Data);
      const buffer = Buffer.from(cleanBase64, 'base64');

      if (buffer.length === 0) {
        return false;
      }

      const format = this.getImageFormatFromBase64(base64Data);

      if (!format) {
        return false;
      }

      if (expectedFormat && format !== expectedFormat) {
        logger.warn(undefined, 'Image format mismatch', {
          expected: expectedFormat,
          actual: format,
        });
        return false;
      }

      return this.validateImageHeaders(buffer, format);
    } catch {
      return false;
    }
  }

  getBase64WithoutPrefix(base64Data: string): string {
    const data = base64Data.trim();

    if (data.startsWith('data:image/')) {
      const commaIndex = data.indexOf(',');
      if (commaIndex !== -1) {
        return data.substring(commaIndex + 1);
      }
    }

    return data;
  }

  getImageFormatFromBase64(base64Data: string): 'png' | 'jpeg' | null {
    const data = base64Data.toLowerCase();

    if (data.includes('image/png')) {
      return 'png';
    }

    if (data.includes('image/jpeg') || data.includes('image/jpg')) {
      return 'jpeg';
    }

    const cleanBase64 = this.getBase64WithoutPrefix(base64Data);
    const buffer = Buffer.from(cleanBase64, 'base64');

    if (buffer.length < 8) {
      return null;
    }

    if (
      buffer[0] === 0x89 &&
      buffer[1] === 0x50 &&
      buffer[2] === 0x4e &&
      buffer[3] === 0x47
    ) {
      return 'png';
    }

    if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
      return 'jpeg';
    }

    return null;
  }

  extractInfo(buffer: Buffer, format: 'png' | 'jpeg'): ImageInfo | null {
    try {
      if (format === 'png') {
        return this.extractPngInfo(buffer);
      }

      if (format === 'jpeg') {
        return this.extractJpegInfo(buffer);
      }
    } catch (error) {
      logger.error(undefined, 'Failed to extract image info', error as Error);
    }

    return null;
  }

  private extractPngInfo(buffer: Buffer): ImageInfo | null {
    if (buffer.length < 25) {
      return null;
    }

    if (
      buffer[0] !== 0x89 ||
      buffer[1] !== 0x50 ||
      buffer[2] !== 0x4e ||
      buffer[3] !== 0x47
    ) {
      return null;
    }

    const width = buffer.readUInt32BE(16);
    const height = buffer.readUInt32BE(20);

    if (width === 0 || height === 0 || width > 10000 || height > 10000) {
      return null;
    }

    return {
      format: 'png',
      width,
      height,
      size: buffer.length,
    };
  }

  private extractJpegInfo(buffer: Buffer): ImageInfo | null {
    if (buffer.length < 2 || buffer[0] !== 0xff || buffer[1] !== 0xd8) {
      return null;
    }

    let offset = 2;

    while (offset < buffer.length) {
      if (buffer[offset] !== 0xff) {
        break;
      }

      const marker = buffer[offset + 1];

      if (marker === 0xc0 || marker === 0xc1 || marker === 0xc2 || marker === 0xc3) {
        offset += 2;

        if (offset + 7 > buffer.length) {
          return null;
        }

        const height = buffer.readUInt16BE(offset + 1);
        const width = buffer.readUInt16BE(offset + 3);

        if (width === 0 || height === 0 || width > 10000 || height > 10000) {
          return null;
        }

        return {
          format: 'jpeg',
          width,
          height,
          size: buffer.length,
        };
      }

      if (marker >= 0xd0 && marker <= 0xd7) {
        offset += 2;
        continue;
      }

      if (marker === 0xda) {
        break;
      }

      offset += 2;

      if (offset + 2 > buffer.length) {
        break;
      }

      const length = buffer.readUInt16BE(offset);
      offset += length;
    }

    return null;
  }

  private validateImageHeaders(buffer: Buffer, format: 'png' | 'jpeg'): boolean {
    if (format === 'png') {
      return (
        buffer.length >= 8 &&
        buffer[0] === 0x89 &&
        buffer[1] === 0x50 &&
        buffer[2] === 0x4e &&
        buffer[3] === 0x47
      );
    }

    if (format === 'jpeg') {
      return buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff;
    }

    return false;
  }
}

export const imageProcessor = new ImageProcessorImpl();
