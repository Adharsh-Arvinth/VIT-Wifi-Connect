import SwiftUI

public struct ContentView: View {

    @ObservedObject var settingsStore = SettingsStore.shared
    @State private var hasCompletedOnboarding: Bool

    public init() {
        let hasCreds = CredentialStore.shared.hasCredentials()
        let isDone = SettingsStore.shared.isOnboardingCompleted
        _hasCompletedOnboarding = State(initialValue: isDone || hasCreds)
    }

    public var body: some View {
        Group {
            if hasCompletedOnboarding {
                HomeView()
            } else {
                OnboardingView {
                    withAnimation(.easeInOut(duration: 0.4)) {
                        hasCompletedOnboarding = true
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
