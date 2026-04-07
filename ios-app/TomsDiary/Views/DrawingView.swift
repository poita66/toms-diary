//
//  DrawingView.swift
//  Tom's Diary - iOS
//
//  Custom UIView for handwriting input with Apple Pencil support.
//

import UIKit
import Combine

final class DrawingView: UIView {
    
    // MARK: - Properties
    
    private var strokes: [[CGPoint]] = []
    private var currentStroke: [CGPoint] = []
    private var strokeColor: UIColor = .black
    private var strokeWidth: CGFloat = 2.5
    
    // Word images and positions
    private var wordImageViews: [UIImageView] = []
    
    // Word position tracking
    private var currentWordPosition: CGPoint = .zero
    private var wordInitialized: Bool = false
    private let wordSpacing: CGFloat = 10
    private let lineHeight: CGFloat = 150  // Match Android line spacing
    private let firstLineY: CGFloat = 180
    private let leftPadding: CGFloat = 40
    private let rightPadding: CGFloat = 40
    
    // Processing state
    private var isProcessing: Bool = false
    
    // Gesture recognition
    private var isDrawing: Bool = false
    
    // MARK: - Initialization
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupView()
    }
    
    private func setupView() {
        backgroundColor = .white
        isUserInteractionEnabled = true
        
        // Initialize word position
        resetWordPosition()
    }
    
    // MARK: - Drawing
    
    override func draw(_ rect: CGRect) {
        super.draw(rect)
        
        // Draw guide lines
        drawGuideLines(in: rect)
        
        // Draw all strokes
        for stroke in strokes {
            drawStroke(stroke)
        }
        
        // Draw current stroke
        if isDrawing, !currentStroke.isEmpty {
            drawStroke(currentStroke)
        }
    }
    
    private func drawGuideLines(in rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        
        let guideColor = UIColor.systemGray5
        guideColor.setFill()
        context.fill(rect)
        
        var y = firstLineY
        let maxY = bounds.height
        let lineLeft = leftPadding
        let lineRight = bounds.width - rightPadding
        
        while y < maxY {
            context.setStrokeColor(UIColor.lightGray.cgColor)
            context.setLineWidth(1.0)
            context.move(to: CGPoint(x: lineLeft, y: y))
            context.addLine(to: CGPoint(x: lineRight, y: y))
            context.strokePath()
            y += lineHeight
        }
    }
    
    private func drawStroke(_ stroke: [CGPoint]) {
        guard stroke.count > 1, let context = UIGraphicsGetCurrentContext() else { return }
        
        context.setStrokeColor(strokeColor.cgColor)
        context.setLineWidth(strokeWidth)
        context.setLineCapStyle(.round)
        context.setLineJoinStyle(.round)
        
        context.move(to: stroke[0])
        
        // Use quadratic curves for smoother lines (like Android)
        for i in 1..<stroke.count {
            let prevPoint = stroke[i - 1]
            let currPoint = stroke[i]
            
            if i == 1 {
                context.addLine(to: currPoint)
            } else {
                let midPoint = CGPoint(
                    x: (prevPoint.x + currPoint.x) / 2,
                    y: (prevPoint.y + currPoint.y) / 2
                )
                context.addQuadCurve(to: currPoint, control: midPoint)
            }
        }
        
        context.strokePath()
    }
    
    // MARK: - Touch Handling
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard !isProcessing, let touch = touches.first else { return }
        
        // Check if this is a finger touch (clear canvas)
        if touch.isFinger {
            if !wordImageViews.isEmpty || !strokes.isEmpty {
                clear()
            }
            return
        }
        
        // Stylus touch - start drawing
        isDrawing = true
        currentStroke = [touch.location(in: self)]
        
        // Check if we should clear canvas on new stroke
        if !wordImageViews.isEmpty || !strokes.isEmpty {
            clear()
            currentStroke = [touch.location(in: self)]
        }
        
        setNeedsDisplay()
    }
    
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard isDrawing, let touch = touches.first else { return }
        
        let newPoint = touch.location(in: self)
        currentStroke.append(newPoint)
        
        // Partial redraw for performance
        setNeedsDisplay()
    }
    
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard isDrawing else { return }
        
        isDrawing = false
        
        if !currentStroke.isEmpty {
            strokes.append(currentStroke)
        }
        currentStroke = []
        
        setNeedsDisplay()
    }
    
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        touchesEnded(touches, with: event)
    }
    
    // MARK: - Word Display
    
    func addWord(_ image: UIImage, at position: CGPoint) {
        let imageView = UIImageView(image: image)
        imageView.frame = CGRect(origin: position, size: image.size)
        imageView.contentMode = .scaleAspectFit
        imageView.isUserInteractionEnabled = false
        addSubview(imageView)
        wordImageViews.append(imageView)
        
        // Advance position
        advanceWordPosition(by: image.size.width)
    }
    
    func advanceWordPosition(by width: CGFloat) {
        currentWordPosition.x += width + wordSpacing
        
        // Check if we need to wrap to next line
        let wrapWidth = bounds.width - leftPadding - rightPadding
        if currentWordPosition.x + width > wrapWidth {
            currentWordPosition.x = leftPadding
            currentWordPosition.y += lineHeight
        }
    }
    
    func getNextWordPosition() -> CGPoint {
        if !wordInitialized {
            currentWordPosition = CGPoint(x: leftPadding, y: firstLineY)
            wordInitialized = true
        }
        return currentWordPosition
    }
    
    func resetWordPosition() {
        currentWordPosition = CGPoint(x: leftPadding, y: firstLineY)
        wordInitialized = false
        
        // Remove word images
        for imageView in wordImageViews {
            imageView.removeFromSuperview()
        }
        wordImageViews = []
    }
    
    // MARK: - Canvas Operations
    
    func clear() {
        strokes = []
        currentStroke = []
        wordImageViews.forEach { $0.removeFromSuperview() }
        wordImageViews = []
        wordInitialized = false
        isDrawing = false
        setNeedsDisplay()
    }
    
    func setProcessing(_ processing: Bool) {
        isProcessing = processing
    }
    
    // MARK: - Image Capture
    
    func getBitmap() -> UIImage? {
        // Capture the current view state
        let renderBounds = bounds
        
        UIGraphicsBeginImageContextWithOptions(renderBounds.size, false, 0)
        defer { UIGraphicsEndImageContext() }
        
        guard let context = UIGraphicsGetCurrentContext() else { return nil }
        
        // Draw white background
        UIColor.white.setFill()
        context.fill(renderBounds)
        
        // Draw the view hierarchy
        drawHierarchy(in: renderBounds, afterScreenUpdates: true)
        
        return UIGraphicsGetImageFromCurrentImageContext()
    }
    
    func getHandwritingBounds() -> CGRect? {
        guard let image = getBitmap() else { return nil }
        return ImageProcessor.getHandwritingBounds(in: image)
    }
}

// MARK: - UITouch Extension

extension UITouch {
    var isFinger: Bool {
        #if targetEnvironment(simulator)
        return true  // In simulator, treat all touches as fingers
        #else
        return toolType == .finger
        #endif
    }
}
