# Tom's Diary - Development Guide

## Project Overview

AI-powered handwritten journal system with:
- **Backend**: TypeScript/Node.js WebSocket server (port 8080, mapped to 18080 via Docker)
- **Android App**: Kotlin for Supernote Nomad (Android 11+, minSdk 30)
- **vLLM**: Vision language model at `http://localhost:8000/v1` (model: `default`)

## Build & Test Commands

### Backend (`/backend/`)

```bash
# Development
npm run dev          # tsx watch src/index.ts

# Build
npm run build        # tsc

# Lint & typecheck
npm run lint         # eslint src --ext .ts
npm run typecheck    # tsc --noEmit

# Tests
npm run test         # vitest (all tests)
npm run test -- src/__tests__/integration.test.ts  # single test file
npm run test -- -t "should validate"              # run tests matching pattern
npm run test:coverage  # with coverage
```

### Android App (`/android-app/`)

```bash
# Full deploy cycle (build, install, start)
cd android-app && ./gradlew assembleDebug && \
adb install -r app/build/outputs/apk/debug/app-debug.apk && \
adb shell am start -n com.tomsdiary/.MainActivity

# Or step by step:
# Build (requires Java 17 - Java 21+ may have issues with Gradle 8.10)
cd android-app && ./gradlew assembleDebug

# Install to device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Start app
adb shell am start -n com.tomsdiary/.MainActivity

# Run tests
./gradlew test
```

### Device Info
- Supernote Nomad A6 X2
- Backend: localhost:8080 (exposed as 18080 via Docker)
- vLLM: localhost:8001
- Screen: 1324x1752 pixels, line spacing 150px, first line Y=180px

### Docker (Backend)

```bash
# Start
docker compose up -d

# Stop
docker compose down

# Rebuild
docker build --no-cache -t backend-toms-diary-backend . && docker compose up -d

# Logs
docker logs toms-diary-backend --tail 50 --follow
```

## Code Style Guidelines

### TypeScript (Backend)

**Imports**: ES modules with `.js` extension (even for TypeScript files)
```typescript
import { WebSocketServer } from 'ws';
import { logger } from '../utils/logger.js';
import type { ClientToServerMessage } from '../types/messages.js';
```

**Types**: Use `type` aliases over `interface` for most cases. Explicit typing preferred.
```typescript
interface ServerOptions {
  port: number;
  host: string;
}

type LogLevel = 'debug' | 'info' | 'warn' | 'error';
```

**Naming**: 
- Files: lowercase with path separators (`websocket.ts`, `handwriting.ts`)
- Classes: PascalCase (`HandwritingRendererImpl`)
- Functions/vars: camelCase (`handleConnection`, `activeSessions`)
- Constants: UPPER_SNAKE_CASE (`DEFAULT_PADDING`, `RENDER_THRESHOLD`)

**Error Handling**: Use logger for all errors, structured error handling with middleware
```typescript
try {
  // operation
} catch (error) {
  logger.error(sessionId, 'Operation failed', error as Error, { context });
  errorHandler.handleInternalError(ws, error as Error, context);
}
```

**Logging**: Always include `sessionId` for traceability
```typescript
logger.info(sessionId, 'Client connected');
logger.debug(sessionId, 'Token received', { length: token.length });
logger.error(sessionId, 'Processing failed', error, { error: errorMessage });
```

**Async**: Use `async/await` over promises. Stream with `for await...of`.
```typescript
for await (const event of llmClient.chatStream(sessionId, messages)) {
  if (event.type === 'token') {
    // handle token
  }
}
```

### Kotlin (Android)

**Imports**: Group by standard library, Android, then third-party
```kotlin
package com.tomsdiary

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
```

**Naming**:
- Classes: PascalCase (`MainActivity`, `WebSocketClient`)
- Functions/vars: camelCase (`sendCanvasImage`, `isConnected`)
- Constants: UPPER_SNAKE_CASE (`AUTO_SEND_DELAY_MS`, `SERVER_URL`)

**Coroutines**: Use `CoroutineScope` with `SupervisorJob` for lifecycle-scoped operations
```kotlin
private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

scope.launch {
  delay(500)
  connect()
}
```

**Null Safety**: Use nullable types explicitly, `?.` for safe calls
```kotlin
private var webSocketClient: WebSocketClient? = null
webSocketClient?.sendImage(base64Image, width, height)
```

**UI Updates**: Always use `withContext(Dispatchers.Main)` for UI changes from background threads
```kotlin
scope.launch(Dispatchers.IO) {
  val result = heavyComputation()
  withContext(Dispatchers.Main) {
    updateUI(result)
  }
}
```

## Architecture

### Backend Structure
```
src/
├── index.ts              # Entry point, loads config, starts WebSocket server
├── config.ts             # Environment configuration (port, vLLM settings)
├── server/
│   └── websocket.ts      # WebSocket server, message routing
├── stream/
│   └── coordinator.ts    # Orchestrates LLM streaming + rendering
├── renderer/
│   └── handwriting.ts    # Canvas-based text rendering with Caveat font
├── llm/
│   ├── client.ts         # vLLM API client (uses chat_template_kwargs for Qwen3)
│   └── orchestrator.ts   # Vision model prompt orchestration
├── session/
│   └── manager.ts        # Session state management
├── utils/
│   ├── logger.ts         # Structured logging with session tracking
│   └── validation.ts     # Zod-based message validation
└── __tests__/
    └── integration.test.ts
```

### Android Structure
```
app/src/main/java/com/tomsdiary/
├── MainActivity.kt       # Main activity, connection management, auto-send, image processing
├── DrawingView.kt        # Custom drawing view for handwriting (replaced WebView.kt)
└── WebSocketClient.kt    # WebSocket client with reconnection logic
```

## Key Configuration

### Backend Environment (`.env`)
```
VLLM_HOST=http://localhost:8001/v1
VLLM_MODEL=default
PORT=8080
HOST=0.0.0.0
LOG_LEVEL=info
```

### Android Server URL
```kotlin
    private val SERVER_URL = "ws://localhost:18080"
```

## vLLM Optimization

### Disabling Reasoning for Fast Inference
The Qwen3.5-27B model generates extensive reasoning tokens by default (~361 tokens, ~49s latency). To achieve fast responses (~0.24s, ~10 tokens), use:

```typescript
// In client.ts - both streaming and non-streaming
this.client.chat.completions.create({
  model: this.model,
  messages,
  chat_template_kwargs: { enable_thinking: false },  // vLLM-specific, disables reasoning generation
  include_reasoning: false,  // Don't return reasoning in response
} as any);
```

**Important findings:**
- `reasoning_effort: 'none'` - Does NOT disable reasoning, still generates ~361 tokens
- `chat_template_kwargs: { enable_thinking: false }` - Actually disables reasoning, ~10 tokens, ~0.24s
- `thinking_effort: 'low'` - Still generates ~430 tokens, ~4.5s (no meaningful improvement)
- `thinking_budget: N` - Limited effect, still ~3.7-4.1s

**Conclusion**: Use `enable_thinking: false` for instant responses. The model has no true "low effort" mode - it's binary (full reasoning or none).

### Image Processing
- Images are greyscale PNGs
- Images are cropped to handwriting bounds (no fixed scaling)
- Use `detail: 'low'` in image_url to reduce token usage
- Full resolution images work fine with `enable_thinking: false` optimization

## Testing Patterns

### Backend Tests (Vitest)
```typescript
import { describe, it, expect, beforeAll, afterAll } from 'vitest';

describe('Feature Name', () => {
  it('should do something', () => {
    const result = functionUnderTest(input);
    expect(result).toBe(expected);
  });

  it('should handle async operation', async () => {
    const result = await asyncFunction(input);
    expect(result).toBeTruthy();
  }, 30000); // timeout for integration tests
});
```

## Common Tasks

1. **Change font size**: Modify `calculateFontSize()` in `handwriting.ts`
2. **Adjust auto-send delay**: Change `AUTO_SEND_DELAY_MS` in `MainActivity.kt`
3. **Update server IP**: Change `SERVER_URL` in `MainActivity.kt` and rebuild app
4. **Add new message type**: Update `types/messages.ts`, add validator in `validation.ts`
5. **Deploy backend**: 
   ```bash
    # Update with your deployment commands
   ```
6. **Deploy Android app**:
   ```bash
   cd android-app && JAVA_HOME=/tmp/jdk-17.0.9+9 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tomsdiary/.MainActivity
   ```

## Known Constraints

- Android build requires Java 17 (Gradle 8.10 incompatibility with Java 21+)
- Backend uses ES modules (`.js` import extensions required)
- Font rendering uses Caveat font from `/fonts/static/Caveat-Regular.ttf`
- Screen width sent from client excludes 80px padding (40px each side)
- Text wraps at `maxWidth - 20` to leave right margin
- ePaper display has 500-1000ms refresh times - minimize invalidate() calls
- **Use throttle (5ms) instead of debounce for pen strokes** to show mid-stroke
- Font size should be 90px (60% of 150px line spacing) to prevent italic overflow
- **Auto-clear is disabled** - canvas clears only when user writes
- LLM timeout is 120 seconds
- **Use `chat_template_kwargs: { enable_thinking: false }`** in LLM requests to disable reasoning entirely
- **Only send current image to LLM, send history as text only** (vLLM is exponentially slower with multiple images)
- **Images should be greyscale** for faster inference
- **Use separate screenWidth vs image dimensions** - screenWidth for font sizing, image dimensions for the actual cropped image
- **Prevent concurrent requests** - double-check `isProcessing` flag to avoid race conditions
- **Images are cropped to handwriting bounds** - no fixed scaling, send full resolution
- **Use `detail: 'low'`** in image_url to reduce token usage
