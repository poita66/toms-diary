# iOS Quick Reference

## File Structure

```
ios-app/
├── README.md                    # Main documentation
├── IMPLEMENTATION_GUIDE.md      # Step-by-step guide
├── QUICK_REFERENCE.md           # This file
└── TomsDiary/
    ├── App/
    │   └── TomsDiaryApp.swift  # App entry point
    ├── Models/
    │   ├── LLMConfig.swift     # LLM configuration
    │   ├── Persona.swift        # Persona definitions
    │   └── Conversation.swift   # Conversation models
    ├── Services/
    │   ├── OpenAIClient.swift      # OpenAI API client
    │   ├── HandwritingRenderer.swift  # Text rendering
    │   └── ImageProcessor.swift    # Image processing
    ├── ViewModels/
    │   └── MainViewModel.swift     # Main logic
    ├── Views/
    │   ├── ContentView.swift       # Main view
    │   ├── DrawingView.swift       # Canvas
    │   └── SettingsView.swift      # Settings
    ├── Assets.xcassets/
    │   └── Fonts/
    │       └── Caveat-Regular.ttf  # Handwriting font
    └── Info.plist                  # App configuration
```

## Key Classes

| Class | Purpose | Key Methods |
|-------|---------|-------------|
| `LLMConfig` | LLM settings | `load()`, `save()`, `validate()` |
| `Persona` | AI character | `systemPrompt`, `displayName` |
| `OpenAIClient` | API calls | `chatStreamWithImage()` |
| `HandwritingRenderer` | Text rendering | `renderWord()`, `renderWordStream()` |
| `ImageProcessor` | Image ops | `captureView()`, `cropToHandwritingBounds()` |
| `DrawingView` | Canvas | `addWord()`, `clear()`, `getBitmap()` |

## Configuration

### LLM Settings (LLMConfig.swift)

```swift
static let `default` = LLMConfig(
    baseUrl: "http://localhost:8001/v1",  // Change to your endpoint
    apiKey: "placeholder",                 // Change if needed
    model: "default"                       // Change to your model
)
```

### Font (Info.plist)

```xml
<key>UIAppFonts</key>
<array>
    <string>Caveat-Regular.ttf</string>
</array>
```

## Common Code Patterns

### Capture and Process Image

```swift
let image = ImageProcessor.captureView(drawingView)
let cropped = ImageProcessor.cropToHandwritingBounds(image)
let grayscale = ImageProcessor.convertToGrayscale(cropped)
let base64 = ImageProcessor.imageToBase64(grayscale)
```

### Call LLM with Streaming

```swift
for try await token in openAIClient.chatStreamWithImage(
    imageBase64: base64,
    history: history,
    persona: .tom
) {
    // Handle token
}
```

### Render Text as Handwriting

```swift
let renderer = HandwritingRenderer()
let fontSize = renderer.calculateFontSize(for: screenWidth)

for try await wordImage in renderer.renderWordStream(text) {
    drawingView.addWord(wordImage, at: position)
}
```

### Save Conversation

```swift
let turn = ConversationTurn(
    userText: transcription,
    assistantText: response
)
conversationManager.addTurn(to: conversation, turn: turn)
```

## Testing Checklist

- [ ] Font loads correctly
- [ ] Drawing responds to Apple Pencil
- [ ] Image captures handwriting
- [ ] LLM connection works
- [ ] Streaming displays smoothly
- [ ] Handwriting renders correctly
- [ ] Settings persist
- [ ] Conversation history works
- [ ] No memory leaks
- [ ] Works on real iPad

## Build Commands

```bash
# Build
xcodebuild -project TomsDiary.xcodeproj -scheme TomsDiary -sdk ipados build

# Run tests
xcodebuild -project TomsDiary.xcodeproj -scheme TomsDiary -sdk ipados test

# Archive
xcodebuild -project TomsDiary.xcodeproj -scheme TomsDiary -sdk ipados archive
```

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Font not found | Check `UIAppFonts` in Info.plist |
| Can't draw | Check `isUserInteractionEnabled` |
| LLM timeout | Increase timeout in `OpenAIClient` |
| Words overlap | Adjust `wordSpacing` in `DrawingView` |
| Slow rendering | Reduce font size or disable variation |

## Ports from Android

| Android | iOS |
|---------|-----|
| SharedPreferences | UserDefaults |
| Coroutines | async/await |
| LiveData/StateFlow | @Published / Combine |
| Bitmap | UIImage |
| Canvas | UIGraphicsContext / Core Graphics |
| GestureDetector | UIGestureRecognizer |

## Resources

- [Swift Documentation](https://docs.swift.org/swift-book/)
- [SwiftUI Documentation](https://developer.apple.com/documentation/swiftui)
- [Core Graphics](https://developer.apple.com/documentation/coregraphics)
- [Combine](https://developer.apple.com/documentation/combine)
