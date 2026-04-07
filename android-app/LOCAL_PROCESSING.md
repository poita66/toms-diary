# Local Processing Migration

## Overview

The app has been migrated from server-generated handwriting to **fully local processing**. The Android app now:

1. **Directly calls any OpenAI-compatible API** (vLLM, Ollama, etc.)
2. **Renders handwriting locally** using the Caveat font
3. **No longer requires the backend WebSocket server**

## Architecture Changes

### Before (Server-Based)
```
Android App → WebSocket Server → vLLM → Handwriting Renderer → Android App
```

### After (Local)
```
Android App → OpenAI API → Local Handwriting Renderer → Android App
```

## New Components

### 1. `OpenAIClient.kt`
- Makes direct HTTP calls to any OpenAI-compatible API
- Supports streaming responses
- Handles multimodal messages (image + text)
- Configurable via `LLMConfig`

### 2. `HandwritingRenderer.kt`
- Renders text to bitmap using the Caveat font
- Streams word-by-word rendering for smooth display
- Matches the original server-side rendering style
- Includes natural handwriting variations (slight rotation, offset)

### 3. `LLMConfig.kt`
- Centralized configuration for LLM settings
- Persists settings in SharedPreferences
- Easy to change API endpoint, key, and model

## Configuration

### Default Settings
- **Base URL**: `http://localhost:8001/v1` (vLLM default)
- **API Key**: `placeholder` (most local LLMs don't require one)
- **Model**: `default`

### Changing Settings
1. Tap the **Settings** button in the app
2. Update the LLM Settings section:
   - **API Base URL**: Your OpenAI-compatible endpoint
   - **API Key**: Required for some services (optional for local LLMs)
   - **Model Name**: The model to use

### Example Endpoints

**vLLM:**
```
Base URL: http://localhost:8001/v1
Model: default
```

**Ollama:**
```
Base URL: http://localhost:11434/v1
Model: llama3.2-vision (or any vision model)
```

**Local AI Studio:**
```
Base URL: http://localhost:1234/v1
Model: Your model name
```

## Font

The Caveat font is included in `app/src/main/assets/Caveat-Regular.ttf`

This is the same font used by the original server-side renderer.

## Removed Components

- **WebSocket connection**: No longer needed
- **Connect/Disconnect buttons**: Removed from UI
- **WebSocketClient.kt**: Deprecated (kept for reference only)

## Benefits

1. **Flexibility**: Use any OpenAI-compatible API
2. **Simplicity**: No backend server required
3. **Performance**: Direct API calls reduce latency
4. **Privacy**: Run entirely locally with local LLMs
5. **Portability**: Easier to deploy and configure

## Migration Notes

- The app still uses the same personas (Tom Riddle, Generic, Friendly)
- Conversation history is preserved
- The handwriting rendering style matches the original
- All existing features (auto-send, swipe history, etc.) still work

## Troubleshooting

### "Connection refused" error
- Ensure your LLM server is running
- Check the base URL in Settings matches your server
- For local testing, ensure the device can reach `localhost` (may need to use device IP)

### "Model not found" error
- Verify the model name in Settings
- Check that the model is loaded in your LLM server
- For vLLM, use `default` or the actual model name

### Slow rendering
- Reduce font size in `HandwritingRenderer.calculateFontSize()`
- Use smaller images or `detail: 'low'` (already configured)
- Consider using a faster model

## Building

```bash
cd android-app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tomsdiary/.MainActivity
```

## Dependencies Added

```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

This provides HTTP client functionality for OpenAI API calls.
