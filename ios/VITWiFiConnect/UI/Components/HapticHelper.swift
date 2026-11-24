import UIKit

public enum HapticHelper {
    public static func click() {
        guard SettingsStore.shared.hapticFeedbackEnabled else { return }
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.prepare()
        generator.impactOccurred()
    }

    public static func medium() {
        guard SettingsStore.shared.hapticFeedbackEnabled else { return }
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
    }

    public static func success() {
        guard SettingsStore.shared.hapticFeedbackEnabled else { return }
        let generator = UINotificationFeedbackGenerator()
        generator.prepare()
        generator.notificationOccurred(.success)
    }

    public static func error() {
        guard SettingsStore.shared.hapticFeedbackEnabled else { return }
        let generator = UINotificationFeedbackGenerator()
        generator.prepare()
        generator.notificationOccurred(.error)
    }
}
