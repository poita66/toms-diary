//
//  Conversation.swift
//  Tom's Diary - iOS
//
//  Models for conversation history and individual turns.
//

import Foundation
import UIKit

// MARK: - Conversation Turn

struct ConversationTurn: Identifiable, Codable {
    let id: UUID
    let userText: String      // OCR-recognized text from user's handwriting
    let assistantText: String  // AI response
    let timestamp: Date
    
    init(userText: String, assistantText: String, timestamp: Date = Date()) {
        self.id = UUID()
        self.userText = userText
        self.assistantText = assistantText
        self.timestamp = timestamp
    }
}

// MARK: - Conversation

struct Conversation: Identifiable, Codable {
    let id: UUID
    var turns: [ConversationTurn]
    var createdAt: Date
    var updatedAt: Date
    var renderedImage: Data?  // Optional cached rendered image
    
    init(id: UUID = UUID(), turns: [ConversationTurn] = [], createdAt: Date = Date()) {
        self.id = id
        self.turns = turns
        self.createdAt = createdAt
        self.updatedAt = createdAt
    }
    
    mutating func addTurn(_ turn: ConversationTurn) {
        turns.append(turn)
        updatedAt = Date()
    }
    
    var lastTurn: ConversationTurn? {
        turns.last
    }
    
    var turnCount: Int {
        turns.count
    }
}

// MARK: - Conversation Manager

@MainActor
class ConversationManager: ObservableObject {
    
    @Published private(set) var conversations: [Conversation] = []
    @Published private(set) var currentConversation: Conversation?
    
    private let saveKey = "saved_conversations"
    private let userDefaults = UserDefaults.standard
    
    init() {
        loadConversations()
    }
    
    // MARK: - Public Methods
    
    func createNewConversation() -> Conversation {
        let conversation = Conversation()
        conversations.insert(conversation, at: 0)
        currentConversation = conversation
        saveConversations()
        return conversation
    }
    
    func addTurn(to conversation: Conversation, turn: ConversationTurn) {
        var updated = conversation
        updated.addTurn(turn)
        
        if let index = conversations.firstIndex(where: { $0.id == conversation.id }) {
            conversations[index] = updated
            currentConversation = updated
            saveConversations()
        }
    }
    
    func selectConversation(_ conversation: Conversation) {
        currentConversation = conversation
    }
    
    func deleteConversation(_ conversation: Conversation) {
        conversations.removeAll { $0.id == conversation.id }
        if currentConversation?.id == conversation.id {
            currentConversation = conversations.first
        }
        saveConversations()
    }
    
    // MARK: - Persistence
    
    private func saveConversations() {
        guard let encoded = try? JSONEncoder().encode(conversations) else { return }
        userDefaults.set(encoded, forKey: saveKey)
    }
    
    private func loadConversations() {
        guard let data = userDefaults.data(forKey: saveKey),
              let decoded = try? JSONDecoder().decode([Conversation].self, from: data) else {
            return
        }
        conversations = decoded
        currentConversation = conversations.first
    }
}
