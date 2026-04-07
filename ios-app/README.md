# Tom's Diary - iOS App

AI-powered handwritten journal for iPad with Apple Pencil support.

## Features

- **Handwriting Input**: Write naturally with Apple Pencil
- **AI Responses**: Get responses from Tom Riddle (or other personas) rendered as handwriting
- **Local Processing**: Direct OpenAI API calls - no backend server required
- **Conversation History**: Swipe through past conversations
- **Customizable LLM**: Connect to any OpenAI-compatible API (vLLM, Ollama, etc.)

## Architecture

```
TomsDiary/
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

## Build & Run

### Prerequisites

- Xcode 15.0+
- iOS 17.0+ deployment target
- iPad with Apple Pencil (for testing)

### Building

```bash
# Open the project in Xcode
cd ios-app
open TomsDiary.xcodeproj

# Or build from command line
xcodebuild -project TomsDiary.xcodeproj -scheme TomsDiary -configuration Debug build
```

### Running on Simulator

```bash
# Select a simulator device
xcrun simctl boot "iPad Pro (12.9-inch) (6th generation)"

# Run the app
xcodebuild -project TomsDiary.xcodeproj -scheme TomsDiary -destination 'platform=iOS Simulator,name=iPad Pro (12.9-inch) (6th generation)' clean build
```

### Running on Device

1. Connect iPad via USB
2. In Xcode, select your iPad from the device list
3. Trust the device on the iPad
4. Build and run (Cmd+R)

## Configuration

### LLM Settings

Accessed via the **Settings** button (gear icon) in the app:

- **Base URL**: `http://YOUR_IP:8001/v1` (your computer's IP, not localhost)
- **API Key**: `placeholder` (optional for local LLMs)
- **Model**: `default` (or your specific model name)

### Important: Network Configuration

When testing on a real iPad:

1. Find your computer's IP address:
   ```bash
   # macOS
   ipconfig getifaddr en0
   
   # Linux
   hostname -I | awk '{print $1}'
   ```

2. Update the Base URL in app settings with your IP
3. Ensure vLLM accepts external connections:
   ```bash
   vllm serve --model your-model --host 0.0.0.0 --port 8001
   ```

4. Check firewall settings allow incoming connections on port 8001

## Project Structure

### DrawingView

Custom `UIView` that handles:
- Apple Pencil input with pressure and tilt support
- Stroke rendering with quadratic curves for smooth lines
- Guide lines for handwriting
- Word display for AI responses
- Image capture for LLM processing

### OpenAIClient

Handles communication with OpenAI-compatible APIs:
- Streaming responses via `AsyncThrowingStream`
- Multimodal messages (image + text)
- Transcription parsing (`[TRANSCRIPTION]` tags)
- 120-second timeout for long responses

### HandwritingRenderer

Renders text as handwritten images:
- Uses Caveat font for natural handwriting appearance
- Word-by-word streaming for smooth display
- Natural variations (rotation, offset) for realism
- Dynamic font sizing based on screen width

### MainViewModel

Coordinates app logic:
- Canvas capture and processing
- LLM communication
- Response rendering
- Conversation management
- Auto-send timer (2 second delay)

## Development

### Adding a New Persona

Edit `Models/Persona.swift`:

```swift
enum Persona: String, CaseIterable, Identifiable {
    case tom = "tom"
    case generic = "generic"
    case friendly = "friendly"
    case yourPersona = "your_persona"  // Add new persona
    
    var displayName: String {
        switch self {
        case .yourPersona:
            return "Your Persona Name"
        // ... other cases
        }
    }
    
    var systemPrompt: String {
        switch self {
        case .yourPersona:
            return """
            Your system prompt here...
            """
        // ... other cases
        }
    }
}
```

### Adjusting Font Size

Modify `calculateFontSize(for:)` in `Services/HandwritingRenderer.swift`:

```swift
func calculateFontSize(for screenWidth: CGFloat) -> CGFloat {
    // Current: screenWidth / 14, clamped to 48-120
    return max(48, min(120, screenWidth / 14))
}
```

### Changing Line Spacing

Update constants in `Views/DrawingView.swift`:

```swift
private let lineHeight: CGFloat = 150  // Adjust as needed
private let firstLineY: CGFloat = 180  // Starting Y position
```

## Known Issues

1. **Font Loading**: Ensure `Caveat-Regular.ttf` is in `Assets.xcassets/Fonts/` and registered in `Info.plist`
2. **Network Connectivity**: iPad must be on same WiFi as LLM server; use IP address, not localhost
3. **Apple Pencil in Simulator**: Simulator doesn't support Apple Pencil; test on real device
4. **Memory Usage**: Large conversations may cause memory pressure; consider implementing caching

## Testing

### Unit Tests

```bash
xcodebuild -project TomsDiary.xcodeproj -scheme TomsDiary -destination 'platform=iOS Simulator,name=iPad Pro (12.9-inch) (6th generation)' test
```

### Manual Testing Checklist

- [ ] Draw with Apple Pencil
- [ ] Send handwriting to LLM
- [ ] Verify transcription accuracy
- [ ] Check response rendering
- [ ] Test conversation history
- [ ] Verify settings persistence
- [ ] Test auto-send timer
- [ ] Test cancel during processing

## Future Enhancements

- [ ] Undo/redo for drawing
- [ ] Export conversations as PDF
- [ ] iCloud sync
- [ ] Multiple diaries
- [ ] Custom personas
- [ ] Theme support
- [ ] Widget support
- [ ] Siri Shortcuts integration

## License

Same as the main project.

## Credits

- **Caveat Font**: By Cyber Digital (Google Fonts)
- **Based on**: Android app by the same author
