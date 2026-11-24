import SwiftUI

/// NetworkHistogramView for iOS
/// Renders network throughput activity using Apple Music equalizer-style bars
/// with specular gradients and rounded caps.
public struct NetworkHistogramView: View {

    public var values: [Double]
    public var barCount: Int = 16
    public var accentColor: Color

    public init(values: [Double], barCount: Int = 16, accentColor: Color = .white) {
        self.values = values
        self.barCount = barCount
        self.accentColor = accentColor
    }

    public var body: some View {
        HStack(alignment: .bottom, spacing: 4) {
            ForEach(0..<barCount, id: \.self) { index in
                let val = sampleValue(at: index)
                ZStack(alignment: .bottom) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color.white.opacity(0.06))
                        .frame(height: 36)

                    RoundedRectangle(cornerRadius: 2)
                        .fill(
                            LinearGradient(
                                colors: [
                                    accentColor,
                                    accentColor.opacity(0.35)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .frame(height: max(4, CGFloat(val) * 36.0))
                }
                .animation(.spring(response: 0.35, dampingFraction: 0.7), value: val)
            }
        }
        .frame(height: 40)
    }

    private func sampleValue(at index: Int) -> Double {
        if index < values.count {
            return min(1.0, max(0.08, values[index]))
        }
        return 0.08
    }
}
