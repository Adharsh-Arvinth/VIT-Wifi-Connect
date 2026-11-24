import Foundation

public enum ConnectionStatus: Equatable {
    case disconnected
    case connecting(step: String)
    case authenticating
    case connected
    case error(message: String)
    case vpnBlocked
}

public struct AuthResult {
    public let success: Bool
    public let status: ConnectionStatus
    public let message: String?

    public init(success: Bool, status: ConnectionStatus, message: String? = nil) {
        self.success = success
        self.status = status
        self.message = message
    }
}

public enum CampusNetworkConstants {
    public static let prontoPortalHost = "phc.prontonetworks.com"
    public static let probeUrl = "http://clients3.google.com/generate_204"
    public static let appleHotspotProbe = "http://captive.apple.com/hotspot-detect.html"
    public static let loginUrlHttp = "http://phc.prontonetworks.com/cgi-bin/authlogin?URI=http://captive.apple.com/hotspot-detect.html"
    public static let cleanLoginUrl = "http://phc.prontonetworks.com/cgi-bin/authlogin"
    public static let logoutUrl = "http://phc.prontonetworks.com/cgi-bin/authlogout"

    public static let knownVitSsids = [
        "F-VIT",
        "VIT5G",
        "VIT2.4G",
        "VIT-Student",
        "VIT-Faculty",
        "VIT-Guest",
        "VIT-WIFI",
        "VIT_WiFi",
        "VIT-WIFI-2",
        "VIT"
    ]
}
