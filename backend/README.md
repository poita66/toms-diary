# Backend Service

## Overview

The backend service is a TypeScript/Node.js application that powers the AI functionality for Tom's Diary. It handles image processing, integrates with Vision Language Models (VLM) for handwriting recognition and response generation, coordinates handwriting rendering, and streams responses back to the Android app via WebSocket.

## Technology Stack

- **Runtime**: Node.js 24
- **Language**: TypeScript
- **Protocol**: WebSocket
- **LLM Client**: OpenAI SDK (compatible with vLLM, OpenAI, and other OpenAI-compatible APIs)
- **Image Processing**: Custom processor with PNG/JPEG support
- **Handwriting Rendering**: Canvas-based font rendering (Caveat font)
- **Validation**: Zod
- **Dependencies**: ws, openai, canvas, zod

## Core Features

### 1. WebSocket Server

- Maintain persistent connections with Android app
- Handle bidirectional streaming
- Manage multiple concurrent connections
- Graceful reconnection support

### 2. Image Processing Pipeline

- Receive and validate image data from app
- Preprocess images for VLM consumption
- Optional: Incremental image handling
- Image caching and optimization

### 3. VLM Integration

- Send images to VLM for recognition
- Receive text transcription of handwriting
- Generate AI response to user's input
- Stream response tokens as they're generated

### 4. Handwriting Rendering

- Convert AI response text to handwriting-style output
- Stream rendering data to Android app
- Support for incremental rendering
- Potential: User handwriting style imitation

### 5. Streaming Coordination

- Orchestrate data flow between components
- Manage backpressure and flow control
- Ensure smooth user experience on e-ink display

## Setup

### Prerequisites

- Node.js 24.x
- npm or yarn
- vLLM installation (or Docker container)
- Qwen3.5 27b 4-bit AWQ model weights

### Installation

1. Clone the repository
2. Install dependencies:
   ```bash
   npm install
   ```
3. Set up environment variables (see Configuration)
4. Start vLLM server (if running separately)
5. Run the backend:
   ```bash
   npm run dev
   ```

### Configuration

Copy `.env.example` to `.env` and configure:

```bash
# Server
PORT=8080
HOST=0.0.0.0

# LLM Configuration (OpenAI-compatible)
# For vLLM:
VLLM_HOST=localhost
VLLM_PORT=8000
VLLM_MODEL=Qwen/Qwen2-VL-7B-Instruct
VLLM_API_KEY=

# For OpenAI API:
# VLLM_HOST=https://api.openai.com/v1
# VLLM_PORT=443
# VLLM_MODEL=gpt-4o
# VLLM_API_KEY=your-api-key

# Logging
LOG_LEVEL=info
```

See `.env.example` for all available options.

## Architecture

### High-Level Design

```
┌──────────────────────────────────────────────────┐
│              Backend Service                      │
├──────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────┐  │
│  │           WebSocket Server                   │  │
│  │  - Client connections                        │  │
│  │  - Bidirectional streaming                   │  │
│  │  - Protocol handling                         │  │
│  └─────────────────────────────────────────────┘  │
│                        ▲                           │
│  ┌─────────────────────┼─────────────────────┐    │
│  │                     │                     │    │
│  ▼                     ▼                     ▼    │
│  ┌──────────────┐  ┌──────────────┐  ┌────────┐ │
│  │  Image       │  │  VLM         │  │  Hand- │ │
│  │  Processor   │◄─┤  Orchestrator│─►│  writing│ │
│  └──────────────┘  └──────────────┘  │  Renderer││
│         ▲                                └────────┘│
│         │                                         │
│         └─────────────────────────────────────────┘
│                    Data Flow
└──────────────────────────────────────────────────┘
```

### Component Details

#### WebSocket Server (`src/server/websocket.ts`)

- Manages client connections with session tracking
- Message validation using Zod schemas
- Routes messages to appropriate handlers
- Handles streaming responses to client
- Graceful connection lifecycle management

#### Image Processor (`src/image/processor.ts`)

- Decodes base64 image data (PNG/JPEG)
- Validates image format and extracts metadata
- Handles data URI prefixes
- Extracts dimensions from image headers

#### Image Cache (`src/image/cache.ts`)

- LRU cache for processed images
- Session-based organization
- Automatic cleanup of old entries

#### LLM Client (`src/llm/client.ts`)

- OpenAI SDK wrapper for compatibility
- Supports streaming and non-streaming modes
- Works with vLLM, OpenAI, or any OpenAI-compatible API
- Configurable via environment variables

#### LLM Orchestrator (`src/llm/orchestrator.ts`)

- Coordinates image → LLM → response flow
- Constructs vision model prompts
- Streams tokens from LLM

#### Handwriting Renderer (`src/renderer/handwriting.ts`)

- Canvas-based text rendering
- Uses "Caveat" handwriting font
- Adds natural variation (rotation, spacing)
- Streams incremental render chunks

#### Stream Coordinator (`src/stream/coordinator.ts`)

- Orchestrates full pipeline: image → LLM → renderer → client
- Buffers tokens for batched rendering
- Manages backpressure and flow control
- Handles errors gracefully

#### Session Manager (`src/session/manager.ts`)

- Tracks client sessions
- Stores session state (images, status, metadata)
- Automatic cleanup on disconnect

#### Error Handler (`src/middleware/errorHandler.ts`)

- Centralized error classification
- Standardized error responses
- Error context collection

#### Logger (`src/utils/logger.ts`)

- Structured logging with session tracking
- Performance timing utilities
- JSON output in production, colored in dev

- Validates incoming images
- Resizes/transforms if needed
- Compresses for transmission to VLM
- Caches images for potential reuse

#### VLM Orchestrator (`src/vlm/`)

- Communicates with vLLM server
- Sends images for handwriting recognition
- Receives and streams LLM response tokens
- Handles errors and retries

#### Handwriting Renderer (`src/renderer/`)

- Converts text to handwriting-style output
- Streams rendering instructions to client
- Supports incremental rendering
- Future: Style transfer from user's handwriting

## API & Protocol

### WebSocket Protocol

#### Client → Server Messages

**Send Image**
```json
{
  "type": "image",
  "data": "<base64-encoded-image>",
  "metadata": {
    "timestamp": 1234567890,
    "width": 1404,
    "height": 1872,
    "format": "png"
  }
}
```

#### Server → Client Messages

**Rendering Chunk**
```json
{
  "type": "render-chunk",
  "data": "<rendering-instructions>",
  "metadata": {
    "chunkIndex": 0,
    "totalChunks": 10,
    "progress": 0.1
  }
}
```

**Completion**
```json
{
  "type": "complete",
  "metadata": {
    "sessionId": "abc123",
    "duration": 5432
  }
}
```

**Error**
```json
{
  "type": "error",
  "message": "Error description",
  "code": "ERROR_CODE"
}
```

### HTTP Endpoints

- `GET /health` - Health check
- `GET /metrics` - Performance metrics (optional)
- `POST /admin/restart` - Restart services (admin only)

## VLM Integration

### Current Approach

Using vLLM with Qwen3.5 27b 4-bit AWQ:

- **Model**: Qwen3.5 27b (quantized to 4-bit AWQ)
- **Server**: vLLM for efficient inference
- **Capability**: Vision-language understanding for handwriting

### Research Questions

1. **Handwriting Recognition Quality**: How well does Qwen3.5 handle handwritten text?
2. **Incremental Images**: Can we send partial images and get incremental recognition?
3. **Image Prefix Caching**: Does vLLM support caching for image sequences?
4. **Latency**: What's the typical response time for image+text generation?

### Alternative Approaches

If VLM doesn't work well:

1. **Two-Step Process**:
   - Step 1: Dedicated handwriting recognition (e.g., Tesseract, specialized OCR)
   - Step 2: Text-only LLM for response generation

2. **Specialized Models**:
   - TrOCR (Transformer-based OCR)
   - PaddleOCR
   - Custom fine-tuned models

## Handwriting Rendering

### Current Implementation

**Font-based rendering using Canvas**

- Uses "Caveat" handwriting-style font
- Renders text incrementally as LLM generates tokens
- Adds slight randomization (rotation, spacing) for natural appearance
- Outputs PNG images streamed to client
- High contrast, e-ink compatible

See `HANDWRITING_RENDERING_RESEARCH.md` for detailed research and future enhancement options.

### Requirements

- Stream rendering as LLM generates tokens
- Ideally animate like writing (stroke-by-stroke)
- Match or complement user's handwriting style
- Output format compatible with e-ink display

### Research Questions

1. **Available Solutions**: What models/libraries exist for text-to-handwriting?
2. **Streaming**: Can rendering happen incrementally?
3. **Style Imitation**: Should/Can we copy user's handwriting? (Ethical considerations)
4. **Performance**: Can rendering keep up with LLM token generation?
5. **E-Ink Compatibility**: Output must work well on monochrome e-ink

### Potential Approaches

- **GAN-based**: Handwriting generation models
- **Transformer-based**: Sequence-to-stroke models
- **Rule-based**: Font rendering with handwriting-style fonts
- **Hybrid**: Pre-rendered character strokes composed dynamically

## Development Notes

### Performance Considerations

- **Image Caching**: Cache processed images to avoid redundant VLM calls
- **Streaming**: Stream both LLM tokens and rendering to minimize latency
- **Backpressure**: Handle cases where rendering can't keep up with generation
- **Concurrency**: Support multiple clients without resource contention

### Error Handling

- **VLM Failures**: Graceful fallback or retry logic
- **Connection Loss**: Preserve state for reconnection
- **Rendering Errors**: Fallback to text display if rendering fails

### Logging & Observability

- Structured logging for debugging
- Metrics for performance monitoring
- Tracing for request flow analysis

## Testing

### Unit Tests

- Image processing functions
- Protocol encoding/decoding
- Error handling paths

### Integration Tests

- VLM communication
- End-to-end image-to-rendering flow
- WebSocket message handling

### Performance Tests

- Latency measurements (image to first token, token to render)
- Concurrent client handling
- Memory usage under load

## Future Enhancements

- Multiple VLM backend support
- User handwriting style learning and imitation
- Local/offline mode with smaller models
- Enhanced rendering (animation, style variation)
- Multi-language support
- Batch processing for multiple images

## References

- [vLLM Documentation](https://docs.lmformat.io/)
- [Qwen Models](https://qwenlm.github.io/)
- [Android App Documentation](../android-app/README.md)
- [Supernote Hardware Reference](../docs/supernote-nomad-hardware.md)
