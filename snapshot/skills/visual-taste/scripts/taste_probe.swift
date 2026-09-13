import Vision
import AppKit
import Foundation

// taste_probe.swift — one page in, one JSON line out.
// Measures: canvas size, OCR lines (text/conf/bbox), per-band palettes, luminance.
// Vision bbox origin = bottom-left, normalized 0-1. We emit y as FROM-TOP for sanity.

let path = CommandLine.arguments[1]
guard let img = NSImage(contentsOfFile: path),
      let cg = img.cgImage(forProposedRect: nil, context: nil, hints: nil),
      let tiff = img.tiffRepresentation,
      let bmp = NSBitmapImageRep(data: tiff) else {
    print("{\"error\":\"cannot load\"}"); exit(1)
}

let W = cg.width, H = cg.height

// --- OCR ---
struct Line { let text: String; let conf: Float; let x: Double; let y: Double; let w: Double; let h: Double }
var lines: [Line] = []
let req = VNRecognizeTextRequest()
req.recognitionLevel = .accurate
req.usesLanguageCorrection = true
let handler = VNImageRequestHandler(cgImage: cg, options: [:])
try? handler.perform([req])
for obs in (req.results ?? []) {
    if let top = obs.topCandidates(1).first {
        let b = obs.boundingBox
        lines.append(Line(text: top.string, conf: top.confidence,
                          x: b.origin.x, y: 1.0 - b.origin.y - b.size.height,
                          w: b.size.width, h: b.size.height))
    }
}

// --- Palettes (per band, quantized to 32-step buckets) ---
var bandLum: [Double: Double] = [:]
func bandPalette(_ y0f: Double, _ y1f: Double) -> [(String, Double)] {
    let x0 = 0, x1 = bmp.pixelsWide
    let y0 = Int(y0f * Double(bmp.pixelsHigh)), y1 = Int(y1f * Double(bmp.pixelsHigh))
    var colors: [String: Int] = [:]
    var total = 0
    var lumSum = 0.0
    for y in stride(from: y0, to: y1, by: 4) {
        for x in stride(from: x0, to: x1, by: 4) {
            if let c = bmp.colorAt(x: x, y: y) {
                let r = c.redComponent, g = c.greenComponent, b = c.blueComponent
                let key = String(format: "#%02x%02x%02x", Int(r*255)/32*32, Int(g*255)/32*32, Int(b*255)/32*32)
                colors[key, default: 0] += 1
                lumSum += 0.299*r + 0.587*g + 0.114*b
                total += 1
            }
        }
    }
    bandLum[y0f] = lumSum / Double(max(total, 1))
    return colors.sorted { $0.value > $1.value }.prefix(5).map {
        ($0.key, 100.0 * Double($0.value) / Double(total))
    }
}

let topBand = bandPalette(0.0, 0.33)
let midBand = bandPalette(0.33, 0.66)
let botBand = bandPalette(0.66, 1.0)

// --- JSON emit ---
func esc(_ s: String) -> String {
    s.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\"", with: "\\\"")
}
var linesJson = "["
for (i, l) in lines.enumerated() {
    if i > 0 { linesJson += "," }
    linesJson += String(format: "{\"t\":\"%@\",\"conf\":%.2f,\"x\":%.3f,\"y\":%.3f,\"w\":%.3f,\"h\":%.4f}",
                        esc(l.text), l.conf, l.x, l.y, l.w, l.h)
}
linesJson += "]"
func palJson(_ p: [(String, Double)]) -> String {
    "[" + p.map { String(format: "{\"hex\":\"%@\",\"share\":%.1f}", $0.0, $0.1) }.joined(separator: ",") + "]"
}
print(String(format: "{\"page\":\"%@\",\"w\":%d,\"h\":%d,\"lines\":%@,\"bands\":{\"top\":%@,\"mid\":%@,\"bot\":%@},\"lum\":{\"top\":%.2f,\"mid\":%.2f,\"bot\":%.2f}}",
             (path as NSString).lastPathComponent, W, H, linesJson,
             palJson(topBand), palJson(midBand), palJson(botBand),
             bandLum[0.0] ?? 0, bandLum[0.33] ?? 0, bandLum[0.66] ?? 0))
