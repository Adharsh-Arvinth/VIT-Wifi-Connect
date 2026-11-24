import SwiftUI

extension Notification.Name {
    static let vitWifiPerformAction = Notification.Name("vitWifiPerformAction")
}

@main
struct VITWiFiConnectApp: App {

    @StateObject private var settingsStore = SettingsStore.shared
    @StateObject private var networkDetector = NetworkDetector.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(settingsStore)
                .environmentObject(networkDetector)
                .onOpenURL { url in
                    handleIncomingURL(url)
                }
        }
    }

    private func handleIncomingURL(_ url: URL) {
        guard url.scheme == "vitwifi" else { return }
        let action = url.host ?? url.path.replacingOccurrences(of: "/", with: "")
        NotificationCenter.default.post(name: .vitWifiPerformAction, object: action)
    }
}
