# Tom's Diary

This is a monorepo for Tom's Diary, a proof-of-concept handwritten chat app inspired by Tom Riddle's diary from Harry Potter and the Chamber of Secrets.

The basic idea is that the user writes a message to the AI agent, and it reads that, wipes the page, and "writes" out its reply — currently a live back-and-forth chat rather than an asynchronous journal.

## Hardware

The initial proof-of-concept was built on a Supernote Nomad (Android 11, Wacom EMR).

## Software

- **Android app**: captures handwritten input, sends it to a vision-capable LLM, and renders the streamed response back as handwriting — all on-device, no backend server involved.
- **LLM**: any OpenAI-compatible vision endpoint — a local one you run yourself ([LM Studio](https://lmstudio.ai/) and [Ollama](https://ollama.com/) are the easiest to set up; [llama.cpp](https://github.com/ggml-org/llama.cpp) and vLLM also work but need more manual setup), or a hosted one like OpenRouter or Hugging Face Inference.

## Quick Start

1. Get access to a vision-capable OpenAI-compatible endpoint. Easiest local option is [LM Studio](https://lmstudio.ai/): download a "VL" (vision-language) model, then start the local server from the Developer tab (default port `1234`). See [android-app/QUICK_START.md](android-app/QUICK_START.md) for llama.cpp, Ollama, and vLLM alternatives — or point the app at a hosted provider like OpenRouter instead.
2. Get the app onto your device:
   - **Download a prebuilt APK** from [Releases](https://github.com/poita66/toms-diary/releases) (see [Verifying the APK](#verifying-the-apk) below), or
   - **Build it yourself**:
     ```bash
     cd android-app
     ./gradlew assembleDebug
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     adb shell am start -n com.tomsdiary/.MainActivity
     ```
3. Open **Settings** in the app and set the LLM Base URL (e.g. `http://localhost:1234/v1`).
4. Write on the canvas and watch the response render as handwriting.

See [android-app/QUICK_START.md](android-app/QUICK_START.md) for full setup details and troubleshooting.

## Verifying the APK

Every release is built and signed by GitHub Actions from this repo's source — never on a developer's own machine — and comes with a [GitHub build provenance attestation](https://docs.github.com/en/actions/security-guides/using-artifact-attestations-to-establish-provenance-for-builds): a signed, publicly checkable statement binding that exact APK to the commit and workflow run that produced it.

To verify a downloaded APK matches what's in this repo:

```bash
gh attestation verify app-release.apk --repo poita66/toms-diary
```

This confirms the APK was built by this repo's `release.yml` workflow from a specific commit — not tampered with or substituted after the fact.

You can also check the release's signing certificate stays consistent across versions (proving successive releases come from the same signer, not just the same repo):

```bash
apksigner verify --print-certs app-release.apk
```

Current signing certificate SHA-256 fingerprint: `1B:9C:B6:EA:EC:0F:98:CF:D9:D2:73:60:90:41:74:FE:F9:C6:8E:13:AB:08:FA:31:72:C6:F3:19:94:AD:CA:13`

Each release also publishes a `.sha256` checksum file alongside the APK for a basic integrity check (`sha256sum -c app-release.apk.sha256`).

Note: this does **not** claim reproducible builds (i.e. that you could rebuild the exact same bytes yourself from source) — that hasn't been verified for this project. The attestation instead proves provenance: this binary came from this repo's CI, unmodified, whatever it built.

## Handwriting recognition & rendering

Handwriting recognition and rendering are implemented via a vision-capable LLM plus a local Caveat-font renderer — see [android-app/LOCAL_PROCESSING.md](android-app/LOCAL_PROCESSING.md) for how the pipeline works.

## Bugs

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
