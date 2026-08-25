# Tom's Diary

This is a monorepo for Tom's Diary, a proof-of-concept handwritten chat app inspired by Tom Riddle's diary from Harry Potter and the Chamber of Secrets.

The basic idea is that the user writes a message to the AI agent, and it reads that, wipes the page, and "writes" out its reply — currently a live back-and-forth chat rather than an asynchronous journal.

## Hardware

The initial proof-of-concept was built on a Supernote Nomad (Android 11, Wacom EMR).

## Software

- **Android app**: captures handwritten input, sends it to a vision-capable LLM, and renders the streamed response back as handwriting — all on-device, no backend server involved.
- **LLM**: any OpenAI-compatible vision endpoint that you run yourself — [LM Studio](https://lmstudio.ai/) and [llama.cpp](https://github.com/ggml-org/llama.cpp) are the easiest ways to get one running locally; Ollama and vLLM also work.

## Quick Start

1. Start a vision-capable OpenAI-compatible LLM server. Easiest option is [LM Studio](https://lmstudio.ai/): download a "VL" (vision-language) model, then start the local server from the Developer tab (default port `1234`). See [android-app/QUICK_START.md](android-app/QUICK_START.md) for llama.cpp, Ollama, and vLLM alternatives.
2. Build and install the Android app:
   ```bash
   cd android-app
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.tomsdiary/.MainActivity
   ```
3. Open **Settings** in the app and set the LLM Base URL (e.g. `http://localhost:1234/v1`).
4. Write on the canvas and watch the response render as handwriting.

See [android-app/QUICK_START.md](android-app/QUICK_START.md) for full setup details and troubleshooting.

## Handwriting recognition & rendering

Handwriting recognition and rendering are implemented via a vision-capable LLM plus a local Caveat-font renderer — see [android-app/LOCAL_PROCESSING.md](android-app/LOCAL_PROCESSING.md) for how the pipeline works.

## Bugs

- Early requests aren't properly cancelled - so the answer might not match the image
- Send button works on the response
- Cannot cancel a send
- Reconnection retries are too often
- Writing is still laggy (not bad, but laggy)

## Features

- **New Conversation**: "New" button starts a fresh conversation
- **Swipe Navigation**: Swipe left/right to navigate through conversation history
- **Immutable History**: Historical pages are read-only (can't write on them)
- **Touch to Clear**: Tap anywhere on response text to clear and start fresh
- **Stylus to Clear**: Pen down on response also clears and navigates to newest page

## TODO

- Settings:
  - Auto-send toggle
  - Auto-send timeout
  - Server URL
- Conversation branching - allow forking from any point in conversation history
