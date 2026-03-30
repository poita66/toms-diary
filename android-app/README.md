# Android App

## Overview

This is the Android application for Tom's Diary, designed to run on the Supernote Nomad (Android 11, Wacom EMR). The app enables users to write handwritten notes to an AI agent, which reads the handwriting, responds, and the app displays the response as if it were handwritten.

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
   - Capture handwritten input from the e-ink display
   - Export as high-resolution images (1404×1872)
   - Support for incremental captures (optional)

2. **Image Transmission**
   - Send captured images to backend via WebSocket
   - Compress images appropriately for transmission
   - Handle connection errors and reconnection

3. **Screen Management**
   - Clear display after image capture
   - Partial refresh optimization for e-ink
   - Smooth transitions between states

4. **Response Display**
   - Receive streamed handwriting rendering from backend
   - Display rendered response on e-ink display
   - Handle incremental rendering updates

## Setup

### Prerequisites

- Android Studio (latest stable version)
- Android SDK (API level 30+ for Android 11)
- Node.js (for build tools, if needed)

### Installation

1. Clone the repository
2. Open project in Android Studio
3. Sync Gradle dependencies
4. Connect Supernote Nomad via USB
5. Enable USB debugging on device
6. Run and deploy

### Supernote-Specific Setup

1. **Enable Sideloading**
   - Go to Settings → System → Developer options
   - Enable "Allow installation of apps from unknown sources"

2. **USB Debugging**
   - Settings → System → About tablet
   - Tap "Build number" 7 times to enable developer options
   - Enable "USB debugging"

## Architecture

### Components

```
┌─────────────────────────────────────────┐
│           Android App                    │
├─────────────────────────────────────────┤
│  ┌──────────────┐  ┌─────────────────┐  │
│  │  UI Layer     │  │  WebSocket      │  │
│  │  (E-Ink      │◄─┤  Client         │  │
│  │   Renderer)   │  │                 │  │
│  └──────────────┘  └─────────────────┘  │
│          ▲                ▲              │
│          │                │              │
│  ┌──────────────┐  ┌─────────────────┐  │
│  │  Screen       │  │  Image          │  │
│  │  Capture      │  │  Processor      │  │
│  │               │  │                 │  │
│  └──────────────┘  └─────────────────┘  │
└─────────────────────────────────────────┘
```

### Key Modules

- **ScreenCapture**: Captures current display state as image
- **WebSocketClient**: Manages connection to backend service
- **DisplayRenderer**: Renders incoming handwriting streams to e-ink
- **StateManager**: Manages app state (writing, sending, receiving, displaying)

## Development Considerations

### E-Ink Display

- **Slow refresh rate**: Full refresh takes ~3-4 seconds
- **Optimization**: Use partial updates where possible
- **Ghosting**: Implement occasional full refreshes to prevent ghosting
- **Monochrome only**: All rendering must be grayscale

### Battery Optimization

- WiFi and Bluetooth significantly impact battery life
- Minimize network activity when not needed
- Implement efficient image compression
- Consider batch processing for multiple captures

### Wacom EMR Integration

- Standard Android Ink API may have higher latency
- Consider native integration with Supernote's EMR system
- 4096 pressure levels available for rich input data
- Palm rejection built into hardware

### Image Capture

- Capture at native resolution (1404×1872) for best OCR results
- Consider compression format (PNG for lossless, JPEG for smaller size)
- Incremental captures may be useful for long notes

## Communication Protocol

### WebSocket Messages

**Client → Server**

```json
{
  "type": "image",
  "data": "<base64-encoded-image>",
  "metadata": {
    "timestamp": 1234567890,
    "width": 1404,
    "height": 1872
  }
}
```

**Server → Client**

```json
{
  "type": "render-chunk",
  "data": "<rendering-instructions>",
  "metadata": {
    "chunkIndex": 0,
    "totalChunks": 10
  }
}
```

## Testing

### On Device

- Test handwriting capture at various speeds
- Verify image quality meets VLM requirements
- Test WebSocket reconnection on network loss
- Profile battery consumption

### Limitations

- No microphone for audio testing
- No speakers for audio feedback
- E-ink refresh limits animation testing

## Future Enhancements

- Support for incremental image streaming to VLM
- Local handwriting recognition fallback
- Multiple handwriting style support
- Offline mode with local LLM

## References

- [Android 11 Documentation](https://developer.android.com/about/versions/11)
- [Supernote Hardware Reference](../docs/supernote-nomad-hardware.md)
- [Backend Documentation](../backend/README.md)
