import { logger } from '../utils/logger.js';

export interface CachedImage {
  key: string;
  base64: string;
  format: 'png' | 'jpeg';
  size: number;
  createdAt: number;
  lastAccessed: number;
  accessCount: number;
}

export interface ImageCache {
  get(key: string): CachedImage | null;
  set(key: string, base64: string, format: 'png' | 'jpeg'): boolean;
  delete(key: string): boolean;
  has(key: string): boolean;
  cleanup(maxAgeMs: number, maxItems: number): number;
  getStats(): { size: number; totalSize: number; oldestEntry: number | null };
}

class ImageCacheImpl implements ImageCache {
  private cache: Map<string, CachedImage>;
  private readonly maxSize: number;
  private readonly maxAge: number;

  constructor(maxSize: number = 100, maxAge: number = 30 * 60 * 1000) {
    this.cache = new Map();
    this.maxSize = maxSize;
    this.maxAge = maxAge;
  }

  get(key: string): CachedImage | null {
    const item = this.cache.get(key);

    if (!item) {
      return null;
    }

    item.lastAccessed = Date.now();
    item.accessCount++;

    logger.debug(undefined, 'Cache hit', { key, accessCount: item.accessCount });
    return item;
  }

  set(key: string, base64: string, format: 'png' | 'jpeg'): boolean {
    const now = Date.now();
    const size = Buffer.from(base64, 'base64').length;

    if (this.cache.size >= this.maxSize) {
      this.evictOldest();
    }

    const item: CachedImage = {
      key,
      base64,
      format,
      size,
      createdAt: now,
      lastAccessed: now,
      accessCount: 1,
    };

    this.cache.set(key, item);
    logger.debug(undefined, 'Image cached', { key, size, format });

    return true;
  }

  delete(key: string): boolean {
    const item = this.cache.get(key);

    if (!item) {
      return false;
    }

    this.cache.delete(key);
    logger.debug(undefined, 'Image removed from cache', { key });

    return true;
  }

  has(key: string): boolean {
    return this.cache.has(key);
  }

  cleanup(maxAgeMs: number = this.maxAge, maxItems: number = this.maxSize): number {
    const now = Date.now();
    let removedCount = 0;

    const itemsToRemove: string[] = [];

    for (const [key, item] of this.cache.entries()) {
      if (now - item.createdAt > maxAgeMs) {
        itemsToRemove.push(key);
        continue;
      }

      if (itemsToRemove.length >= maxItems) {
        break;
      }
    }

    if (this.cache.size > maxItems) {
      const sorted = Array.from(this.cache.entries()).sort(
        (a, b) => a[1].lastAccessed - b[1].lastAccessed
      );

      for (let i = 0; i < this.cache.size - maxItems; i++) {
        if (!itemsToRemove.includes(sorted[i][0])) {
          itemsToRemove.push(sorted[i][0]);
        }
      }
    }

    for (const key of itemsToRemove) {
      this.cache.delete(key);
      removedCount++;
    }

    if (removedCount > 0) {
      logger.info(undefined, `Cache cleanup removed ${removedCount} items`, {
        remaining: this.cache.size,
      });
    }

    return removedCount;
  }

  getStats(): { size: number; totalSize: number; oldestEntry: number | null } {
    let totalSize = 0;
    let oldestEntry: number | null = null;

    for (const item of this.cache.values()) {
      totalSize += item.size;

      if (oldestEntry === null || item.createdAt < oldestEntry) {
        oldestEntry = item.createdAt;
      }
    }

    return {
      size: this.cache.size,
      totalSize,
      oldestEntry,
    };
  }

  private evictOldest(): void {
    let oldestKey: string | null = null;
    let oldestTime = Infinity;

    for (const [key, item] of this.cache.entries()) {
      if (item.lastAccessed < oldestTime) {
        oldestTime = item.lastAccessed;
        oldestKey = key;
      }
    }

    if (oldestKey) {
      this.cache.delete(oldestKey);
      logger.debug(undefined, 'Cache evicted oldest item', { key: oldestKey });
    }
  }
}

export const imageCache = new ImageCacheImpl();
