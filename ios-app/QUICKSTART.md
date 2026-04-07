# iOS Quick Start Guide

Get the iOS app running in 5 minutes.

## Prerequisites

- [ ] Xcode 15.0+ installed
- [ ] iPad with Apple Pencil (or iPad Simulator)
- [ ] LLM server running (vLLM, Ollama, etc.)

## Step 1: Start Your LLM Server

```bash
# For simulator testing
vllm serve --model your-model --port 8001

# For real iPad testing (find your IP first)
# macOS: ipconfig getifaddr en0
# Then:
vllm serve --model your-model --host 0.0.0.0 --port 8001
```

## Step 2: Open the Project

```bash
cd ios-app
open TomsDiary.xcodeproj
```

## Step 3: Build and Run

### Option A: Using Xcode

1. Select target: `iPad Pro (12.9-inch) (6th generation)` or your connected iPad
2. Click **Run** (Cmd+R)

### Option B: Using Build Script

```bash
# Build only
./build.sh build

# Build and run on simulator
./build.sh run
```

## Step 4: Configure LLM Settings

1. Launch the app
2. Tap the **gear icon** (⚙️) in the top right
3. Update settings:
   - **Base URL**: 
     - Simulator: `http://localhost:8001/v1`
     - Real iPad: `http://YOUR_IP:8001/v1`
   - **API Key**: `placeholder` (or your key)
   - **Model**: `default` (or your model name)
4. Tap **Save**

## Step 5: Write and Send

1. Use Apple Pencil to write on the canvas
2. Wait 2 seconds for auto-send, or tap **Send**
3. Watch as the AI responds in handwriting!

## Troubleshooting

### "Connection Refused"
- ✅ LLM server is running
- ✅ Correct IP address (not localhost on real device)
- ✅ Firewall allows port 8001
- ✅ vLLM started with `--host 0.0.0.0` (for device access)

### "Font Not Found"
- ✅ Caveat-Regular.ttf is in `Assets.xcassets/Fonts/`
- ✅ Font registered in `Info.plist` under `UIAppFonts`
- ✅ Clean build: Product → Clean Build Folder (Shift+Cmd+K)

### "Drawing Laggy"
- ✅ Test on real iPad (Simulator doesn't have Apple Pencil)
- ✅ Check Xcode → Account → ensure device is trusted

### "No Response"
- ✅ Check LLM server logs
- ✅ Verify image is being sent (check console logs)
- ✅ Try with simpler model first

## Common Commands

```bash
# Find your computer's IP (for iPad testing)
# macOS
ipconfig getifaddr en0

# Linux
hostname -I | awk '{print $1}'

# Test LLM connection
curl http://localhost:8001/v1/models

# Build and install on connected device
xcodebuild -project TomsDiary.xcodeproj -scheme TomsDiary -destination 'id=YOUR_DEVICE_ID' clean build

# List connected devices
xcrun devicectl list devices
```

## Next Steps

- [Read the full README](README.md)
- [Implementation details](IMPLEMENTATION_SUMMARY.md)
- [Development guide](../AGENTS.md)

## Support

For issues:
1. Check Xcode console logs
2. Check LLM server logs
3. Verify network connectivity
4. Review [Known Issues](IMPLEMENTATION_SUMMARY.md#known-issues)
