import Foundation
import Network

/// NetworkDetector for iOS
/// Uses NWPathMonitor scoped to Wi-Fi to detect active network status,
/// VPN interference, and genuine internet connectivity without leaking to cellular data.
public final class NetworkDetector: ObservableObject {

    public static let shared = NetworkDetector()

    private let monitor = NWPathMonitor(requiringInterfaceType: .wifi)
    private let monitorQueue = DispatchQueue(label: "com.vitwificonnect.networkmonitor")

    @Published public private(set) var isWifiConnected: Bool = false
    @Published public private(set) var isOnline: Bool = false
    @Published public private(set) var isVpnActive: Bool = false
    @Published public private(set) var lastDetectedPortalUrl: String?

    private init() {
        startMonitoring()
    }

    public func startMonitoring() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self = self else { return }

            let wifiAvailable = path.usesInterfaceType(.wifi)
            let vpnRunning = path.availableInterfaces.contains { $0.type == .other }

            DispatchQueue.main.async {
                self.isWifiConnected = wifiAvailable
                self.isVpnActive = vpnRunning
            }

            if wifiAvailable {
                Task {
                    let online = await self.probeInternet()
                    DispatchQueue.main.async {
                        self.isOnline = online
                    }
                }
            } else {
                DispatchQueue.main.async {
                    self.isOnline = false
                }
            }
        }
        monitor.start(queue: monitorQueue)
    }

    public func stopMonitoring() {
        monitor.cancel()
    }

    /// Direct probe to confirm true internet connectivity.
    /// Returns true ONLY if HTTP 204 No Content is received.
    public func probeInternet() async -> Bool {
        guard let url = URL(string: CampusNetworkConstants.probeUrl) else { return false }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 3.5
        request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData

        let config = URLSessionConfiguration.ephemeral
        config.waitsForConnectivity = false
        config.allowsCellularAccess = false // Prevent cellular leakage

        let session = URLSession(configuration: config)

        do {
            let (_, response) = try await session.data(for: request)
            if let httpResponse = response as? HTTPURLResponse {
                return httpResponse.statusCode == 204
            }
            return false
        } catch {
            return false
        }
    }

    /// Captures the captive portal redirect URL if redirected.
    public func probePortal() async -> (isOnline: Bool, isPronto: Bool, redirectUrl: String?) {
        guard let url = URL(string: CampusNetworkConstants.appleHotspotProbe) else {
            return (false, false, nil)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 4.0
        request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData

        let delegate = RedirectCaptureDelegate()
        let config = URLSessionConfiguration.ephemeral
        config.allowsCellularAccess = false

        let session = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)

        do {
            let (data, response) = try await session.data(for: request)
            if let httpResponse = response as? HTTPURLResponse {
                if httpResponse.statusCode == 204 ||
                   (httpResponse.statusCode == 200 && String(data: data, encoding: .utf8)?.contains("<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>") == true) {
                    return (true, false, nil)
                }

                let captured = delegate.redirectLocation
                let isPronto = captured?.contains("pronto") == true ||
                               captured?.contains(CampusNetworkConstants.prontoPortalHost) == true ||
                               String(data: data, encoding: .utf8)?.contains("ProntoAuthentication") == true

                if let captured = captured {
                    DispatchQueue.main.async {
                        self.lastDetectedPortalUrl = captured
                    }
                }
                return (false, isPronto, captured)
            }
            return (false, false, nil)
        } catch {
            return (false, false, nil)
        }
    }
}

private class RedirectCaptureDelegate: NSObject, URLSessionTaskDelegate {
    var redirectLocation: String?

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        if let location = response.allHeaderFields["Location"] as? String {
            self.redirectLocation = location
        } else if let urlStr = request.url?.absoluteString {
            self.redirectLocation = urlStr
        }
        // Don't follow redirect automatically so we inspect the target
        completionHandler(nil)
    }
}
