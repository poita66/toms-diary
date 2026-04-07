//
//  SettingsView.swift
//  Tom's Diary - iOS
//
//  Settings screen for LLM configuration and persona selection.
//

import SwiftUI

struct SettingsView: View {
    
    // MARK: - Properties
    
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedPersona: Persona
    @StateObject private var viewModel = SettingsViewModel()
    
    // MARK: - Body
    
    var body: some View {
        NavigationView {
            Form {
                // Persona Section
                Section("Persona") {
                    Picker("Select Persona", selection: $selectedPersona) {
                        ForEach(Persona.allCases) { persona in
                            Text(persona.displayName).tag(persona)
                        }
                    }
                    .pickerStyle(.radioGroup)
                }
                
                // LLM Settings Section
                Section("LLM Settings") {
                    HStack {
                        Text("Base URL")
                            .frame(width: 100, alignment: .leading)
                        TextField("http://localhost:8001/v1", text: $viewModel.baseUrl)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                            .textContentType(.URL)
                            .textFieldStyle(.roundedBorder)
                    }
                    
                    HStack {
                        Text("API Key")
                            .frame(width: 100, alignment: .leading)
                        SecureField("placeholder", text: $viewModel.apiKey)
                            .textFieldStyle(.roundedBorder)
                    }
                    
                    HStack {
                        Text("Model")
                            .frame(width: 100, alignment: .leading)
                        TextField("default", text: $viewModel.model)
                            .textFieldStyle(.roundedBorder)
                    }
                }
                
                // Info Section
                Section("Info") {
                    HStack {
                        Text("Status")
                        Spacer()
                        Text(viewModel.connectionStatus)
                            .foregroundColor(viewModel.connectionStatusColor)
                            .fontWeight(.medium)
                    }
                    
                    HStack {
                        Text("Tip")
                        Spacer()
                        Text("Use your computer's IP address (not localhost) when testing on a real device")
                            .foregroundColor(.secondary)
                            .font(.caption)
                            .multilineTextAlignment(.trailing)
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        saveSettings()
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
            .onAppear {
                viewModel.loadSettings()
            }
        }
    }
    
    // MARK: - Actions
    
    private func saveSettings() {
        // Save persona
        UserDefaults.standard.set(selectedPersona.rawValue, forKey: "selected_persona")
        
        // Save LLM config
        let config = LLMConfig(baseUrl: viewModel.baseUrl, apiKey: viewModel.apiKey, model: viewModel.model)
        config.save()
        
        // Update connection status
        if config.validate() {
            viewModel.connectionStatus = "Ready"
            viewModel.connectionStatusColor = .green
        } else {
            viewModel.connectionStatus = "Invalid Config"
            viewModel.connectionStatusColor = .red
        }
    }
}

// MARK: - Settings ViewModel

final class SettingsViewModel: ObservableObject {
    
    @Published var baseUrl: String = ""
    @Published var apiKey: String = ""
    @Published var model: String = ""
    @Published var connectionStatus: String = "Not Connected"
    @Published var connectionStatusColor: Color = .gray
    
    init() {
        loadSettings()
    }
    
    func loadSettings() {
        let config = LLMConfig.load()
        baseUrl = config.baseUrl
        apiKey = config.apiKey
        model = config.model
        
        // Update status
        if config.validate() {
            connectionStatus = "Ready"
            connectionStatusColor = .green
        } else {
            connectionStatus = "Invalid Config"
            connectionStatusColor = .red
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationView {
        SettingsView(selectedPersona: .constant(.tom))
    }
}
