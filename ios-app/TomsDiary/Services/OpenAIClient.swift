//
//  OpenAIClient.swift
//  Tom's Diary - iOS
//
//  Client for interacting with any OpenAI-compatible API.
//  Supports streaming responses for real-time handwriting rendering.
//

import Foundation

// MARK: - Client

final class OpenAIClient {
    
    // MARK: - Properties
    
    private let session: URLSession
    private let config: LLMConfig
    
    // MARK: - Initialization
    
    init(config: LLMConfig = .default, session: URLSession = .shared) {
        self.config = config
        self.session = session
    }
    
    // MARK: - Public Methods
    
    /// Sends an image and receives streaming text response
    /// - Parameters:
    ///   - imageBase64: Base64-encoded PNG image
    ///   - history: Previous conversation turns
    ///   - persona: Persona to use for the response
    /// - Returns: Async sequence of response tokens
    func chatStreamWithImage(
        imageBase64: String,
        history: [ConversationTurn],
        persona: Persona = .tom
    ) async throws -> AsyncThrowingStream<String, Error> {
        
        let systemPrompt = persona.systemPrompt
        let messages = buildMultimodalMessages(systemPrompt: systemPrompt, imageBase64: imageBase64, history: history)
        
        let request = try buildRequest(messages: messages, streaming: true)
        
        return try await streamResponse(from: request)
    }
    
    /// Sends text-only messages and receives streaming response
    func chatStream(
        messages: [ChatMessage],
        persona: Persona = .tom
    ) async throws -> AsyncThrowingStream<String, Error> {
        
        let systemPrompt = persona.systemPrompt
        let finalMessages = [ChatMessage(role: .system, content: systemPrompt)] + messages
        
        let request = try buildRequest(messages: finalMessages.map { ["role": $0.role.rawValue, "content": $0.content] }, streaming: true)
        return try await streamResponse(from: request)
    }
    
    // MARK: - Private Methods
    
    private func buildMultimodalMessages(systemPrompt: String, imageBase64: String, history: [ConversationTurn]) -> [[String: Any]] {
        var messages: [[String: Any]] = []
        
        // System message
        messages.append(["role": "system", "content": systemPrompt])
        
        // History as text only (limit to last 3 turns)
        let limitedHistory = Array(history.suffix(3))
        for turn in limitedHistory {
            messages.append(["role": "user", "content": turn.userText])
            messages.append(["role": "assistant", "content": turn.assistantText])
        }
        
        // Current message with image
        let content: [[String: Any]] = [
            ["type": "image_url", "image_url": ["url": "data:image/png;base64,\(imageBase64)", "detail": "low"]],
            ["type": "text", "text": "Read this handwritten note. First output the exact transcription in this format: [TRANSCRIPTION]<exact text as written>[/TRANSCRIPTION]\n\nThen on a new line, respond to it in a warm, conversational way. CRITICAL: Keep your response VERY SHORT - 2-3 sentences maximum, no more than 50 words. It will be displayed on a small ePaper screen. IMPORTANT: Use proper punctuation throughout your response - include commas, periods, question marks, and apostrophes where grammatically appropriate."]
        ]
        messages.append(["role": "user", "content": content])
        
        return messages
    }
    
    private func buildRequest(messages: [[String: Any]], streaming: Bool) throws -> URLRequest {
        guard let url = URL(string: "\(config.baseUrl)/chat/completions") else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(config.apiKey)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 120  // 120 second timeout
        
        let body: [String: Any] = [
            "model": config.model,
            "stream": streaming,
            "max_completion_tokens": 16384,
            "chat_template_kwargs": ["enable_thinking": false],
            "include_reasoning": false,
            "messages": messages
        ]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        
        return request
    }
    
    private func streamResponse(from request: URLRequest) async throws -> AsyncThrowingStream<String, Error> {
        return AsyncThrowingStream<String, Error> { continuation in
            
            let task = URLSession.shared.dataTaskPublisher(for: request)
                .receive(on: DispatchQueue.global())
                .tryMap { data, response -> Data in
                    guard let httpResponse = response as? HTTPURLResponse else {
                        throw URLError(.badServerResponse)
                    }
                    
                    if httpResponse.statusCode != 200 {
                        let errorBody = String(data: data, encoding: .utf8) ?? "Unknown error"
                        print("HTTP Error \(httpResponse.statusCode): \(errorBody)")
                        throw NSError(domain: "HTTPError", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: errorBody])
                    }
                    
                    return data
                }
                .map { $0 }
                .sink { completion in
                    switch completion {
                    case .failure(let error):
                        print("Stream error: \(error)")
                        continuation.finish(throwing: error)
                    case .finished:
                        continuation.finish()
                    }
                } receiveValue: { data in
                    // Process streaming data incrementally
                    if let string = String(data: data, encoding: .utf8) {
                        for line in string.components(separatedBy: "\n") {
                            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
                            
                            if trimmed.hasPrefix("data: ") {
                                let jsonData = String(trimmed.dropFirst(6))
                                
                                if jsonData == "[DONE]" {
                                    continuation.finish()
                                    return
                                }
                                
                                // Parse the JSON chunk
                                if let jsonData = jsonData.data(using: .utf8),
                                   let json = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
                                   let choices = json["choices"] as? [[String: Any]],
                                   let firstChoice = choices.first,
                                   let delta = firstChoice["delta"] as? [String: Any],
                                   let content = delta["content"] as? String,
                                   !content.isEmpty {
                                    continuation.yield(content)
                                }
                            }
                        }
                    }
                }
            
            // Store task reference for cancellation
            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }
    
    // MARK: - Types
    
    struct ChatMessage {
        enum Role: String {
            case system, user, assistant
        }
        
        let role: Role
        let content: String
    }
}
