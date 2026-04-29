# Tom's Diary - Development Guide

## Project Overview

AI-powered handwritten journal system with:
- **Android App**: Kotlin for Supernote Nomad (Android 11+, minSdk 30)
- **iOS App**: SwiftUI for iPad with Apple Pencil support (iOS 17+)
- **Local Processing**: Direct OpenAI API calls with local handwriting rendering
- **LLM Backend**: Any OpenAI-compatible API (vLLM, Ollama, etc.)

### Key Architecture
- ✅ **No backend server required** - apps call LLM directly
- ✅ **Local handwriting rendering** - uses Caveat font on device
- ✅ **Flexible LLM support** - works with any OpenAI-compatible API
- ✅ **Simplified deployment** - just the app + your LLM of choice
- ✅ **Cross-platform** - Android and iOS apps with identical functionality

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

### iOS App (`/ios-app/`)

```bash
# Open in Xcode
cd ios-app && open TomsDiary.xcodeproj

# Or use the build script
# Build
./build.sh build

# Clean and build
./build.sh clean && ./build.sh build

# Run on simulator
./build.sh run

# Run tests
./build.sh test
```

**iOS Build Requirements:**
- Xcode 15.0+
- iOS 17.0+ deployment target
- iPad with Apple Pencil (for real device testing)

**iOS Network Configuration:**
When testing on a real iPad, update the LLM Base URL in app settings to use your computer's IP:
```bash
# Find your computer's IP
# macOS
ipconfig getifaddr en0

# Linux
hostname -I | awk '{print $1}'

# Then in the app Settings, set Base URL to:
http://YOUR_IP:8001/v1
```

### LLM Server (Required)

Start your OpenAI-compatible LLM server before using the app:

**vLLM:**
```bash
# For local testing (Android or iOS simulator)
vllm serve --model your-model-name --port 8001

# For iOS device testing (must allow external connections)
vllm serve --model your-model-name --host 0.0.0.0 --port 8001
```

**Ollama:**
```bash
ollama serve  # Default port 11434
# Then enable API mode or use a wrapper
```

**Test the connection:**
```bash
curl http://localhost:8001/v1/models
```

### Device Info

**Android (Supernote Nomad A6 X2)**
- LLM: localhost:8001 (vLLM) or localhost:11434 (Ollama)
- Screen: 1324x1752 pixels, line spacing 150px, first line Y=180px

**iOS (iPad)**
- LLM: YOUR_IP:8001 (use computer's IP, not localhost when on device)
- Apple Pencil support with pressure and tilt
- iPad Pro 12.9" recommended for testing

## Code Style Guidelines

### Swift (iOS)

**Imports**: Group by standard library, then Apple frameworks, then third-party
```swift
import Foundation
import UIKit
import SwiftUI
import Combine
```

**Naming**:
- Files: PascalCase (`DrawingView.swift`, `OpenAIClient.swift`)
- Classes/Structs: PascalCase (`MainViewModel`, `ConversationTurn`)
- Functions/vars: camelCase (`sendCanvasImage`, `isProcessing`)
- Constants: nested in `enum` or `struct` with `static let`
- Private properties: prefix with `_` when appropriate

**Async/Await**: Use `async/await` over closures. Use `AsyncThrowingStream` for streaming.
```swift
func chatStreamWithImage(imageBase64: String) async throws -> AsyncThrowingStream<String, Error> {
    return AsyncThrowingStream { continuation in
        // stream implementation
    }
}

// Consuming streams
for try await token in client.chatStreamWithImage(imageBase64: base64) {
    process(token)
}
```

**Null Safety**: Use optional types explicitly, `?` for safe calls, `??` for defaults
```swift
private var drawingView: DrawingView?
drawingView?.clear()
let width = image.size.width ?? 0
```

**UI Updates**: Use `@MainActor` for view models that interact with UI
```swift
@MainActor
final class MainViewModel: ObservableObject {
    @Published var statusText: String = "Ready"
}
```

**Memory Management**: Use `weak` references to avoid retain cycles
```swift
weak var drawingView: DrawingView?
```

**Error Handling**: Use `do-catch` with specific error types
```swift
do {
    let result = try await someAsyncOperation()
} catch is URLError {
    // Handle network error
} catch {
    // Handle other errors
}
```

**Extensions**: Keep extensions focused and named
```swift
// MARK: - UITouch Extension
extension UITouch {
    var isFinger: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return toolType == .finger
        #endif
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
- Classes: PascalCase (`MainActivity`, `OpenAIClient`)
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
├── LLMConfig.kt              # Configuration management for LLM settings
└── WebSocketClient.kt        # DEPRECATED - kept for reference only
```

### iOS Structure
```
ios-app/TomsDiary/
├── App/
│   └── TomsDiaryApp.swift      # App entry point
├── Views/
│   ├── ContentView.swift        # Main SwiftUI view
│   ├── DrawingView.swift        # Custom UIView for handwriting
│   └── SettingsView.swift       # LLM configuration
├── ViewModels/
│   └── MainViewModel.swift      # App logic coordinator
├── Services/
│   ├── OpenAIClient.swift       # OpenAI API client
│   ├── HandwritingRenderer.swift # Text-to-handwriting rendering
│   └── ImageProcessor.swift     # Image capture & processing
├── Models/
│   ├── LLMConfig.swift          # LLM configuration
│   ├── Persona.swift            # Persona definitions
│   └── Conversation.swift       # Conversation models
└── Assets.xcassets/
    └── Fonts/
        └── Caveat-Regular.ttf   # Handwriting font
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
3. **Change font size**: 
   - Android: Modify `calculateFontSize()` in `HandwritingRenderer.kt`
   - iOS: Modify `calculateFontSize(for:)` in `Services/HandwritingRenderer.swift`
4. **Adjust auto-send delay**: 
   - Android: Change `AUTO_SEND_DELAY_MS` in `MainActivity.kt`
   - iOS: Change `autoSendDelay` in `ViewModels/MainViewModel.swift`
5. **Add new persona**: 
   - Android: Update `PERSONAS` map in `OpenAIClient.kt`
   - iOS: Update `Persona` enum in `Models/Persona.swift`
6. **Deploy Android app**:
   ```bash
   cd android-app && JAVA_HOME=/tmp/jdk-17.0.9+9 ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.tomsdiary/.MainActivity
   ```
7. **Build iOS app**:
   ```bash
   cd ios-app
   open TomsDiary.xcodeproj  # Open in Xcode
   # Or build from command line
   ./build.sh build
   ```
8. **Start vLLM**:
   ```bash
   vllm serve --model your-model --port 8001
   # For iOS device testing:
   vllm serve --model your-model --host 0.0.0.0 --port 8001
   ```

## Known Constraints

### General
- Android build requires Java 17 (Gradle 8.10 incompatibility with Java 21+)
- Font rendering uses Caveat font (included in both apps)
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

### iOS Specific
- Requires Xcode 15.0+ and iOS 17.0+
- Apple Pencil not available in Simulator - test on real device
- Use computer's IP address (not localhost) when testing on real iPad
- Font must be registered in `Info.plist` under `UIAppFonts`
- Line spacing is 150pt (matching Android)
- First line Y position is 180pt (matching Android)
