//
//  HandwritingRenderer.swift
//  Tom's Diary - iOS
//
//  Renders text as handwritten images using the Caveat font.
//  Supports word-by-word streaming for smooth display.
//

import Foundation
import UIKit

final class HandwritingRenderer {
    
    // MARK: - Properties
    
    private var caveatFont: UIFont?
    
    // MARK: - Initialization
    
    init() {
        loadFont()
    }
    
    private func loadFont() {
        // Try to load from bundle (registered in Info.plist)
        caveatFont = UIFont(name: "Caveat-Regular", size: 90)
        
        if caveatFont == nil {
            // Try to load from assets
            if let path = Bundle.main.path(forResource: "Caveat-Regular", ofType: "ttf"),
               let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
               let font = UIFont(data: data) {
                caveatFont = font
            }
        }
        
        if caveatFont == nil {
            // Last resort: use system script font
            caveatFont = .systemFont(ofSize: 90, weight: .regular)
            print("⚠️ Warning: Caveat font not found, using system font")
        }
    }
    
    // MARK: - Configuration
    
    struct RenderOptions {
        let fontSize: CGFloat
        let backgroundColor: UIColor
        let textColor: UIColor
        let padding: CGFloat
        let maxWidth: CGFloat
        let addVariation: Bool
        
        static let `default` = RenderOptions(
            fontSize: 90,
            backgroundColor: .white,
            textColor: .black,
            padding: 20,
            maxWidth: 900,
            addVariation: true
        )
    }
    
    // MARK: - Public Methods
    
    /// Calculates appropriate font size for given screen width
    func calculateFontSize(for screenWidth: CGFloat) -> CGFloat {
        max(48, min(120, screenWidth / 14))
    }
    
    /// Renders a single word as an image
    func renderWord(_ word: String, options: RenderOptions = .default) -> UIImage? {
        guard let font = caveatFont else { return nil }
        
        let attributes: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: options.textColor
        ]
        
        let wordSize = word.size(withAttributes: attributes)
        
        // Get font metrics
        let fontDesc = font.fontDescriptor
        let ctFont = CTFontCreateWithFontDescriptor(fontDesc, options.fontSize, nil)
        let descender = CTFontGetDescender(ctFont)
        let ascender = CTFontGetAscender(ctFont)
        let wordHeight = abs(descender - ascender)
        
        let leftPadding: CGFloat = 15
        let rightPadding: CGFloat = 30
        let topPadding: CGFloat = 10
        let bottomPadding: CGFloat = 25
        
        let canvasWidth = wordSize.width + leftPadding + rightPadding
        let canvasHeight = wordHeight + topPadding + bottomPadding
        
        let rect = CGRect(x: 0, y: 0, width: canvasWidth, height: canvasHeight)
        
        UIGraphicsBeginImageContextWithOptions(rect.size, false, 0)
        guard let context = UIGraphicsGetCurrentContext() else { return nil }
        
        // Draw background
        options.backgroundColor.setFill()
        context.fill(rect)
        
        // Calculate text position (baseline)
        let x = leftPadding
        let y = topPadding + wordHeight - descender
        
        // Add variation if enabled
        if options.addVariation {
            // Small rotation in radians (not degrees)
            let rotation = Double.random(in: -0.5...0.5) * 0.02  // ±0.01 radians (~±0.57 degrees)
            let xOffset = Double.random(in: -0.5...0.5) * 2  // ±1 point offset
            
            context.save()
            context.translateBy(x: x + CGFloat(xOffset), y: y)
            context.rotate(by: CGFloat(rotation))
            
            let attributedString = NSAttributedString(string: word, attributes: attributes)
            attributedString.draw(at: .zero)
            
            context.restore()
        } else {
            let attributedString = NSAttributedString(string: word, attributes: attributes)
            attributedString.draw(at: CGPoint(x: x, y: y))
        }
        
        let image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        return image
    }
    
    /// Renders words from text as a sequence of images
    func renderWordStream(_ text: String, options: RenderOptions = .default) -> AsyncStream<UIImage> {
        AsyncStream { continuation in
            let words = text.split(separator: " ").map(String.init)
            
            for word in words {
                if let image = renderWord(word, options: options) {
                    continuation.yield(image)
                }
            }
            
            continuation.finish()
        }
    }
}
