# Quick Start Guide

## Prerequisites

1. **Java 17** (Gradle 8.10 has issues with Java 21+)
2. **Android SDK** with command-line tools
3. **OpenAI-compatible LLM server** with a **vision-capable** model (LM Studio, llama.cpp, Ollama, vLLM, etc.)

## Step 1: Start Your LLM Server

Any server exposing an OpenAI-compatible `/v1/chat/completions` endpoint works, as long as the model can read images. Pick whichever you already have set up:

### Option A: LM Studio (Easiest)
1. Download [LM Studio](https://lmstudio.ai/) and open it
2. Search for `unsloth/Qwen3.5-9B-GGUF` (or any other vision-capable model) and download the `Q4_K_M` quant
3. Go to the **Developer** tab → **Start Server** (default port `1234`)

### Option B: llama.cpp
```bash
# Build llama.cpp, or install a prebuilt release: https://github.com/ggml-org/llama.cpp

# -hf pulls the model straight from Hugging Face (multimodal projector included automatically)
llama-server -hf unsloth/Qwen3.5-9B-GGUF:Q4_K_M --port 8080
```

### Option C: Ollama
```bash
# Install Ollama
# https://ollama.com/download

# Pull a vision-capable model — either a named model:
ollama pull llama3.2-vision
# ...or pull a GGUF directly from Hugging Face:
ollama pull hf.co/unsloth/Qwen3.5-9B-GGUF:Q4_K_M

# Start server (default port 11434)
ollama serve
```

### Option D: vLLM
```bash
# Install vLLM
pip install vllm

# Start with a vision-capable model in a vLLM-supported format
# (vLLM's GGUF support is limited — an AWQ/GPTQ/full-precision checkpoint is more reliable)
vllm serve --model <your-model> --port 8001
```

## Step 2: Build and Install the App

```bash
cd android-app

# Build
./gradlew assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Start the app
adb shell am start -n com.tomsdiary/.MainActivity
```

## Step 3: Configure the App

1. Open the app
2. Tap **Settings** (gear icon)
3. Update **LLM Settings**:
   - For LM Studio: `http://localhost:1234/v1`
   - For llama.cpp: `http://localhost:8080/v1`
   - For Ollama: `http://localhost:11434/v1`
   - For vLLM: `http://localhost:8001/v1`
4. Tap **Save**

## Step 4: Write and Chat

1. Write on the canvas
2. Wait 2 seconds for auto-send (or tap **Send**)
3. Watch the response appear in handwriting
4. Swipe to view conversation history

## Troubleshooting

### "Connection refused" or "Failed to connect"
- Ensure your LLM server is running
- Check the URL in Settings matches your server
- For LM Studio: `curl http://localhost:1234/v1/models` should return models
- For llama.cpp: `curl http://localhost:8080/v1/models` should return models
- For Ollama: `curl http://localhost:11434/api/tags` should return models
- For vLLM: `curl http://localhost:8001/v1/models` should return models

### "Model not found"
- Verify the model name in Settings matches what your server has loaded
- For LM Studio and llama.cpp, use the exact model/file name shown by the server
- For Ollama, use the exact model name (e.g., `llama3.2-vision`)
- For vLLM, the default model is `default`

### Response text but no image understanding
- The model must be **vision-capable** — a text-only model will reply but won't "see" your handwriting
- For llama.cpp, make sure `--mmproj` points at the matching multimodal projector file for your model

### Slow responses
- Use `enable_thinking: false` (already configured in app)
- Try a smaller/faster model
- Reduce image detail (already set to 'low' in app)

### App won't install
- Ensure USB debugging is enabled on device
- Check `adb devices` shows your device
- Verify Java 17 is being used: `java -version`

## Default Configuration

- **Base URL**: `http://localhost:8001/v1`
- **API Key**: `placeholder` (not needed for local LLMs)
- **Model**: `default`
- **Persona**: Tom Riddle

## Network Configuration

### For Supernote Nomad
The device connects via USB/WiFi. If your LLM server is on the same network:

1. Find your computer's IP: `ip addr show` (Linux) or `ipconfig` (Windows)
2. In app Settings, use: `http://<your-ip>:<port>/v1` (matching whichever server you're running)
3. Ensure your LLM server accepts external connections:
   - **LM Studio**: In the Developer tab server settings, enable "Serve on Local Network"
   - **llama.cpp**: `llama-server -m ... --mmproj ... --host 0.0.0.0 --port 8080`
   - **Ollama**: `OLLAMA_HOST=0.0.0.0 ollama serve`
   - **vLLM**: `vllm serve --model ... --host 0.0.0.0 --port 8001`

## Next Steps

- Read `LOCAL_PROCESSING.md` for detailed architecture information
- Check `AGENTS.md` for development guidelines
- Explore the code in `app/src/main/java/com/tomsdiary/`
