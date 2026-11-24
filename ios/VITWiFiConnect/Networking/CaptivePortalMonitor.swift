import Foundation

/// CaptivePortalMonitor
/// Solves the unpredictable captive portal redirection timing on iOS.
/// Campus gateways (especially Pronto at VIT) delay port 80 hijacking anywhere from 100ms
/// to several seconds after Wi-Fi association.
///
/// This monitor uses adaptive polling with exponential backoff and jitter to detect
/// the exact moment the portal redirect becomes active, triggering immediate login
/// without hanging or failing prematurely.
public final class CaptivePortalMonitor {

    public static let shared = CaptivePortalMonitor()

    private var isPolling = false
    private var pollTask: Task<Void, Never>?

    public typealias PortalDetectedHandler = (_ portalUrl: String) -> Void
    public typealias OnlineDetectedHandler = () -> Void

    private init() {}

    /// Adaptive poller that waits for the captive portal redirect to appear.
    /// Polling intervals: 300ms, 450ms, 600ms, 900ms, 1200ms, 1500ms... up to 15s max.
    public func waitForPortalRedirect(
        onPortalDetected: @escaping PortalDetectedHandler,
        onAlreadyOnline: @escaping OnlineDetectedHandler,
        onTimeout: @escaping () -> Void
    ) {
        cancel()
        isPolling = true

        pollTask = Task {
            var attempt = 0
            let maxAttempts = 18
            var delayMs: UInt64 = 350

            while !Task.isCancelled && attempt < maxAttempts {
                attempt += 1

                // 1. Check if already online
                let isOnline = await NetworkDetector.shared.probeInternet()
                if isOnline {
                    await MainActor.run {
                        self.isPolling = false
                        onAlreadyOnline()
                    }
                    return
                }

                // 2. Probe for portal redirect
                let probe = await NetworkDetector.shared.probePortal()
                if probe.isOnline {
                    await MainActor.run {
                        self.isPolling = false
                        onAlreadyOnline()
                    }
                    return
                }

                if let redirectUrl = probe.redirectUrl, !redirectUrl.isEmpty {
                    await MainActor.run {
                        self.isPolling = false
                        onPortalDetected(redirectUrl)
                    }
                    return
                }

                // 3. Jittered adaptive backoff
                let jitter = UInt64.random(in: 0...100)
                try? await Task.sleep(nanoseconds: (delayMs + jitter) * 1_000_000)

                if delayMs < 1500 {
                    delayMs = UInt64(Double(delayMs) * 1.3)
                }
            }

            if !Task.isCancelled {
                await MainActor.run {
                    self.isPolling = false
                    onTimeout()
                }
            }
        }
    }

    public func cancel() {
        isPolling = false
        pollTask?.cancel()
        pollTask = nil
    }
}
