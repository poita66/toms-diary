# Tom's Diary - Development Guide

## Project Overview

AI-powered handwritten chat app with:
- **Android App**: Kotlin for Supernote Nomad (Android 11+, minSdk 30)
- **Local Processing**: Direct OpenAI API calls with local handwriting rendering
- **LLM Backend**: Any OpenAI-compatible API (vLLM, Ollama, etc.)

### Key Architecture
- ✅ **No backend server required** - app calls LLM directly
- ✅ **Local handwriting rendering** - uses Caveat font on device
- ✅ **Flexible LLM support** - works with any OpenAI-compatible API
- ✅ **Simplified deployment** - just the app + your LLM of choice

## Build & Test Commands

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

### LLM Server (Required)

Start an OpenAI-compatible LLM server with a **vision-capable** model before using the app. Pick whichever you have:

**LM Studio** (GUI, easiest): download a "VL" model, then Developer tab → Start Server. Default port `1234`.

**llama.cpp:**
```bash
llama-server -m model.gguf --mmproj model-mmproj.gguf --port 8080
```

**Ollama:**
```bash
ollama pull llama3.2-vision
ollama serve  # Default port 11434
```

**vLLM:**
```bash
vllm serve --model your-model-name --port 8001
```

**Test the connection** (adjust host/port to your server):
```bash
curl http://localhost:8080/v1/models
```

### Device Info

**Android (Supernote Nomad A6 X2)**
- LLM: localhost:1234 (LM Studio), localhost:8080 (llama.cpp), localhost:11434 (Ollama), or localhost:8001 (vLLM)
- Screen: 1324x1752 pixels, line spacing 150px, first line Y=180px

## Code Style Guidelines

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
- Classes: PascalCase (`MainActivity`, `OpenAIClient`)
- Functions/vars: camelCase (`sendCanvasImage`, `isProcessing`)
- Constants: UPPER_SNAKE_CASE (`AUTO_SEND_DELAY_MS`)

**Coroutines**: Use `CoroutineScope` with `SupervisorJob` for lifecycle-scoped operations
```kotlin
private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

scope.launch {
  delay(500)
  sendCanvasImage()
}
```

**Null Safety**: Use nullable types explicitly, `?.` for safe calls
```kotlin
private var openAIClient: OpenAIClient? = null
openAIClient?.sendImage(base64Image, width, height)
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

### Android Structure
```
app/src/main/java/com/tomsdiary/
├── MainActivity.kt           # Main activity, LLM calls, local rendering coordination
├── DrawingView.kt            # Custom drawing view for handwriting
├── OpenAIClient.kt           # OpenAI API client (any compatible endpoint)
├── HandwritingRenderer.kt    # Local canvas-based text rendering with Caveat font
└── LLMConfig.kt              # Configuration management for LLM settings
```

### Data Flow
```
1. User writes on canvas
2. App captures & crops handwriting
3. Image sent to LLM via OpenAI API
4. LLM returns streaming tokens
5. App renders tokens as handwriting locally
6. Words displayed one-by-one on canvas
```

## Key Configuration

### LLM Settings (in-app)

Accessed via the **Settings** button in the app:

- **API Base URL**: `http://localhost:8001/v1` (default, vLLM)
- **API Key**: `placeholder` (optional for local LLMs)
- **Model Name**: `default` (or your specific model)

## vLLM Optimization

### Disabling Reasoning for Fast Inference
The Qwen3.5-27B model generates extensive reasoning tokens by default (~361 tokens, ~49s latency). To achieve fast responses (~0.24s, ~10 tokens), the app uses:

```kotlin
// In OpenAIClient.kt
put("chat_template_kwargs", JSONObject().apply {
    put("enable_thinking", false)  // vLLM-specific, disables reasoning generation
})
put("include_reasoning", false)  // Don't return reasoning in response
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
- Use `detail: 'low'` in image_url to reduce token usage (already configured)
- Full resolution images work fine with `enable_thinking: false` optimization

## Common Tasks

1. **Change LLM endpoint**: Open Settings in app → update API Base URL
2. **Change model**: Open Settings in app → update Model Name
3. **Change font size**: Modify `calculateFontSize()` in `HandwritingRenderer.kt`
4. **Adjust auto-send delay**: Change `AUTO_SEND_DELAY_MS` in `MainActivity.kt`
5. **Add new persona**: Update `PERSONAS` map in `OpenAIClient.kt`
6. **Deploy Android app**:
   ```bash
   cd android-app && ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.tomsdiary/.MainActivity
   ```
7. **Start vLLM**:
   ```bash
   vllm serve --model your-model --port 8001
   ```

## Known Constraints

### General
- Android build requires Java 17 (Gradle 8.10 incompatibility with Java 21+)
- Font rendering uses Caveat font
- LLM timeout is 120 seconds
- **Use `chat_template_kwargs: { enable_thinking: false }`** in LLM requests to disable reasoning entirely
- **Only send current image to LLM, send history as text only** (vLLM is exponentially slower with multiple images)
- **Images should be greyscale** for faster inference
- **Use `detail: 'low'`** in image_url to reduce token usage

### Android Specific
- Screen width sent from client excludes 80px padding (40px each side)
- Text wraps at `maxWidth - 20` to leave right margin
- ePaper display has 500-1000ms refresh times - minimize invalidate() calls
- **Use throttle (5ms) instead of debounce for pen strokes** to show mid-stroke
- Font size should be 90px (60% of 150px line spacing) to prevent italic overflow
- **Auto-clear is disabled** - canvas clears only when user writes
- **Prevent concurrent requests** - double-check `isProcessing` flag to avoid race conditions
- **Images are cropped to handwriting bounds** - no fixed scaling, send full resolution
