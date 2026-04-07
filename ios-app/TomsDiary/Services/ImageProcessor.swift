//
//  ImageProcessor.swift
//  Tom's Diary - iOS
//
//  Handles image capture, cropping, and processing.
//

import Foundation
import UIKit

final class ImageProcessor {
    
    // MARK: - Public Methods
    
    /// Captures a UIView as a UIImage
    static func captureView(_ view: UIView) -> UIImage? {
        view.layoutIfNeeded()
        
        let renderBounds = view.bounds
        
        UIGraphicsBeginImageContextWithOptions(renderBounds.size, false, 0)
        guard let context = UIGraphicsGetCurrentContext() else { return nil }
        
        // Draw white background
        UIColor.white.setFill()
        context.fill(renderBounds)
        
        view.drawHierarchy(in: renderBounds, afterScreenUpdates: true)
        
        let image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        return image
    }
    
    /// Crops image to specified bounds
    static func cropImage(_ image: UIImage, to bounds: CGRect) -> UIImage? {
        guard let cgImage = image.cgImage else { return nil }
        
        // Convert bounds to image coordinates
        let x = max(0, min(bounds.minX, CGFloat(cgImage.width)))
        let y = max(0, min(bounds.minY, CGFloat(cgImage.height)))
        let width = max(1, min(bounds.width, CGFloat(cgImage.width) - x))
        let height = max(1, min(bounds.height, CGFloat(cgImage.height) - y))
        
        let cropRect = CGRect(x: x, y: y, width: width, height: height)
        
        guard let croppedCGImage = cgImage.cropping(to: cropRect) else {
            return image
        }
        
        return UIImage(cgImage: croppedCGImage)
    }
    
    /// Crops image to handwriting bounds
    static func cropToHandwritingBounds(_ image: UIImage) -> UIImage? {
        guard let bounds = getHandwritingBounds(in: image) else {
            return image
        }
        
        // Add padding
        let paddedBounds = bounds.insetBy(dx: -20, dy: -20)
        return cropImage(image, to: paddedBounds)
    }
    
    /// Converts image to grayscale
    static func convertToGrayscale(_ image: UIImage) -> UIImage? {
        guard let cgImage = image.cgImage else { return nil }
        
        let width = cgImage.width
        let height = cgImage.height
        
        UIGraphicsBeginImageContextWithOptions(CGSize(width: width, height: height), false, 0)
        guard let context = UIGraphicsGetCurrentContext() else { return nil }
        
        let rect = CGRect(x: 0, y: 0, width: width, height: height)
        
        // Draw white background
        UIColor.white.setFill()
        context.fill(rect)
        
        // Set grayscale colorspace
        context.setFillColor(UIColor.black.cgColor)
        
        // Create a grayscale representation
        let colorSpace = CGColorSpaceCreateDeviceGray()
        context.setInterpolationQuality(.none)
        
        // Draw the image in grayscale
        // First, we need to convert the image to grayscale
        if let provider = cgImage.dataProvider,
           let data = provider.data,
           let buffer = CFDataGetBytePtr(data) {
            
            let bytesPerRow = cgImage.bytesPerRow
            let bitmapInfo = cgImage.bitmapInfo.rawValue
            
            // Create grayscale bitmap context
            let alphaInfo = CGBitmapInfo(rawValue: bitmapInfo) | .alphaNone
            let grayscaleContext = CGContext(
                data: nil,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width,
                space: colorSpace,
                bitmapInfo: alphaInfo.rawValue
            )
            
            if let grayscaleContext = grayscaleContext {
                // Convert each pixel to grayscale
                for y in 0..<height {
                    for x in 0..<width {
                        let offset = y * bytesPerRow + x * 4
                        let r = CGFloat(buffer[offset]) / 255.0
                        let g = CGFloat(buffer[offset + 1]) / 255.0
                        let b = CGFloat(buffer[offset + 2]) / 255.0
                        
                        // Use luminance formula for grayscale conversion
                        let gray = 0.299 * r + 0.587 * g + 0.114 * b
                        
                        grayscaleContext.setFillColor(CGColor(gray: gray, alpha: 1.0))
                        grayscaleContext.fill(CGRect(x: x, y: y, width: 1, height: 1))
                    }
                }
                
                // Draw the grayscale image onto the original context
                if let grayscaleImage = grayscaleContext.makeImage() {
                    context.draw(grayscaleImage, in: rect)
                }
            }
        }
        
        let grayscaleImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        return grayscaleImage ?? image
    }
    
    /// Converts UIImage to base64 string
    static func imageToBase64(_ image: UIImage, format: UIImage.RawRepresentation = .png, compression: CGFloat = 1.0) -> String? {
        guard let data = image.asData(format: format, compression: compression) else {
            return nil
        }
        return data.base64EncodedString()
    }
    
    /// Gets bounding box of handwriting in image
    static func getHandwritingBounds(in image: UIImage) -> CGRect? {
        guard let cgImage = image.cgImage else { return nil }
        
        let width = cgImage.width
        let height = cgImage.height
        let bytesPerRow = cgImage.bytesPerRow
        let data = cgImage.dataProvider?.data
        
        guard let data = data,
              let buffer = data.bytes.bindMemory(to: UInt8.self, capacity: data.count) else {
            return nil
        }
        
        var minX = width, maxX = 0, minY = height, maxY = 0
        var foundPixels = false
        
        for y in 0..<height {
            for x in 0..<width {
                let offset = y * bytesPerRow + x * 4
                let alpha = buffer[offset + 3]
                
                // Check for non-white, non-transparent pixels
                if alpha > 10 {
                    foundPixels = true
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                }
            }
        }
        
        guard foundPixels else { return nil }
        
        return CGRect(
            x: CGFloat(minX),
            y: CGFloat(minY),
            width: CGFloat(maxX - minX + 1),
            height: CGFloat(maxY - minY + 1)
        )
    }
}

// MARK: - UIImage Extension

extension UIImage {
    func asData(format: UIImage.RawRepresentation = .png, compression: CGFloat = 1.0) -> Data? {
        switch format {
        case .png:
            return pngData()
        case .jpeg:
            return jpegData(compressionQuality: compression)
        @unknown default:
            return nil
        }
    }
}
