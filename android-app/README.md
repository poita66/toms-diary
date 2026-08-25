# Android App

## Overview

This is the Android application for Tom's Diary, designed to run on the Supernote Nomad (Android 11, Wacom EMR). The app lets users chat with an AI agent by handwriting, via direct OpenAI-compatible API calls. Each message is sent as an image to a vision-capable LLM, and the reply is displayed as locally-rendered handwriting.

## Target Hardware

**Supernote Nomad (A6 X2)**
- **OS**: Android 11
- **Display**: 7.8" E Ink, 1404×1872 (300 PPI), monochrome
- **Stylus**: Wacom One EMR, 4096 pressure levels
- **Storage**: 32GB internal + microSD (up to 2TB)
- **Connectivity**: WiFi, Bluetooth, USB-C OTG

See [Supernote Nomad Hardware Reference](../docs/supernote-nomad-hardware.md) for detailed specifications.

## Core Features

1. **Handwriting Capture**
   - Custom canvas view for capturing handwritten input
   - Export as high-resolution PNG images
   - Real-time drawing with pressure-sensitive stylus

2. **LLM Integration**
   - Direct HTTP calls to any OpenAI-compatible API (vLLM, Ollama, etc.)
   - Streaming responses via SSE
   - Configurable endpoint via in-app Settings

3. **Local Handwriting Rendering**
   - Renders LLM responses as handwriting using the Caveat font
   - Word-by-word streaming display on canvas

4. **Conversation Management**
   - Conversation history with swipe navigation
   - Multiple persona support (Tom Riddle, Generic, Friendly)
   - Auto-send after idle timeout

## Setup

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or later
- **Android SDK** (API level 30+ for Android 11)
- **Java 17 JDK**: Required for building the project. Install from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/). Note: Java 21+ may have compatibility issues with Gradle 8.10.
- **Gradle 8.2+**: The project includes a Gradle wrapper (`./gradlew`), so a system-wide Gradle installation is not required.
- **JAVA_HOME environment variable**: Must be set to point to your Java 17 JDK installation directory.

#### Setting JAVA_HOME

**Linux/macOS** (add to `~/.bashrc`, `~/.bash_profile`, or `~/.zshrc`):
```bash
export JAVA_HOME=/path/to/jdk-17
export PATH=$JAVA_HOME/bin:$PATH
```

**Windows** (System Properties → Environment Variables):
```
JAVA_HOME=C:\Program Files\Java\jdk-17
```

Verify installation:
```bash
$ java -version
openjdk version "17.0.x"
$ $JAVA_HOME/bin/java -version
openjdk version "17.0.x"
```

### Build Configuration

The project uses Kotlin DSL for Gradle build files:

```kotlin
// app/build.gradle.kts
android {
    namespace = "com.tomsdiary"
    compileSdk = 34
    defaultConfig {
        minSdk = 30
        targetSdk = 34
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### Build Commands

```bash
# Clean and build debug APK
./gradlew clean assembleDebug

# If JAVA_HOME is not set, you can specify it inline:
# JAVA_HOME=/path/to/jdk-17 ./gradlew clean assembleDebug

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test

# Generate release APK
./gradlew assembleRelease
```

### APK Location

After building, the APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Installation on Supernote Nomad

### Enable Sideloading

1. Go to **Settings** → **System** → **Developer options**
2. Enable **"Allow installation of apps from unknown sources"**

### Enable USB Debugging

1. **Settings** → **System** → **About tablet**
2. Tap **"Build number"** 7 times to enable developer options
3. Go back and enable **"USB debugging"**

### Install via ADB

```bash
# Connect Supernote via USB
adb devices

# Install the app
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.tomsdiary/.MainActivity
```

## Architecture

### Key Classes

#### MainActivity
- Main activity managing the UI and app flow
- Handles button clicks (Clear, Send)
- Coordinates canvas capture, the LLM client, and the handwriting renderer
- Manages app state (idle, processing, etc.)

#### DrawingView
- Custom View for handwriting capture
- Handles touch/stylus events for drawing
- Captures canvas as a Bitmap

#### OpenAIClient
- Makes direct HTTP calls to any OpenAI-compatible API
- Sends the canvas image (base64 PNG) and streams the response
- See [LOCAL_PROCESSING.md](LOCAL_PROCESSING.md) for the full request/response flow

#### HandwritingRenderer
- Renders streamed LLM text as handwriting locally using the Caveat font

### Data Flow

1. **User writes** on canvas → DrawingView captures strokes
2. **User sends (or auto-send fires)** → MainActivity captures canvas as Bitmap
3. **Bitmap → Base64** → PNG encoded as base64 string
4. **LLM call** → Image sent directly to the configured OpenAI-compatible endpoint
5. **Canvas cleared** → Ready for new input
6. **Streaming tokens received** → Rendered as handwriting word-by-word on canvas

## Configuration

### LLM Endpoint

Configured in-app via **Settings** (persisted via `LLMConfig`) — see [LOCAL_PROCESSING.md](LOCAL_PROCESSING.md) for defaults and example endpoints.

### Network Permissions

The app requires the following permissions (declared in AndroidManifest.xml):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

## UI Elements

### Canvas View
- Full-screen drawing area
- White background, black strokes
- 3px stroke width with anti-aliasing
- Supports multiple strokes

### Control Panel
- **Clear Button**: Clears the canvas
- **Send Button**: Sends current canvas to the configured LLM

### Status Text
- Shows processing state (Sending, Waiting for response)

## Development Considerations

### E-Ink Display

- **Slow refresh rate**: Full refresh takes ~3-4 seconds
- **Optimization**: Use partial updates where possible
- **Ghosting**: Consider occasional full refreshes
- **Monochrome only**: All rendering is grayscale

### Battery Optimization

- WiFi significantly impacts battery life
- Minimize network activity when not needed
- Efficient PNG compression (quality 100)
- Consider batch processing for long notes

### Image Capture

- Capture at device resolution for best OCR results
- PNG format for lossless quality
- Base64 encoding for the LLM API request
- Typical image size: ~500KB-1MB for full canvas

## Testing

### On Device Testing

- Test handwriting capture at various speeds
- Verify image quality meets VLM requirements
- Test LLM reconnection on network loss
- Profile battery consumption

## Troubleshooting

### Connection Issues

- Verify your LLM server is running at the address configured in Settings
- Ensure the device and LLM server are on the same network (use the server machine's IP, not `localhost`, when testing on physical hardware)
- Check LLM server logs for incoming requests

### Build Issues

- Ensure Java 17+ is installed: `java -version`
- Ensure Gradle wrapper is present: `ls gradle/wrapper/gradle-wrapper.jar`
- Clean and rebuild: `./gradlew clean assembleDebug`

### Runtime Issues

- Check Android logs: `adb logcat | grep TomsDiary`
- Verify network permissions in AndroidManifest.xml
- Check the LLM Base URL configured in app Settings

## Future Enhancements

- Support for incremental image streaming to VLM
- Local handwriting recognition fallback
- Multiple handwriting style support
- Offline mode with local LLM
- Stroke-by-stroke animation for responses
- Note history and persistence
- Search and organization features

## References

- [Android 11 Documentation](https://developer.android.com/about/versions/11)
- [Supernote Hardware Reference](../docs/supernote-nomad-hardware.md)
- [Development Guide](../AGENTS.md)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
