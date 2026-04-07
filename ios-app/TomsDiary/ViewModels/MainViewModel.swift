//
//  MainViewModel.swift
//  Tom's Diary - iOS
//
//  Main view model for coordinating app logic.
//

import Foundation
import Combine
import UIKit

@MainActor
final class MainViewModel: ObservableObject {
    
    // MARK: - Published Properties
    
    @Published var statusText: String = "Ready"
    @Published var statusColor: Color = .green
    @Published var isProcessing = false
    @Published var currentPersona: Persona = .tom
    
    // MARK: - Private Properties
    
    private var openAIClient: OpenAIClient
    private let handwritingRenderer = HandwritingRenderer()
    private var conversationManager = ConversationManager()
    private var autoSendTimer: Timer?
    private var currentTranscription: String = ""
    private var currentResponse: String = ""
    
    // DrawingView reference (weak to avoid retain cycle)
    weak var drawingView: DrawingView?
    
    // Auto-send delay
    private let autoSendDelay: TimeInterval = 2.0
    
    // MARK: - Initialization
    
    init(config: LLMConfig = .default) {
        self.openAIClient = OpenAIClient(config: config)
    }
    
    func setup() {
        // Load existing conversation or create new one
        if conversationManager.currentConversation == nil {
            _ = conversationManager.createNewConversation()
        }
        
        // Load saved persona
        currentPersona = Persona(rawValue: UserDefaults.standard.string(forKey: "selected_persona") ?? "tom") ?? .tom
    }
    
    // MARK: - Public Methods
    
    func startNewConversation() {
        cancelAutoSend()
        _ = conversationManager.createNewConversation()
        drawingView?.clear()
        drawingView?.resetWordPosition()
        currentTranscription = ""
        currentResponse = ""
        updateStatus("New conversation", .green)
    }
    
    func clearCanvas() {
        cancelAutoSend()
        drawingView?.clear()
        drawingView?.resetWordPosition()
        updateStatus("Canvas cleared", .blue)
    }
    
    func scheduleAutoSend() {
        // If already processing, cancel it
        if isProcessing {
            isProcessing = false
            drawingView?.setProcessing(false)
            updateStatus("Request cancelled", .yellow)
            return
        }
        
        cancelAutoSend()
        autoSendTimer = Timer.scheduledTimer(withTimeInterval: autoSendDelay, repeats: false) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.sendCanvasImage()
            }
        }
    }
    
    func sendCanvasImage() {
        cancelAutoSend()
        
        // Prevent concurrent requests
        guard !isProcessing else {
            print("Already processing, ignoring")
            return
        }
        
        isProcessing = true
        drawingView?.setProcessing(true)
        updateStatus("Sending...", .blue)
        drawingView?.resetWordPosition()
        
        Task {
            await processImage()
        }
    }
    
    // MARK: - Private Methods
    
    private func processImage() async {
        do {
            // 1. Capture canvas
            guard let image = drawingView?.getBitmap() else {
                throw NSError(domain: "ImageCapture", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to capture canvas"])
            }
            
            // 2. Get handwriting bounds and crop
            let bounds = drawingView?.getHandwritingBounds()
            let croppedImage: UIImage
            
            if let bounds = bounds {
                // Add padding to bounds
                let paddedBounds = bounds.insetBy(dx: -20, dy: -20)
                croppedImage = ImageProcessor.cropImage(image, to: paddedBounds) ?? image
            } else {
                croppedImage = image
            }
            
            // 3. Convert to grayscale
            let grayscaleImage = ImageProcessor.convertToGrayscale(croppedImage) ?? croppedImage
            
            // 4. Convert to base64
            guard let base64 = ImageProcessor.imageToBase64(grayscaleImage) else {
                throw NSError(domain: "ImageProcessing", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to encode image"])
            }
            
            print("Image processed: \(grayscaleImage.size.width)x\(grayscaleImage.size.height), base64 length: \(base64.count)")
            
            // 5. Get conversation history
            let history = conversationManager.currentConversation?.turns ?? []
            
            // 6. Call LLM with streaming
            var fullResponse = ""
            var transcription = ""
            var inTranscription = false
            
            updateStatus("Processing...", .blue)
            
            for try await token in openAIClient.chatStreamWithImage(
                imageBase64: base64,
                history: history,
                persona: currentPersona
            ) {
                fullResponse += token
                
                // Parse transcription
                if token.contains("[TRANSCRIPTION]") {
                    inTranscription = true
                }
                
                if inTranscription {
                    if token.contains("[/TRANSCRIPTION]") {
                        inTranscription = false
                    } else {
                        transcription += token
                    }
                }
                
                updateStatus("Writing...", .blue)
            }
            
            currentResponse = fullResponse
            currentTranscription = transcription
            
            print("Response received: \(fullResponse.count) chars, transcription: \(transcription.count) chars")
            
            // 7. Clear canvas and render response
            drawingView?.clear()
            drawingView?.resetWordPosition()
            
            // Extract the actual response (after transcription)
            let responseText: String
            if fullResponse.contains("[/TRANSCRIPTION]") {
                responseText = String(fullResponse.split(separator: "[/TRANSCRIPTION]").last ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            } else {
                responseText = fullResponse
            }
            
            print("Rendering response: \(responseText)")
            
            // 8. Render response locally
            try await renderResponse(responseText)
            
            // 9. Save to history
            if !currentTranscription.isEmpty {
                let turn = ConversationTurn(userText: currentTranscription, assistantText: currentResponse)
                conversationManager.addTurn(to: conversationManager.currentConversation!, turn: turn)
            }
            
            isProcessing = false
            drawingView?.setProcessing(false)
            updateStatus("Response complete", .green)
            
        } catch {
            isProcessing = false
            drawingView?.setProcessing(false)
            updateStatus("Error: \(error.localizedDescription)", .red)
            print("Processing error: \(error)")
        }
    }
    
    private func renderResponse(_ text: String) async throws {
        guard let drawingView = drawingView else { return }
        
        // Calculate font size based on screen width
        let screenWidth = drawingView.bounds.width
        let fontSize = handwritingRenderer.calculateFontSize(for: screenWidth)
        
        let options = HandwritingRenderer.RenderOptions(
            fontSize: fontSize,
            backgroundColor: .white,
            textColor: .black,
            padding: 20,
            maxWidth: screenWidth - 80,  // Leave padding on both sides
            addVariation: true
        )
        
        print("Rendering with font size: \(fontSize), max width: \(options.maxWidth)")
        
        // Stream words and display them
        for await wordImage in handwritingRenderer.renderWordStream(text, options: options) {
            let position = drawingView.getNextWordPosition()
            drawingView.addWord(wordImage, at: position)
            
            // Small delay for smooth streaming effect (50ms)
            try await Task.sleep(nanoseconds: 50_000_000)
        }
    }
    
    private func cancelAutoSend() {
        autoSendTimer?.invalidate()
        autoSendTimer = nil
    }
    
    private func updateStatus(_ text: String, _ color: Color) {
        statusText = text
        statusColor = color
    }
    
    // MARK: - Deinit
    
    deinit {
        cancelAutoSend()
    }
}
