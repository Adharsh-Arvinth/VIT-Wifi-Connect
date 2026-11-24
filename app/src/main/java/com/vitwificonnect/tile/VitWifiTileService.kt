package com.vitwificonnect.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.vitwificonnect.R
import com.vitwificonnect.data.CredentialStore
import com.vitwificonnect.data.NetworkDetector
import com.vitwificonnect.data.SettingsStore
import com.vitwificonnect.data.VitWifiManager
import com.vitwificonnect.ui.MainActivity
import com.vitwificonnect.util.HapticHelper
import com.vitwificonnect.widget.VitWifiWidgetProvider
import java.util.concurrent.Executors

/**
 * VitWifiTileService
 *
 * True One-Click Control Center Tool:
 * 1. ONE-CLICK DISCONNECT: Tapping when connected cleanly logs out from Pronto gateway
 *    (releasing MAC address) and severs the Wi-Fi connection immediately.
 * 2. ONE-CLICK CONNECT: Tapping when disconnected automatically turns on Wi-Fi if off,
 *    associates with campus AP, and completes captive portal authentication with saved credentials.
 * 3. TILE HOLD / LONG PRESS: Directs straight to MainActivity via QS_TILE_PREFERENCES with
 *    category DEFAULT (never opening the system App Info page).
 */
class VitWifiTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var isBusy = false

    companion object {
        fun requestTileUpdate(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val component = ComponentName(context.applicationContext, VitWifiTileService::class.java)
                    requestListeningState(context.applicationContext, component)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        val ctx = applicationContext
        CredentialStore.init(ctx)
        SettingsStore.init(ctx)

        val tile = qsTile ?: return
        if (isBusy) return

        HapticHelper.click(ctx)

        // Determine intent based on current tile state and live network connection
        val isCurrentlyConnected = (tile.state == Tile.STATE_ACTIVE) ||
                (NetworkDetector.isWifiConnected(ctx) && NetworkDetector.isWifiActuallyOnline(ctx))

        if (isCurrentlyConnected) {
            // ONE-CLICK DISCONNECT FROM NETWORK
            handleDisconnect(ctx)
        } else {
            // ONE-CLICK CONNECT & AUTHENTICATE
            handleConnect(ctx)
        }
    }

    private fun handleDisconnect(ctx: Context) {
        val tile = qsTile ?: return
        isBusy = true

        // Immediate tactile/visual response
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "Disconnecting…"
        } else {
            tile.label = "Disconnecting…"
        }
        tile.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_disconnected)
        tile.updateTile()

        VitWifiManager.disconnect(ctx, object : VitWifiManager.LogoutCallback {
            override fun onSuccess(message: String) {
                isBusy = false
                handler.post {
                    val t = qsTile ?: return@post
                    t.state = Tile.STATE_INACTIVE
                    t.label = getString(R.string.tile_label)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        t.subtitle = "Disconnected"
                    }
                    t.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_disconnected)
                    t.updateTile()

                    Toast.makeText(ctx, "Disconnected from campus Wi-Fi", Toast.LENGTH_SHORT).show()
                    VitWifiWidgetProvider.updateAllWidgets(ctx)
                }
            }

            override fun onFailure(message: String) {
                isBusy = false
                handler.post {
                    val t = qsTile ?: return@post
                    t.state = Tile.STATE_INACTIVE
                    t.label = getString(R.string.tile_label)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        t.subtitle = "Disconnected"
                    }
                    t.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_disconnected)
                    t.updateTile()

                    Toast.makeText(ctx, "Disconnected", Toast.LENGTH_SHORT).show()
                    VitWifiWidgetProvider.updateAllWidgets(ctx)
                }
            }
        })
    }

    private fun handleConnect(ctx: Context) {
        SettingsStore.setExplicitlyDisconnected(ctx, false)

        if (!CredentialStore.hasCredentials(ctx)) {
            Toast.makeText(ctx, "Please save your VIT credentials in the app first", Toast.LENGTH_LONG).show()
            openApp()
            return
        }

        val tile = qsTile ?: return
        isBusy = true

        val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiOn = wifiManager?.isWifiEnabled == true

        // Instant visual feedback
        tile.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (!isWifiOn) "Turning on Wi-Fi…" else getString(R.string.status_connecting)
        } else {
            tile.label = if (!isWifiOn) "Enabling Wi-Fi…" else "Connecting…"
        }
        tile.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_connected)
        tile.updateTile()

        if (!isWifiOn) {
            // Attempt to turn on Wi-Fi programmatically
            val enabled = VitWifiManager.enableWifi(ctx)
            if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // If system restricts background Wi-Fi toggling, present native Wi-Fi panel
                try {
                    val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivityAndCollapse(panelIntent)
                } catch (_: Throwable) {
                    try {
                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivityAndCollapse(intent)
                    } catch (_: Throwable) {}
                }
            }

            // Wait for Wi-Fi radio to become active, then proceed to authenticate
            backgroundExecutor.execute {
                var waitCount = 0
                while ((wifiManager?.isWifiEnabled != true) && waitCount < 24) {
                    try { Thread.sleep(500) } catch (_: InterruptedException) {}
                    waitCount++
                }

                if (wifiManager?.isWifiEnabled == true) {
                    handler.post {
                        val t = qsTile ?: return@post
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            t.subtitle = "Connecting to VIT Wi-Fi…"
                        }
                        t.updateTile()
                    }
                    executeAuthFlow(ctx)
                } else {
                    isBusy = false
                    handler.post {
                        val t = qsTile ?: return@post
                        t.state = Tile.STATE_INACTIVE
                        t.label = getString(R.string.tile_label)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            t.subtitle = "Wi-Fi is off"
                        }
                        t.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_disconnected)
                        t.updateTile()
                    }
                }
            }
        } else {
            executeAuthFlow(ctx)
        }
    }

    private fun executeAuthFlow(ctx: Context) {
        VitWifiManager.connectAndAuthenticate(ctx, object : VitWifiManager.ConnectionCallback {
            override fun onProgress(message: String) {
                handler.post {
                    val t = qsTile ?: return@post
                    t.state = Tile.STATE_ACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        t.subtitle = message
                    }
                    t.updateTile()
                }
            }

            override fun onSuccess(message: String) {
                isBusy = false
                handler.post {
                    val t = qsTile ?: return@post
                    t.state = Tile.STATE_ACTIVE
                    t.label = getString(R.string.tile_label)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        t.subtitle = getString(R.string.status_connected)
                    }
                    t.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_connected)
                    t.updateTile()

                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                    HapticHelper.success(ctx)
                    VitWifiWidgetProvider.updateAllWidgets(ctx)
                }
            }

            override fun onFailure(message: String, isCredentialsError: Boolean) {
                isBusy = false
                handler.post {
                    val t = qsTile ?: return@post
                    t.state = Tile.STATE_INACTIVE
                    t.label = getString(R.string.tile_label)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        t.subtitle = if (isCredentialsError) "Invalid Creds" else "Tap to retry"
                    }
                    t.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_disconnected)
                    t.updateTile()

                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                    VitWifiWidgetProvider.updateAllWidgets(ctx)

                    if (isCredentialsError) {
                        openApp()
                    }
                }
            }
        })
    }

    private fun updateTileState() {
        if (isBusy) return
        val ctx = applicationContext
        CredentialStore.init(ctx)
        SettingsStore.init(ctx)

        // If user explicitly disconnected, maintain disconnected state
        if (SettingsStore.isExplicitlyDisconnected(ctx)) {
            val tile = qsTile ?: return
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.status_disconnected)
            }
            tile.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_disconnected)
            tile.updateTile()
            return
        }

        backgroundExecutor.execute {
            val isWifi = NetworkDetector.isWifiConnected(ctx)
            val isOnline = if (isWifi) NetworkDetector.isWifiActuallyOnline(ctx) else false

            handler.post {
                if (isBusy) return@post
                val tile = qsTile ?: return@post

                if (isOnline) {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = getString(R.string.tile_label)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = getString(R.string.status_connected)
                    }
                    tile.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_connected)
                } else if (isWifi) {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = getString(R.string.tile_label)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = getString(R.string.tile_subtitle_ready)
                    }
                    tile.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_disconnected)
                } else {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = getString(R.string.tile_label)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = getString(R.string.status_disconnected)
                    }
                    tile.icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_disconnected)
                }

                tile.updateTile()
            }
        }
    }

    private fun openApp() {
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                101,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }
}
