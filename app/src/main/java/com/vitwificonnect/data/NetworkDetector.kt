package com.vitwificonnect.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

object NetworkDetector {

    private const val BODY_SNIPPET_BYTES = 8 * 1024

    // Known VIT campus network SSIDs
    val KNOWN_VIT_SSIDS = listOf(
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
    )

    @Volatile
    var lastDetectedLoginUrl: String? = null
        private set

    /**
     * Finds the active Wi-Fi Network object, regardless of whether it has validated internet.
     * Critical when behind a captive portal where Android defaults to cellular data.
     */
    fun getWifiNetwork(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return network
            }
        }
        // Fallback: accept WiFi even if VPN-wrapped (last resort)
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return network
            }
        }
        return null
    }

    /**
     * Binds all subsequent network requests in this process to the Wi-Fi network interface.
     * NOTE: This can throw EPERM if a VPN is active. We catch all exceptions safely.
     * Prefer using getWifiNetwork() + network.openConnection() over bindProcessToNetwork().
     */
    fun bindToWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val wifiNetwork = getWifiNetwork(context) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.bindProcessToNetwork(wifiNetwork)
            } else {
                @Suppress("DEPRECATION")
                ConnectivityManager.setProcessDefaultNetwork(wifiNetwork)
            }
        } catch (_: Throwable) {
            // EPERM happens when VPN is active — this is non-fatal, we still have getWifiNetwork()
            false
        }
    }

    /**
     * Unbinds the process, restoring default system network routing.
     */
    fun unbindFromWifi(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.bindProcessToNetwork(null)
            } else {
                @Suppress("DEPRECATION")
                ConnectivityManager.setProcessDefaultNetwork(null)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Checks if a VPN connection is active on the device.
     */
    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true
            }
        }
        return false
    }

    /**
     * Retrieves the IPv4 address of the Wi-Fi DHCP gateway.
     */
    fun getWifiGatewayIp(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val gatewayInt = wifiManager?.dhcpInfo?.gateway ?: 0
            if (gatewayInt != 0) {
                "${gatewayInt and 0xFF}.${(gatewayInt shr 8) and 0xFF}.${(gatewayInt shr 16) and 0xFF}.${(gatewayInt shr 24) and 0xFF}"
            } else null
        } catch (_: Exception) { null }
    }

    fun isWifiConnected(context: Context): Boolean = getWifiNetwork(context) != null

    fun getConnectedSsid(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiNetwork = getWifiNetwork(context)

        if (cm != null && wifiNetwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val caps = cm.getNetworkCapabilities(wifiNetwork)
            val info = caps?.transportInfo as? WifiInfo
            val ssid = info?.ssid?.trim()?.removeSurrounding("\"")
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") return ssid
        }

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val rawSsid = wifiManager?.connectionInfo?.ssid?.trim()?.removeSurrounding("\"")
            if (!rawSsid.isNullOrEmpty() && rawSsid != "<unknown ssid>") return rawSsid
        } catch (_: Exception) {}

        return null
    }

    fun isVitNetwork(context: Context): Boolean {
        val ssid = getConnectedSsid(context)
        if (ssid != null && (ssid.contains("VIT", ignoreCase = true) ||
                KNOWN_VIT_SSIDS.any { it.equals(ssid, ignoreCase = true) })) {
            return true
        }
        val probe = probePortal(context)
        if (probe.isProntoPortal) return true
        if (isOnline(context)) {
            return ssid?.contains("VIT", ignoreCase = true) == true
        }
        return probe.isProntoPortal
    }

    /**
     * Checks if Wi-Fi has genuine internet — strictly over the Wi-Fi Network object.
     * Never falls back to cellular to prevent false positives from Jio 5G.
     */
    fun isWifiActuallyOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val wifiNetwork = getWifiNetwork(context) ?: return false
        val caps = cm.getNetworkCapabilities(wifiNetwork) ?: return false

        // Android explicitly flagged captive portal = NOT online
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) return false

        // Android already marked as validated = online
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return true

        // Direct HTTP 204 probe strictly through the Wi-Fi Network object
        return try {
            val conn = wifiNetwork.openConnection(URL(PROBE_URL)) as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.useCaches = false
            conn.connect()
            val code = conn.responseCode
            try { conn.disconnect() } catch (_: Throwable) {}
            code == 204
        } catch (_: Throwable) {
            false
        }
    }

    fun isOnline(context: Context? = null): Boolean {
        if (context != null) return isWifiActuallyOnline(context)
        return try {
            intercept(URL(PROBE_URL), null).isOnline
        } catch (_: Exception) { false }
    }

    fun probePortal(context: Context? = null): PortalProbeResult {
        return try {
            val result = intercept(URL(PROBE_URL), context)
            if (!result.isOnline && result.portalHost != null) {
                lastDetectedLoginUrl = result.portalHost
            }
            result
        } catch (_: UnknownHostException) {
            PortalProbeResult(false, false, null, null)
        } catch (_: Exception) {
            PortalProbeResult(false, false, null, null)
        }
    }

    private fun intercept(url: URL, context: Context?): PortalProbeResult {
        val wifiNetwork = context?.let { getWifiNetwork(it) }
        // Always try Wi-Fi network object first (bypasses process binding / EPERM entirely)
        if (wifiNetwork != null) {
            val wifiResult = tryExecuteProbe(url, wifiNetwork)
            if (wifiResult != null) return wifiResult
        }
        // Fallback: unbound connection (may go through cellular if VPN or no Wi-Fi network object)
        val fallbackResult = tryExecuteProbe(url, null)
        return fallbackResult ?: PortalProbeResult(false, false, null, null)
    }

    private fun tryExecuteProbe(url: URL, network: Network?): PortalProbeResult? {
        var connection: HttpURLConnection? = null
        return try {
            val conn = if (network != null) {
                network.openConnection(url) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }
            connection = conn
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            conn.useCaches = false
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode == 204) {
                PortalProbeResult(true, false, null, responseCode)
            } else {
                val location = conn.getHeaderField("Location")
                val body = bodySnippet(conn)
                val isPronto = pointsAtPronto(location, body)
                if (location != null) lastDetectedLoginUrl = location
                PortalProbeResult(false, isPronto, location, responseCode)
            }
        } catch (_: Throwable) {
            null
        } finally {
            try { connection?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun bodySnippet(conn: HttpURLConnection): String? = try {
        (if (conn.responseCode >= 400) conn.errorStream else conn.inputStream)
            ?.use { String(it.readUpTo(BODY_SNIPPET_BYTES), java.nio.charset.Charset.forName("ISO-8859-1")) }
    } catch (_: Exception) { null }

    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val read = read(buffer, total, limit - total)
            if (read < 0) break
            total += read
        }
        return buffer.copyOf(total)
    }

    private fun pointsAtPronto(location: String?, body: String?): Boolean {
        val redirectHost = location?.let { runCatching { URL(it).host }.getOrNull() }
        if (redirectHost?.contains("pronto", ignoreCase = true) == true ||
            redirectHost?.equals(PRONTO_PORTAL_HOST, ignoreCase = true) == true) {
            return true
        }
        return body?.contains(PRONTO_PORTAL_HOST, ignoreCase = true) == true ||
               body?.contains("ProntoAuthentication", ignoreCase = true) == true
    }
}
