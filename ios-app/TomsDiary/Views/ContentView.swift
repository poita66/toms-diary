//
//  ContentView.swift
//  Tom's Diary - iOS
//
//  Main SwiftUI view for the app.
//

import SwiftUI

struct ContentView: View {
    
    // MARK: - Properties
    
    @StateObject private var viewModel: MainViewModel
    @State private var showingSettings = false
    @State private var drawingView: DrawingView = DrawingView()
    
    // MARK: - Initialization
    
    init(viewModel: MainViewModel = MainViewModel()) {
        self._viewModel = StateObject(wrappedValue: viewModel)
    }
    
    // MARK: - Body
    
    var body: some View {
        VStack(spacing: 0) {
            // Drawing canvas
            DrawingViewWrapper(drawingView: $drawingView)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.white)
            
            // Status bar
            statusBar
            
            // Action buttons
            actionButtons
        }
        .navigationTitle("Tom's Diary")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showingSettings = true }) {
                    Image(systemName: "gearshape.fill")
                }
            }
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView(selectedPersona: $viewModel.currentPersona)
        }
        .onAppear {
            setupViewModel()
        }
    }
    
    // MARK: - Subviews
    
    @ViewBuilder
    private var statusBar: some View {
        HStack {
            Text(viewModel.statusText)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(viewModel.statusColor)
            
            Spacer()
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(Color.black.opacity(0.85))
    }
    
    @ViewBuilder
    private var actionButtons: some View {
        HStack(spacing: 16) {
            Button("New") {
                viewModel.startNewConversation()
            }
            .buttonStyle(.bordered)
            
            Button("Clear") {
                viewModel.clearCanvas()
            }
            .buttonStyle(.bordered)
            
            Spacer()
            
            Button("Send") {
                viewModel.sendCanvasImage()
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isProcessing)
        }
        .padding()
        .background(Color.black.opacity(0.95))
    }
    
    // MARK: - Setup
    
    private func setupViewModel() {
        viewModel.drawingView = drawingView
        viewModel.setup()
    }
}

// MARK: - DrawingView Wrapper

struct DrawingViewWrapper: UIViewRepresentable {
    
    @Binding var drawingView: DrawingView
    
    func makeUIView(context: Context) -> DrawingView {
        drawingView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        return drawingView
    }
    
    func updateUIView(_ uiView: DrawingView, context: Context) {
        // Update as needed
    }
}

// MARK: - Preview

#Preview {
    NavigationView {
        ContentView(viewModel: MainViewModel())
    }
}
