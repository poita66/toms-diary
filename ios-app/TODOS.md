# iOS Implementation TODOs

## Pre-Implementation

- [ ] Set up Xcode development environment
- [ ] Create Apple Developer account (if needed)
- [ ] Get an iPad with Apple Pencil for testing
- [ ] Copy Caveat-Regular.ttf from `backend/fonts/static/` to `ios-app/TomsDiary/Assets.xcassets/Fonts/`
- [ ] Create Xcode project using the provided structure

## Core Features

### Drawing Canvas

- [ ] Create Xcode project with SwiftUI
- [ ] Implement `DrawingView` with stroke capture
- [ ] Add Apple Pencil support (pressure, tilt)
- [ ] Implement palm rejection
- [ ] Add stroke storage and replay
- [ ] Implement `clear()` method
- [ ] Implement `addWord(_:at:)` for displaying AI responses
- [ ] Add word wrapping logic
- [ ] Implement `getBitmap()` for image capture
- [ ] Add read-only mode for history viewing

### Image Processing

- [ ] Implement `ImageProcessor.captureView(_:)`
- [ ] Implement `cropToHandwritingBounds(_:)`
- [ ] Implement `convertToGrayscale(_:)`
- [ ] Implement `imageToBase64(_:format:compression:)`
- [ ] Implement `getHandwritingBounds(in:)`
- [ ] Test with various handwriting samples

### LLM Integration

- [ ] Implement `OpenAIClient` with URLSession
- [ ] Add streaming response support with `AsyncThrowingStream`
- [ ] Implement `chatStreamWithImage(_:history:persona:)`
- [ ] Add transcription parsing (`[TRANSCRIPTION]` tags)
- [ ] Implement error handling
- [ ] Add timeout handling
- [ ] Implement cancellation support
- [ ] Test with vLLM local instance
- [ ] Test with different OpenAI-compatible APIs

### Handwriting Rendering

- [ ] Implement `HandwritingRenderer`
- [ ] Load Caveat font from bundle
- [ ] Implement `renderWord(_:options:)`
- [ ] Implement `renderWordStream(_:options:)`
- [ ] Add natural variations (rotation, offset)
- [ ] Implement `calculateFontSize(for:)`
- [ ] Test rendering quality
- [ ] Tune font size and spacing

### Settings & Configuration

- [ ] Implement `LLMConfig` persistence with UserDefaults
- [ ] Create `SettingsView` with SwiftUI Forms
- [ ] Add LLM URL configuration
- [ ] Add API key configuration
- [ ] Add model name configuration
- [ ] Add persona selection
- [ ] Implement settings validation
- [ ] Add configuration reset option

### Conversation Management

- [ ] Implement `Conversation` model
- [ ] Implement `ConversationTurn` model
- [ ] Implement `ConversationManager`
- [ ] Add conversation persistence (Core Data or SwiftData)
- [ ] Implement "New Conversation" functionality
- [ ] Add conversation list view
- [ ] Implement conversation selection
- [ ] Add conversation deletion
- [ ] Implement swipe navigation between turns

### Main View

- [ ] Create `ContentView` with SwiftUI
- [ ] Integrate `DrawingView`
- [ ] Add action buttons (New, Clear, Send)
- [ ] Add status bar
- [ ] Implement button actions
- [ ] Add loading states
- [ ] Add error display
- [ ] Implement auto-send timer
- [ ] Add haptic feedback

## Advanced Features

### Apple Pencil Enhancements

- [ ] Add pressure-based stroke weight
- [ ] Add tilt-based shading
- [ ] Implement Apple Pencil double-tap gesture
- [ ] Add palm rejection tuning
- [ ] Support Apple Pencil Pro features (if available)

### Performance

- [ ] Profile with Xcode Instruments
- [ ] Optimize rendering for large conversations
- [ ] Implement caching for rendered words
- [ ] Add background processing for image capture
- [ ] Optimize memory usage
- [ ] Add lazy loading for conversation history

### Export & Share

- [ ] Export conversations as images
- [ ] Export as PDF
- [ ] Share to other apps
- [ ] Save to Photos library
- [ ] Print support

### User Experience

- [ ] Add undo/redo for drawing
- [ ] Add drawing tools (eraser, different colors)
- [ ] Implement zoom/pan for large conversations
- [ ] Add search in conversation history
- [ ] Implement favorites/bookmarks
- [ ] Add tags or categories

### Accessibility

- [ ] VoiceOver support
- [ ] Dynamic Type support
- [ ] Reduce Motion respect
- [ ] High Contrast Mode support
- [ ] Switch Control support

## Testing

### Unit Tests

- [ ] Test `LLMConfig` serialization
- [ ] Test `ImageProcessor` methods
- [ ] Test `HandwritingRenderer` output
- [ ] Test `ConversationManager` persistence
- [ ] Test transcription parsing

### UI Tests

- [ ] Test drawing flow
- [ ] Test send and receive flow
- [ ] Test settings changes
- [ ] Test conversation navigation
- [ ] Test error scenarios

### Performance Tests

- [ ] Test rendering performance with long text
- [ ] Test memory usage with many conversations
- [ ] Test network performance with slow connections
- [ ] Test battery impact

## Deployment

### App Store Preparation

- [ ] Create App Store Connect app record
- [ ] Design app icon (1024x1024)
- [ ] Create screenshots for all device sizes
- [ ] Write app description
- [ ] Write privacy policy
- [ ] Configure App Store pricing
- [ ] Submit for review

### Build Configuration

- [ ] Set up CI/CD with GitHub Actions or similar
- [ ] Configure TestFlight for beta testing
- [ ] Set up crash reporting (Firebase Crashlytics)
- [ ] Configure analytics (if desired)

## Post-Launch

- [ ] Monitor crash reports
- [ ] Collect user feedback
- [ ] Plan version 2.0 features
- [ ] Add cloud sync with iCloud
- [ ] Add multiple diary support
- [ ] Add custom personas
- [ ] Add theme support
- [ ] Add widget support

## Nice to Have

- [ ] Siri Shortcuts integration
- [ ] Share extension for importing text
- [ ] Today widget for quick notes
- [ ] Handoff support between devices
- [ ] Apple Watch companion app
- [ ] Augmented reality features
- [ ] Voice input support
- [ ] Translation features
- [ ] Sentiment analysis
- [ ] Statistics and insights

## Known Challenges

- [ ] **Font Loading**: Ensure Caveat font loads on all devices
- [ ] **Network Reliability**: Handle offline scenarios gracefully
- [ ] **Memory Management**: Large conversations may cause issues
- [ ] **Apple Pencil Latency**: Minimize input lag for natural feel
- [ ] **ePaper Emulation**: If targeting ePaper-like displays, optimize refresh
- [ ] **App Store Review**: Ensure content guidelines are met (Harry Potter references)

## Notes

- Start with MVP (Core Features only)
- Test early and often on real iPad
- Profile before optimizing
- Get user feedback before adding advanced features
- Consider starting with paid app to validate concept
