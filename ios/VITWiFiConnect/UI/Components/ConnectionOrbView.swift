import SwiftUI

/// ConnectionOrbView for iOS
/// Implements physical liquid glass lens optics inspired by Apple Music and iOS 18 Design Systems:
/// 1. Multi-tier refractive outer halo rim with specular highlight.
/// 2. Translucent glass substrate orb with directional internal light dispersion.
/// 3. Curved specular crescent highlight simulating optical refraction.
/// 4. Dynamic orbital photon for authenticating states.
/// 5. High-contrast precision glyphs (Checkmark, Exclamation, Wi-Fi waves).
public struct ConnectionOrbView: View {

    public var status: ConnectionStatus
    public var accentColor: Color
    public var secondaryAccentColor: Color

    @State private var pulseFraction: CGFloat = 0.0
    @State private var rotationAngle: Double = 0.0

    public init(status: ConnectionStatus, accentColor: Color, secondaryAccentColor: Color) {
        self.status = status
        self.accentColor = accentColor
        self.secondaryAccentColor = secondaryAccentColor
    }

    public var body: some View {
        // Explicit 120 FPS frame pacing for ProMotion displays (1/120s = 8.33ms)
        TimelineView(.animation(minimumInterval: 1.0 / 120.0)) { timeline in
            Canvas { context, size in
                let cx = size.width / 2.0
                let cy = size.height / 2.0
                let maxRadius = min(cx, cy) - 8.0
                let outerRadius = maxRadius * 0.94
                let orbRadius = outerRadius * 0.74

                // Determine active colors based on state
                let (primaryColor, stateColor) = resolveColors()

                // 1. Outer Liquid Glass Rim
                let rimRect = CGRect(x: cx - outerRadius, y: cy - outerRadius, width: outerRadius * 2, height: outerRadius * 2)
                let rimGradient = Gradient(colors: [
                    primaryColor.opacity(0.40),
                    Color.white.opacity(0.18),
                    Color.white.opacity(0.04)
                ])
                context.stroke(
                    Path(ellipseIn: rimRect),
                    with: .linearGradient(rimGradient, startPoint: CGPoint(x: cx, y: cy - outerRadius), endPoint: CGPoint(x: cx, y: cy + outerRadius)),
                    lineWidth: 1.5
                )

                // 2. Dynamic Liquid Aura / Ripple during connecting
                if isAnimatingState {
                    let auraRadius = outerRadius * (0.88 + 0.22 * pulseFraction)
                    let auraAlpha = Double(1.0 - pulseFraction) * 0.45
                    let auraRect = CGRect(x: cx - auraRadius, y: cy - auraRadius, width: auraRadius * 2, height: auraRadius * 2)
                    context.stroke(
                        Path(ellipseIn: auraRect),
                        with: .color(stateColor.opacity(auraAlpha)),
                        lineWidth: 2.5
                    )
                }

                // 3. Central Liquid Glass Orb
                let orbRect = CGRect(x: cx - orbRadius, y: cy - orbRadius, width: orbRadius * 2, height: orbRadius * 2)
                let orbGradient = Gradient(colors: [
                    primaryColor.opacity(0.25),
                    primaryColor.opacity(0.08)
                ])
                context.fill(
                    Path(ellipseIn: orbRect),
                    with: .radialGradient(
                        orbGradient,
                        center: CGPoint(x: cx, y: cy - orbRadius * 0.35),
                        startRadius: 0,
                        endRadius: orbRadius * 1.15
                    )
                )

                // 4. Specular Caustic Crescent Highlight
                let specRect = CGRect(
                    x: cx - orbRadius * 0.72,
                    y: cy - orbRadius * 0.95,
                    width: orbRadius * 1.44,
                    height: orbRadius * 0.9
                )
                let specGradient = Gradient(colors: [
                    Color.white.opacity(0.55),
                    Color.white.opacity(0.0)
                ])
                context.fill(
                    Path(ellipseIn: specRect),
                    with: .linearGradient(
                        specGradient,
                        startPoint: CGPoint(x: cx, y: cy - orbRadius),
                        endPoint: CGPoint(x: cx, y: cy - orbRadius * 0.1)
                    )
                )

                // 5. Orb Specular Border Bevel
                context.stroke(
                    Path(ellipseIn: orbRect),
                    with: .color(primaryColor.opacity(0.85)),
                    lineWidth: 1.8
                )

                // 6. Orbital Photon for Authenticating State
                if case .authenticating = status {
                    let rad = rotationAngle * .pi / 180.0
                    let px = cx + (outerRadius * 0.88) * cos(rad)
                    let py = cy + (outerRadius * 0.88) * sin(rad)
                    let photonRect = CGRect(x: px - 4, y: py - 4, width: 8, height: 8)
                    context.fill(Path(ellipseIn: photonRect), with: .color(primaryColor))
                }

                // 7. Precision Center Glyphs
                drawGlyphs(context: context, cx: cx, cy: cy, orbRadius: orbRadius, color: primaryColor)
            }
        }
        .frame(width: 140, height: 140)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.8).repeatForever(autoreverses: false)) {
                pulseFraction = 1.0
            }
            withAnimation(.linear(duration: 2.4).repeatForever(autoreverses: false)) {
                rotationAngle = 360.0
            }
        }
    }

    private var isAnimatingState: Bool {
        switch status {
        case .connecting, .authenticating: return true
        default: return false
        }
    }

    private func resolveColors() -> (primary: Color, state: Color) {
        switch status {
        case .connected:
            return (accentColor, accentColor)
        case .connecting:
            return (Color.orange, Color.orange)
        case .authenticating:
            return (accentColor, accentColor)
        case .error, .vpnBlocked:
            return (Color.red, Color.red)
        case .disconnected:
            return (accentColor, accentColor)
        }
    }

    private func drawGlyphs(context: GraphicsContext, cx: CGFloat, cy: CGFloat, orbRadius: CGFloat, color: Color) {
        switch status {
        case .connected:
            // Precision Apple-style checkmark
            var check = Path()
            let sz = orbRadius * 0.44
            check.move(to: CGPoint(x: cx - sz * 0.75, y: cy + sz * 0.05))
            check.addLine(to: CGPoint(x: cx - sz * 0.2, y: cy + sz * 0.6))
            check.addLine(to: CGPoint(x: cx + sz * 0.8, y: cy - sz * 0.48))
            context.stroke(check, with: .color(color), style: StrokeStyle(lineWidth: 3.0, lineCap: .round, lineJoin: .round))

        case .error, .vpnBlocked:
            // Minimalist Exclamation
            var line = Path()
            line.move(to: CGPoint(x: cx, y: cy - orbRadius * 0.42))
            line.addLine(to: CGPoint(x: cx, y: cy + orbRadius * 0.08))
            context.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: 3.0, lineCap: .round))

            let dotRect = CGRect(x: cx - 2.5, y: cy + orbRadius * 0.32, width: 5.0, height: 5.0)
            context.fill(Path(ellipseIn: dotRect), with: .color(color))

        case .connecting, .authenticating:
            // Rotating refractive arc
            let arcRadius = orbRadius * 0.46
            var arc = Path()
            arc.addArc(
                center: CGPoint(x: cx, y: cy),
                radius: arcRadius,
                startAngle: Angle(degrees: rotationAngle),
                endAngle: Angle(degrees: rotationAngle + 260),
                clockwise: false
            )
            context.stroke(arc, with: .color(color), style: StrokeStyle(lineWidth: 2.8, lineCap: .round))

        case .disconnected:
            // Wi-Fi concentric wave arcs
            let radii = [orbRadius * 0.52, orbRadius * 0.34, orbRadius * 0.16]
            for r in radii {
                var wave = Path()
                wave.addArc(
                    center: CGPoint(x: cx, y: cy),
                    radius: r,
                    startAngle: Angle(degrees: 225),
                    endAngle: Angle(degrees: 315),
                    clockwise: false
                )
                context.stroke(wave, with: .color(color.opacity(0.8)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
            }
        }
    }
}
