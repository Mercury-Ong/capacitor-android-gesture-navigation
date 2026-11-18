import Capacitor
import Foundation
import UIKit

/// iOS has no navigation buttons, so only the status bar is styled here:
/// light or dark icons from the app's own pixels under it.
@objc(GestureNavigationPlugin)
public class GestureNavigationPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "GestureNavigationPlugin"
    public let jsName = "GestureNavigation"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getMode", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setStyle", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startAutoStyle", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopAutoStyle", returnType: CAPPluginReturnPromise),
    ]

    private struct RGB: Equatable {
        let r: UInt8, g: UInt8, b: UInt8
        var hex: String { String(format: "#%02X%02X%02X", r, g, b) }
    }

    private static let sampleWidth = 48

    private var timer: Timer?
    private var interval: TimeInterval = 0.25
    private var autoStyle = false
    private var topLight: Bool?
    private var bottomLight: Bool?
    private var bottomColor: RGB?
    private var observers: [NSObjectProtocol] = []

    @objc func getMode(_ call: CAPPluginCall) {
        call.resolve(["mode": "gesture"])
    }

    // No navigation buttons on iOS.
    @objc func setStyle(_ call: CAPPluginCall) {
        call.resolve()
    }

    @objc func startAutoStyle(_ call: CAPPluginCall) {
        let ms = max(call.getDouble("interval") ?? 250, 100)
        DispatchQueue.main.async {
            self.interval = ms / 1000
            self.autoStyle = true
            self.observeLifecycle()
            self.startTimer()
            self.sample()
            call.resolve()
        }
    }

    @objc func stopAutoStyle(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.autoStyle = false
            self.stopTimer()
            self.observers.forEach(NotificationCenter.default.removeObserver)
            self.observers.removeAll()
            call.resolve()
        }
    }

    private func startTimer() {
        stopTimer()
        let timer = Timer(timeInterval: interval, repeats: true) { [weak self] _ in self?.sample() }
        RunLoop.main.add(timer, forMode: .common) // keeps sampling while the page scrolls
        self.timer = timer
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }

    // Sampling pauses in the background.
    private func observeLifecycle() {
        guard observers.isEmpty else { return }
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: UIApplication.willResignActiveNotification, object: nil, queue: .main) { [weak self] _ in
            self?.stopTimer()
        })
        observers.append(center.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self = self, self.autoStyle else { return }
            self.startTimer()
            self.sample()
        })
    }

    private func sample() {
        guard let window = bridge?.viewController?.view.window, window.bounds.width > 0 else { return }
        let bounds = window.bounds
        let width = Self.sampleWidth
        let scale = CGFloat(width) / bounds.width
        let height = max(1, Int((bounds.height * scale).rounded()))
        var pixels = [UInt8](repeating: 0, count: width * height * 4)

        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            // UIKit draws top-down; memory row 0 is then the top of the window.
            context.translateBy(x: 0, y: CGFloat(height))
            context.scaleBy(x: 1, y: -1)
            UIGraphicsPushContext(context)
            defer { UIGraphicsPopContext() }
            return window.drawHierarchy(
                in: CGRect(x: 0, y: 0, width: CGFloat(width), height: bounds.height * scale),
                afterScreenUpdates: false
            )
        }
        guard drawn else { return }

        // Rows under the status bar, and an 8pt strip just above the home indicator.
        let insets = window.safeAreaInsets
        let topRows = min(height, max(1, Int((insets.top * scale).rounded())))
        let band = max(1, Int((8 * scale).rounded()))
        let bottomEnd = min(height, max(1, Int(((bounds.height - insets.bottom) * scale).rounded())))
        let top = dominantColor(pixels, width: width, from: 0, to: topRows)
        let bottom = dominantColor(pixels, width: width, from: max(0, bottomEnd - band), to: bottomEnd)
        apply(top: top, bottom: bottom)
    }

    private func apply(top: RGB, bottom: RGB) {
        let isTopLight = Self.isLight(top, was: topLight)
        let isBottomLight = Self.isLight(bottom, was: bottomLight)
        if isTopLight != topLight {
            bridge?.statusBarStyle = isTopLight ? .darkContent : .lightContent
        }
        let changed = isTopLight != topLight || isBottomLight != bottomLight || bottom != bottomColor
        topLight = isTopLight
        bottomLight = isBottomLight
        bottomColor = bottom
        if changed {
            notifyListeners("toneChange", data: [
                "top": isTopLight ? "light" : "dark",
                "bottom": isBottomLight ? "light" : "dark",
                "bottomColor": bottom.hex,
            ])
        }
    }

    // Light enough for dark icons. Hysteresis keeps scrolling content from flickering them.
    private static func isLight(_ color: RGB, was: Bool?) -> Bool {
        let luminance = luminance(color)
        guard let was = was else { return luminance > 0.4 }
        return was ? luminance > 0.3 : luminance > 0.5
    }

    private static func luminance(_ color: RGB) -> Double {
        func channel(_ value: UInt8) -> Double {
            let c = Double(value) / 255
            return c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.r) + 0.7152 * channel(color.g) + 0.0722 * channel(color.b)
    }

    // Most common colour in the rows (4 bits per channel): the background, not the text on it.
    private func dominantColor(_ pixels: [UInt8], width: Int, from: Int, to: Int) -> RGB {
        var counts: [Int: Int] = [:]
        var samples: [Int: RGB] = [:]
        var best = -1
        var bestCount = -1
        for y in from..<max(from + 1, to) {
            for x in 0..<width {
                let i = (y * width + x) * 4
                let color = RGB(r: pixels[i], g: pixels[i + 1], b: pixels[i + 2])
                let key = Int(color.r >> 4) << 8 | Int(color.g >> 4) << 4 | Int(color.b >> 4)
                let count = (counts[key] ?? 0) + 1
                counts[key] = count
                if samples[key] == nil { samples[key] = color }
                if count > bestCount {
                    bestCount = count
                    best = key
                }
            }
        }
        return samples[best] ?? RGB(r: 255, g: 255, b: 255)
    }
}
