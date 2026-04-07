//
//  LLMConfig.swift
//  Tom's Diary - iOS
//
//  Configuration for the LLM (OpenAI-compatible API).
//  The app can use any OpenAI-compatible endpoint including:
//  - Local vLLM instances
//  - Ollama
//  - Any other OpenAI-compatible API
//

import Foundation

struct LLMConfig {
    
    // MARK: - Properties
    
    let baseUrl: String
    let apiKey: String
    let model: String
    
    // MARK: - Defaults
    
    static let `default` = LLMConfig(
        baseUrl: "http://localhost:8001/v1",
        apiKey: "placeholder",
        model: "default"
    )
    
    // MARK: - Initialization
    
    init(
        baseUrl: String = "http://localhost:8001/v1",
        apiKey: String = "placeholder",
        model: String = "default"
    ) {
        self.baseUrl = baseUrl
        self.apiKey = apiKey
        self.model = model
    }
    
    // MARK: - Persistence
    
    private enum Keys {
        static let baseUrl = "llm_base_url"
        static let apiKey = "llm_api_key"
        static let model = "llm_model"
    }
    
    static func load(from userDefaults: UserDefaults = .standard) -> LLMConfig {
        LLMConfig(
            baseUrl: userDefaults.string(forKey: Keys.baseUrl) ?? `default`.baseUrl,
            apiKey: userDefaults.string(forKey: Keys.apiKey) ?? `default`.apiKey,
            model: userDefaults.string(forKey: Keys.model) ?? `default`.model
        )
    }
    
    func save(to userDefaults: UserDefaults = .standard) {
        userDefaults.set(baseUrl, forKey: Keys.baseUrl)
        userDefaults.set(apiKey, forKey: Keys.apiKey)
        userDefaults.set(model, forKey: Keys.model)
    }
    
    // MARK: - Validation
    
    func validate() -> Bool {
        // Validate URL
        guard let url = URL(string: baseUrl),
              url.scheme == "http" || url.scheme == "https" else {
            return false
        }
        
        // URL and model should not be empty
        return !baseUrl.isEmpty && !model.isEmpty
    }
}

// MARK: - Codable

extension LLMConfig: Codable {
    enum CodingKeys: String, CodingKey {
        case baseUrl = "base_url"
        case apiKey = "api_key"
        case model
    }
}
