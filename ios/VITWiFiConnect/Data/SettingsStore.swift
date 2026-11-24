import Foundation
import SwiftUI

public struct ColorPalette: Identifiable, Hashable {
    public let id: String
    public let name: String
    public let subtitle: String
    public let primaryColor: Color
    public let secondaryColor: Color
    public let glowColor: Color
    public let hex: String

    public init(id: String, name: String, subtitle: String, hex: String, primaryColor: Color, secondaryColor: Color, glowColor: Color) {
        self.id = id
        self.name = name
        self.subtitle = subtitle
        self.hex = hex
        self.primaryColor = primaryColor
        self.secondaryColor = secondaryColor
        self.glowColor = glowColor
    }
}

public final class SettingsStore: ObservableObject {

    public static let shared = SettingsStore()

    public static let palettes: [ColorPalette] = [
        ColorPalette(
            id: "emerald",
            name: "Emerald Aurora",
            subtitle: "Campus Neo Emerald",
            hex: "#10B981",
            primaryColor: Color(red: 16/255, green: 185/255, blue: 129/255),
            secondaryColor: Color(red: 5/255, green: 150/255, blue: 105/255),
            glowColor: Color(red: 52/255, green: 211/255, blue: 153/255)
        ),
        ColorPalette(
            id: "ocean",
            name: "Midnight Ocean",
            subtitle: "Apple Deep Azure",
            hex: "#3B82F6",
            primaryColor: Color(red: 59/255, green: 130/255, blue: 246/255),
            secondaryColor: Color(red: 29/255, green: 78/255, blue: 216/255),
            glowColor: Color(red: 96/255, green: 165/255, blue: 250/255)
        ),
        ColorPalette(
            id: "sunset",
            name: "Sunset Flare",
            subtitle: "Apple Music Sunset Amber",
            hex: "#F97316",
            primaryColor: Color(red: 249/255, green: 115/255, blue: 22/255),
            secondaryColor: Color(red: 194/255, green: 65/255, blue: 12/255),
            glowColor: Color(red: 251/255, green: 146/255, blue: 60/255)
        ),
        ColorPalette(
            id: "violet",
            name: "Royal Amethyst",
            subtitle: "Royal Velvet Purple",
            hex: "#8B5CF6",
            primaryColor: Color(red: 139/255, green: 92/255, blue: 246/255),
            secondaryColor: Color(red: 109/255, green: 40/255, blue: 217/255),
            glowColor: Color(red: 167/255, green: 139/255, blue: 250/255)
        ),
        ColorPalette(
            id: "rose",
            name: "Rose Nebula",
            subtitle: "Neon Rose & Magenta",
            hex: "#F43F5E",
            primaryColor: Color(red: 244/255, green: 63/255, blue: 94/255),
            secondaryColor: Color(red: 190/255, green: 18/255, blue: 60/255),
            glowColor: Color(red: 251/255, green: 113/255, blue: 133/255)
        ),
        ColorPalette(
            id: "titanium",
            name: "Cyber Titanium",
            subtitle: "Luminescent Platinum Slate",
            hex: "#E2E8F0",
            primaryColor: Color(red: 226/255, green: 232/255, blue: 240/255),
            secondaryColor: Color(red: 148/255, green: 163/255, blue: 184/255),
            glowColor: Color(white: 1.0)
        )
    ]

    private let defaults = UserDefaults.standard

    private let keyAutoLogin = "pref_auto_login"
    private let keyNoBackground = "pref_no_background"
    private let keyAccentPalette = "pref_accent_palette"
    private let keyHapticFeedback = "pref_haptic_feedback"
    private let keySpeedUnits = "pref_speed_units"
    private let keyLastAuthSuccess = "pref_last_auth_success"
    private let keyLastAuthFail = "pref_last_auth_fail"
    private let keyAuthFailCount = "pref_auth_fail_count"
    private let keyOnboardingDone = "pref_onboarding_done"
    private let keyExplicitDisconnect = "pref_explicit_disconnect"

    @Published public var isExplicitlyDisconnected: Bool {
        didSet { defaults.set(isExplicitlyDisconnected, forKey: keyExplicitDisconnect) }
    }

    @Published public var autoLoginEnabled: Bool {
        didSet { defaults.set(autoLoginEnabled, forKey: keyAutoLogin) }
    }

    @Published public var noBackgroundMode: Bool {
        didSet { defaults.set(noBackgroundMode, forKey: keyNoBackground) }
    }

    @Published public var selectedPaletteId: String {
        didSet { defaults.set(selectedPaletteId, forKey: keyAccentPalette) }
    }

    @Published public var hapticFeedbackEnabled: Bool {
        didSet { defaults.set(hapticFeedbackEnabled, forKey: keyHapticFeedback) }
    }

    @Published public var speedUnit: String {
        didSet { defaults.set(speedUnit, forKey: keySpeedUnits) }
    }

    public var currentPalette: ColorPalette {
        SettingsStore.palettes.first { $0.id == selectedPaletteId } ?? SettingsStore.palettes.first!
    }

    private init() {
        self.autoLoginEnabled = defaults.object(forKey: keyAutoLogin) as? Bool ?? true
        self.noBackgroundMode = defaults.bool(forKey: keyNoBackground)
        self.selectedPaletteId = defaults.string(forKey: keyAccentPalette) ?? "emerald"
        self.hapticFeedbackEnabled = defaults.object(forKey: keyHapticFeedback) as? Bool ?? true
        self.speedUnit = defaults.string(forKey: keySpeedUnits) ?? "bps"
        self.isExplicitlyDisconnected = defaults.bool(forKey: keyExplicitDisconnect)
    }

    public var isOnboardingCompleted: Bool {
        get { defaults.bool(forKey: keyOnboardingDone) }
        set { defaults.set(newValue, forKey: keyOnboardingDone) }
    }

    public func onAuthSuccess() {
        defaults.set(Date().timeIntervalSince1970, forKey: keyLastAuthSuccess)
        defaults.set(0, forKey: keyAuthFailCount)
    }

    public func onAuthFail() {
        defaults.set(Date().timeIntervalSince1970, forKey: keyLastAuthFail)
        let count = defaults.integer(forKey: keyAuthFailCount)
        defaults.set(count + 1, forKey: keyAuthFailCount)
    }

    public func isRecentlyAuthenticated() -> Bool {
        let last = defaults.double(forKey: keyLastAuthSuccess)
        guard last > 0 else { return false }
        return (Date().timeIntervalSince1970 - last) < 30 * 60 // 30-minute cooldown
    }

    public func shouldAllowAuthAttempt() -> Bool {
        let count = defaults.integer(forKey: keyAuthFailCount)
        let lastFail = defaults.double(forKey: keyLastAuthFail)
        let elapsed = Date().timeIntervalSince1970 - lastFail

        if count >= 3 && elapsed < 3 * 60 { return false }
        if count >= 5 && elapsed < 10 * 60 { return false }
        return true
    }

    public func clearSession() {
        defaults.set(0.0, forKey: keyLastAuthSuccess)
        self.isExplicitlyDisconnected = true
    }
}
