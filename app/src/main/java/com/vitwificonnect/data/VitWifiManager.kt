package com.vitwificonnect.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.vitwificonnect.util.HapticHelper
import com.vitwificonnect.widget.VitWifiWidgetProvider
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

object VitWifiManager {

    private val handler = Handler(Looper.getMainLooper())
    @Volatile
    private var suggestionsRegistered = false

    interface ConnectionCallback {
        fun onProgress(message: String)
        fun onSuccess(message: String)
        fun onFailure(message: String, isCredentialsError: Boolean)
    }

    interface LogoutCallback {
        fun onSuccess(message: String)
        fun onFailure(message: String)
    }

    /**
     * Complete one-click connection + authentication flow:
     * 1. Validate saved credentials.
     * 2. Enable Wi-Fi if needed.
     * 3. Register VIT campus network suggestions once.
     * 4. Check if Wi-Fi is already online (skip auth if so).
     * 5. Authenticate with the captive portal (HTTP POST, then WebView fallback).
     * 6. Confirm internet connectivity.
     */
    fun connectAndAuthenticate(context: Context, callback: ConnectionCallback) {
        val appContext = context.applicationContext
        CredentialStore.init(appContext)
        SettingsStore.init(appContext)
        SettingsStore.setExplicitlyDisconnected(appContext, false)

        if (!CredentialStore.hasCredentials(appContext)) {
            callback.onFailure("Please save your registration number and password first", true)
            return
        }

        callback.onProgress("Connecting to WiFi…")

        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

                // Step 1: Ensure Wi-Fi is enabled
                if (wifiManager != null && !wifiManager.isWifiEnabled) {
                    val enabled = enableWifi(appContext)
                    if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        handler.post {
                            try {
                                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                appContext.startActivity(panelIntent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                appContext.startActivity(intent)
                            }
                        }
                    }

                    // Wait up to 10s for Wi-Fi to power on
                    var waitWifiCount = 0
                    while (!wifiManager.isWifiEnabled && waitWifiCount < 20) {
                        Thread.sleep(500)
                        waitWifiCount++
                    }
                }

                // Step 2: Register network suggestions once (not on every single call to avoid disconnecting existing AP)
                if (!suggestionsRegistered) {
                    registerCampusNetworkSuggestions(appContext)
                    suggestionsRegistered = true
                }

                // Step 3: Trigger reconnect if not connected yet
                if (!NetworkDetector.isWifiConnected(appContext)) {
                    wifiManager?.let { wm ->
                        try {
                            @Suppress("DEPRECATION")
                            wm.reconnect()
                        } catch (_: Throwable) {}
                    }
                }

                // Step 4: Wait up to 8s for Wi-Fi association if not yet connected
                var waitCount = 0
                while (!NetworkDetector.isWifiConnected(appContext) && waitCount < 16) {
                    Thread.sleep(500)
                    waitCount++
                }

                if (!NetworkDetector.isWifiConnected(appContext)) {
                    handler.post {
                        callback.onFailure("No Wi-Fi connection found. Please connect to a VIT network.", false)
                    }
                    executor.shutdown()
                    return@execute
                }

                // Step 4: Check if already authenticated & online (avoids duplicate login session collision)
                if (NetworkDetector.isWifiActuallyOnline(appContext)) {
                    SettingsStore.onAuthSuccess(appContext)
                    HapticHelper.success(appContext)
                    VitWifiWidgetProvider.updateAllWidgets(appContext)
                    com.vitwificonnect.tile.VitWifiTileService.requestTileUpdate(appContext)
                    handler.post {
                        callback.onSuccess("Already connected to VIT WiFi")
                    }
                    executor.shutdown()
                    return@execute
                }

                // Step 5: Captive portal authentication
                handler.post {
                    callback.onProgress("Authenticating with campus gateway…")
                }

                val reg = CredentialStore.getRegNumber(appContext)
                val pass = CredentialStore.getPassword(appContext)

                if (reg.isNullOrBlank() || pass.isNullOrBlank()) {
                    handler.post {
                        callback.onFailure("Please enter your registration number and password", true)
                    }
                    executor.shutdown()
                    return@execute
                }

                val authenticator = WiFiAuthenticator(appContext)
                authenticator.authenticate(reg, pass, object : WiFiAuthenticator.AuthCallback {
                    override fun onSuccess(message: String) {
                        SettingsStore.onAuthSuccess(appContext)
                        HapticHelper.success(appContext)
                        VitWifiWidgetProvider.updateAllWidgets(appContext)
                        com.vitwificonnect.tile.VitWifiTileService.requestTileUpdate(appContext)
                        callback.onSuccess(message)
                        executor.shutdown()
                    }

                    override fun onFailure(message: String, status: ConnectionStatus) {
                        SettingsStore.onAuthFail(appContext)
                        HapticHelper.error(appContext)
                        VitWifiWidgetProvider.updateAllWidgets(appContext)
                        com.vitwificonnect.tile.VitWifiTileService.requestTileUpdate(appContext)
                        val isCred = status is ConnectionStatus.Failed &&
                            (status.reason == "invalid_credentials" || status.reason == "rejected")
                        callback.onFailure(message, isCred)
                        executor.shutdown()
                    }
                })

            } catch (e: Exception) {
                handler.post {
                    callback.onFailure("Connection error: ${e.message}", false)
                }
                executor.shutdown()
            }
        }
    }

    /**
     * Programmatically turns on Wi-Fi if currently disabled.
     * Uses direct API call and reflection for broad OEM ROM compatibility.
     */
    fun enableWifi(context: Context): Boolean {
        val appContext = context.applicationContext
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        if (wifiManager.isWifiEnabled) return true

        // Method 1: Direct API
        try {
            @Suppress("DEPRECATION")
            val direct = wifiManager.setWifiEnabled(true)
            if (direct || wifiManager.isWifiEnabled) return true
        } catch (_: Throwable) {}

        // Method 2: Reflection on WifiManager for OEM compatibility
        try {
            val method = wifiManager.javaClass.getMethod("setWifiEnabled", Boolean::class.javaPrimitiveType)
            val res = method.invoke(wifiManager, true) as? Boolean
            if (res == true || wifiManager.isWifiEnabled) return true
        } catch (_: Throwable) {}

        return wifiManager.isWifiEnabled
    }

    /**
     * True Network Disconnection & Session Logout:
     * 1. Calls Pronto authlogout endpoint to release MAC address from campus gateway.
     * 2. Suppresses automatic background reconnection until user explicitly requests it.
     * 3. Unbinds Wi-Fi network interface from the process.
     * 4. Removes campus network suggestions on Android 10+ and severs AP association.
     * 5. Updates widgets and Quick Settings tile immediately.
     */
    fun disconnect(context: Context, callback: LogoutCallback? = null) {
        val appContext = context.applicationContext
        SettingsStore.init(appContext)
        SettingsStore.setExplicitlyDisconnected(appContext, true)
        SettingsStore.setLastAuthSuccessTime(appContext, 0L)

        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                // Step 1: Release device MAC from Pronto gateway across all potential endpoints
                val wifiNetwork = NetworkDetector.getWifiNetwork(appContext)
                val gatewayIp = NetworkDetector.getWifiGatewayIp(appContext)

                val logoutUrls = mutableListOf<String>()
                logoutUrls.add(LOGOUT_URL)
                logoutUrls.add("https://phc.prontonetworks.com/cgi-bin/authlogout")
                if (!gatewayIp.isNullOrBlank()) {
                    logoutUrls.add("http://$gatewayIp/cgi-bin/authlogout")
                    logoutUrls.add("https://$gatewayIp/cgi-bin/authlogout")
                }

                for (urlString in logoutUrls) {
                    try {
                        val targetUrl = URL(urlString)
                        val conn = if (wifiNetwork != null) {
                            wifiNetwork.openConnection(targetUrl) as HttpURLConnection
                        } else {
                            targetUrl.openConnection() as HttpURLConnection
                        }

                        if (conn is HttpsURLConnection) {
                            conn.sslSocketFactory = WiFiAuthenticator.trustingSSLSocketFactory
                            conn.hostnameVerifier = WiFiAuthenticator.trustingHostnameVerifier
                        }

                        conn.connectTimeout = 2500
                        conn.readTimeout = 2500
                        conn.requestMethod = "GET"
                        conn.instanceFollowRedirects = true
                        conn.useCaches = false
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        conn.setRequestProperty("Accept", "*/*")
                        conn.setRequestProperty("Connection", "close")

                        val code = conn.responseCode
                        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
                        try { stream?.readBytes() } catch (_: Throwable) {}
                        conn.disconnect()
                    } catch (_: Throwable) {}
                }

                // Step 2: Unbind process from Wi-Fi
                NetworkDetector.unbindFromWifi(appContext)

                // Step 3: Remove network suggestions if on Android 10+ so system doesn't auto-reconnect
                val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        wifiManager?.removeNetworkSuggestions(emptyList())
                    } catch (_: Throwable) {}
                }

                // Step 4: Sever Wi-Fi association with current access point if allowed
                wifiManager?.let { wm ->
                    try {
                        @Suppress("DEPRECATION")
                        wm.disconnect()
                    } catch (_: Throwable) {}

                    try {
                        @Suppress("DEPRECATION")
                        val netId = wm.connectionInfo?.networkId ?: -1
                        if (netId != -1) {
                            wm.disableNetwork(netId)
                        }
                    } catch (_: Throwable) {}
                }

                // Step 5: Update live widgets and tile
                VitWifiWidgetProvider.updateAllWidgets(appContext)
                com.vitwificonnect.tile.VitWifiTileService.requestTileUpdate(appContext)

                handler.post {
                    callback?.onSuccess("Disconnected from campus Wi-Fi")
                }
            } catch (e: Exception) {
                handler.post {
                    VitWifiWidgetProvider.updateAllWidgets(appContext)
                    com.vitwificonnect.tile.VitWifiTileService.requestTileUpdate(appContext)
                    callback?.onFailure("Disconnect error: ${e.message}")
                }
            } finally {
                executor.shutdown()
            }
        }
    }

    /**
     * Session Logout: Delegates to full disconnect.
     */
    fun logout(context: Context, callback: LogoutCallback) {
        disconnect(context, callback)
    }

    /**
     * Registers VIT network suggestions so Android auto-connects when in range.
     */
    fun registerCampusNetworkSuggestions(context: Context) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val suggestions = NetworkDetector.KNOWN_VIT_SSIDS.map { ssid ->
                    WifiNetworkSuggestion.Builder()
                        .setSsid(ssid)
                        .setIsAppInteractionRequired(false)
                        .build()
                }
                wifiManager.addNetworkSuggestions(suggestions)
            } catch (_: Exception) {}
        } else {
            try {
                @Suppress("DEPRECATION")
                for (ssid in NetworkDetector.KNOWN_VIT_SSIDS) {
                    val config = WifiConfiguration().apply {
                        SSID = "\"$ssid\""
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    }
                    val netId = wifiManager.addNetwork(config)
                    if (netId != -1) {
                        wifiManager.enableNetwork(netId, false)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
