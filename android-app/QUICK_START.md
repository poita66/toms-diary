# Quick Start Guide

## Prerequisites

1. **Java 17** (Gradle 8.10 has issues with Java 21+)
2. **Android SDK** with command-line tools
3. **OpenAI-compatible LLM server** (vLLM, Ollama, etc.)

## Step 1: Start Your LLM Server

### Option A: vLLM (Recommended)
```bash
# Install vLLM
pip install vllm

# Start with a vision model
vllm serve --model Qwen/Qwen2.5-VL-7B-Instruct --port 8001
```

### Option B: Ollama
```bash
# Install Ollama
# https://ollama.com/download

# Pull a vision model
ollama pull llama3.2-vision

# Start server (default port 11434)
ollama serve
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
   - For vLLM: `http://localhost:8001/v1`
   - For Ollama: `http://localhost:11434/v1`
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
- For vLLM: `curl http://localhost:8001/v1/models` should return models
- For Ollama: `curl http://localhost:11434/api/tags` should return models

### "Model not found"
- Verify the model name in Settings
- For vLLM, the default model is `default`
- For Ollama, use the exact model name (e.g., `llama3.2-vision`)

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
2. In app Settings, use: `http://<your-ip>:8001/v1`
3. Ensure your LLM server accepts external connections:
   ```bash
   vllm serve --model ... --host 0.0.0.0 --port 8001
   ```

## Next Steps

- Read `LOCAL_PROCESSING.md` for detailed architecture information
- Check `AGENTS.md` for development guidelines
- Explore the code in `app/src/main/java/com/tomsdiary/`
