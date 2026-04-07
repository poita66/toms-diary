# iOS Implementation Guide

## Step-by-Step Implementation Plan

### Phase 1: Project Setup (Day 1)

#### 1.1 Create Xcode Project

```bash
# In Xcode:
# File -> New -> Project
# Select "App" under iOS
# Product Name: TomsDiary
# Interface: SwiftUI
# Language: Swift
# Devices: iPad (can also support iPhone)
```

#### 1.2 Add Dependencies

No external dependencies needed - all functionality uses built-in frameworks:
- UIKit (for DrawingView)
- SwiftUI (for UI)
- Combine (for streaming)
- URLSession (for HTTP)

#### 1.3 Add Caveat Font

1. Copy `Caveat-Regular.ttf` from `backend/fonts/static/` to `TomsDiary/Assets.xcassets/Fonts/`
2. In Xcode, select the font file
3. In File Inspector, set "Target Membership" to TomsDiary
4. Add to `Info.plist` under `UIAppFonts` (already done in template)

#### 1.4 Configure Info.plist

The template `Info.plist` is already configured with:
- Font registration
- Device orientations
- Photo library access (for future export features)

### Phase 2: Drawing Canvas (Days 2-3)

#### 2.1 Implement DrawingView

1. Copy `DrawingView.swift` from the template
2. Test basic stroke drawing:
   ```swift
   // In ContentView, replace with actual DrawingView
   DrawingView()
       .frame(maxWidth: .infinity, maxHeight: .infinity)
   ```

3. Test Apple Pencil:
   - Palm rejection should work automatically
   - Pressure sensitivity available via `gesture.force`
   - Tilt available via `gesture.tilt`

#### 2.2 Add Stroke Storage

- Strokes are stored in `[[CGPoint]]`
- Each stroke is a path of points
- Implement undo/redo if needed

#### 2.3 Add Word Display

- Implement `addWord(_:at:)` method
- Test with sample images
- Ensure proper positioning and wrapping

### Phase 3: Image Processing (Day 4)

#### 3.1 Capture Canvas

```swift
let image = ImageProcessor.captureView(drawingView)
```

#### 3.2 Crop to Handwriting

```swift
let cropped = ImageProcessor.cropToHandwritingBounds(image)
```

#### 3.3 Convert to Grayscale

```swift
let grayscale = ImageProcessor.convertToGrayscale(cropped)
```

#### 3.4 Encode as Base64

```swift
let base64 = ImageProcessor.imageToBase64(grayscale)
```

### Phase 4: LLM Integration (Days 5-6)

#### 4.1 Implement OpenAIClient

1. Copy `OpenAIClient.swift` from template
2. Update `LLMConfig.default` with your LLM endpoint:
   ```swift
   static let `default` = LLMConfig(
       baseUrl: "http://YOUR_IP:8001/v1",  // Your computer's IP
       apiKey: "placeholder",
       model: "default"
   )
   ```

#### 4.2 Test Connection

```swift
let client = OpenAIClient()
// Test with a simple message
```

#### 4.3 Handle Streaming

- The template uses `AsyncThrowingStream`
- Tokens are yielded one at a time
- Parse `[TRANSCRIPTION]` tags

#### 4.4 Network Configuration

For iPad to reach your computer:

1. Find your computer's IP:
   ```bash
   ipconfig getifaddr en0  # macOS WiFi
   # or
   ifconfig | grep "inet "
   ```

2. Update `LLMConfig.default.baseUrl` with your IP
3. Ensure vLLM accepts external connections:
   ```bash
   vllm serve --model ... --host 0.0.0.0 --port 8001
   ```

### Phase 5: Handwriting Rendering (Day 7)

#### 5.1 Implement HandwritingRenderer

1. Copy `HandwritingRenderer.swift` from template
2. Test font loading:
   ```swift
   let renderer = HandwritingRenderer()
   let image = renderer.renderWord("Hello")
   ```

#### 5.2 Test Word Streaming

```swift
for try await wordImage in renderer.renderWordStream("Hello world") {
    // Add to DrawingView
}
```

#### 5.3 Tune Appearance

- Adjust font size in `calculateFontSize(for:)`
- Modify padding in `RenderOptions`
- Enable/disable variation

### Phase 6: Integration (Days 8-9)

#### 6.1 Connect ViewModel to Views

1. Update `ContentView` to use actual `DrawingView`
2. Wire up buttons to `MainViewModel` methods
3. Test full flow: write → send → receive → display

#### 6.2 Add Settings

1. Copy `SettingsView.swift` and `SettingsViewModel`
2. Test LLM configuration persistence
3. Test persona switching

#### 6.3 Add Conversation History

1. Use `ConversationManager` from template
2. Implement swipe gestures for navigation
3. Add "New Conversation" button

### Phase 7: Polish (Day 10+)

#### 7.1 Error Handling

- Add error alerts
- Show loading states
- Handle network failures gracefully

#### 7.2 Performance

- Profile with Xcode Instruments
- Optimize rendering if needed
- Add caching for rendered words

#### 7.3 Apple Pencil Features

- Add pressure-based stroke weight
- Add tilt-based shading
- Support Apple Pencil double-tap gestures

#### 7.4 Testing

- Write unit tests for services
- Write UI tests for workflows
- Test on real iPad with Apple Pencil

## Common Issues & Solutions

### Issue: Font doesn't load

**Solution:**
1. Check font is in `Assets.xcassets`
2. Verify `UIAppFonts` in `Info.plist`
3. Check target membership in Xcode
4. Clean build folder (Shift+Cmd+K)

### Issue: Can't connect to LLM

**Solution:**
1. Check iPad and computer are on same WiFi
2. Use computer's IP, not `localhost`
3. Check firewall settings on computer
4. Verify vLLM is running with `--host 0.0.0.0`

### Issue: Drawing laggy

**Solution:**
1. Use `CADisplayLink` instead of gesture recognizer
2. Reduce stroke point density
3. Use `Core Graphics` directly instead of `CAShapeLayer`

### Issue: Words don't wrap correctly

**Solution:**
1. Check `lineHeight` in `DrawingView`
2. Verify `advanceWordPosition` logic
3. Test with different screen sizes

## Build & Deploy

### Debug Build

```bash
# In Xcode:
# Product -> Build (Cmd+B)
# Product -> Run (Cmd+R)
```

### Install on Device

1. Connect iPad via USB
2. Select device in Xcode
3. Trust the device on iPad
4. Build and run

### Test Flight (for beta testing)

1. Enroll in Apple Developer Program
2. Create App Store Connect app record
3. Upload build via Xcode
4. Add testers
5. Test via Test Flight app

## Next Steps After MVP

1. **App Store Submission**
   - Create app icon and screenshots
   - Write app description
   - Submit for review

2. **Advanced Features**
   - Export to PDF
   - Share to other apps
   - Cloud sync with iCloud
   - Multiple diaries

3. **Performance Optimizations**
   - Metal-based rendering
   - Background processing
   - Caching strategies

4. **Accessibility**
   - VoiceOver support
   - Dynamic Type
   - Reduce Motion respect

## Resources

- [SwiftUI Tutorials](https://developer.apple.com/tutorials/swiftui)
- [Produce Graphics with Core Graphics](https://developer.apple.com/documentation/coregraphics)
- [Apple Pencil Guide](https://developer.apple.com/design/human-interface-guidelines/apple-pencil)
- [Combine Framework](https://developer.apple.com/documentation/combine)

## Support

For issues or questions, refer to the main project repository or create an issue.
