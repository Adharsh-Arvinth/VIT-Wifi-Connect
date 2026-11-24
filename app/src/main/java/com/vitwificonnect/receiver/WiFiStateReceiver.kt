package com.vitwificonnect.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import com.vitwificonnect.data.CredentialStore
import com.vitwificonnect.data.NetworkDetector
import com.vitwificonnect.data.SettingsStore
import com.vitwificonnect.data.VitWifiManager
import com.vitwificonnect.widget.VitWifiWidgetProvider

class WiFiStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action ?: return

            CredentialStore.init(context)
            SettingsStore.init(context)

            when (action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)
                    if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                        checkAndAuthenticate(context)
                    } else {
                        // Wi-Fi disabled — update widget and tile to disconnected state
                        VitWifiWidgetProvider.updateAllWidgets(context)
                        com.vitwificonnect.tile.VitWifiTileService.requestTileUpdate(context)
                    }
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    // Rate-limit: only trigger if not recently authenticated
                    checkAndAuthenticate(context)
                }
            }
        } catch (_: Exception) {}
    }

    private fun checkAndAuthenticate(context: Context) {
        try {
            VitWifiWidgetProvider.updateAllWidgets(context)
            com.vitwificonnect.tile.VitWifiTileService.requestTileUpdate(context)

            if (SettingsStore.isExplicitlyDisconnected(context)) return
            if (!SettingsStore.isAutoLoginEnabled(context)) return
            if (SettingsStore.isNoBackgroundEnabled(context)) return
            if (!CredentialStore.hasCredentials(context)) return
            if (!NetworkDetector.isWifiConnected(context)) return

            // Skip if already online
            if (NetworkDetector.isWifiActuallyOnline(context)) return

            // Rate limit: skip if authenticated within last 5 minutes (avoid hammering the portal)
            val lastSuccess = SettingsStore.getLastAuthSuccessTime(context)
            if (lastSuccess > 0L && System.currentTimeMillis() - lastSuccess < 5 * 60 * 1000) return

            // Rate limit: skip if too many recent failures
            if (!SettingsStore.shouldAllowAuthAttempt(context)) return

            VitWifiManager.connectAndAuthenticate(context, object : VitWifiManager.ConnectionCallback {
                override fun onProgress(message: String) {}
                override fun onSuccess(message: String) {
                    VitWifiWidgetProvider.updateAllWidgets(context)
                    com.vitwificonnect.tile.VitWifiTileService.requestTileUpdate(context)
                }
                override fun onFailure(message: String, isCredentialsError: Boolean) {
                    VitWifiWidgetProvider.updateAllWidgets(context)
                    com.vitwificonnect.tile.VitWifiTileService.requestTileUpdate(context)
                }
            })
        } catch (_: Exception) {}
    }
}
