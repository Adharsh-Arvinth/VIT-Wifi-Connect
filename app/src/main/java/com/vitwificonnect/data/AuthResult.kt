package com.vitwificonnect.data

sealed interface ConnectionStatus {
    object Idle : ConnectionStatus
    object Success : ConnectionStatus
    data class Connecting(val step: String) : ConnectionStatus
    data class Failed(val reason: String) : ConnectionStatus
}

data class AuthResult(
    val success: Boolean,
    val status: ConnectionStatus,
    val message: String? = null
)

data class PortalProbeResult(
    val isOnline: Boolean,
    val isProntoPortal: Boolean,
    val portalHost: String?,
    val responseCode: Int?
)

const val PRONTO_PORTAL_HOST = "phc.prontonetworks.com"
const val PROBE_URL = "http://clients3.google.com/generate_204"
const val PROBE_URL_ALT = "http://connectivitycheck.gstatic.com/generate_204"
const val LOGIN_URL_HTTP = "http://phc.prontonetworks.com/cgi-bin/authlogin?URI=http://captive.apple.com/hotspot-detect.html"
const val LOGIN_URL_HTTPS = "https://phc.prontonetworks.com/cgi-bin/authlogin?URI=http://captive.apple.com/hotspot-detect.html"
const val LOGIN_URL = LOGIN_URL_HTTP
const val LOGOUT_URL = "http://phc.prontonetworks.com/cgi-bin/authlogout"
