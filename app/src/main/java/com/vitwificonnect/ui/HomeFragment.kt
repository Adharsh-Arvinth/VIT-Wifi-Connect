package com.vitwificonnect.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vitwificonnect.R
import com.vitwificonnect.data.CredentialStore
import com.vitwificonnect.data.NetworkDetector
import com.vitwificonnect.data.NetworkStatsManager
import com.vitwificonnect.data.SettingsStore
import com.vitwificonnect.data.VitWifiManager
import com.vitwificonnect.ui.view.ConnectionIndicatorView
import com.vitwificonnect.ui.view.NetworkHistogramView
import com.vitwificonnect.util.HapticHelper
import java.util.concurrent.Executors

class HomeFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private var isConnecting = false
    private var isSamplingRunning = false

    private lateinit var ambientBackdrop: com.vitwificonnect.ui.view.AmbientBackdropView
    private lateinit var cardHero: com.vitwificonnect.ui.view.LiquidGlassCardView
    private lateinit var cardNetworkActivity: com.vitwificonnect.ui.view.LiquidGlassCardView
    private lateinit var cardQuickSettings: com.vitwificonnect.ui.view.LiquidGlassCardView
    private lateinit var layoutStatusBadge: View
    private lateinit var viewStatusDot: View
    private lateinit var tvStatusBadge: TextView
    private lateinit var indicatorView: ConnectionIndicatorView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnConnect: MaterialButton
    private lateinit var layoutConnectedActions: View
    private lateinit var btnDisconnect: MaterialButton
    private lateinit var btnReconnect: MaterialButton
    private lateinit var btnSettings: ImageButton
    private lateinit var histogramView: NetworkHistogramView
    private lateinit var tvSpeedDown: TextView
    private lateinit var tvSpeedUp: TextView
    private lateinit var ivSpeedDown: android.widget.ImageView
    private lateinit var ivSpeedUp: android.widget.ImageView
    private lateinit var ivSessionClock: android.widget.ImageView
    private lateinit var ivSessionStats: android.widget.ImageView
    private lateinit var tvSessionTime: TextView
    private lateinit var tvDataTransferred: TextView
    private lateinit var switchAutoLogin: SwitchCompat

    private val statsSamplerRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || !isSamplingRunning) return
            val (downBps, upBps) = NetworkStatsManager.sample()
            val speedUnit = SettingsStore.getSpeedUnits(requireContext())

            tvSpeedDown.text = NetworkStatsManager.formatSpeed(downBps, speedUnit)
            tvSpeedUp.text = NetworkStatsManager.formatSpeed(upBps, speedUnit)
            histogramView.setValues(NetworkStatsManager.getHistory())

            val duration = NetworkStatsManager.getSessionDuration()
            tvSessionTime.text = NetworkStatsManager.formatDuration(duration)

            val totalBytes = NetworkStatsManager.totalSessionRxBytes + NetworkStatsManager.totalSessionTxBytes
            tvDataTransferred.text = NetworkStatsManager.formatData(totalBytes)

            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        ambientBackdrop = view.findViewById(R.id.ambient_backdrop)
        cardHero = view.findViewById(R.id.card_hero)
        cardNetworkActivity = view.findViewById(R.id.card_network_activity)
        cardQuickSettings = view.findViewById(R.id.card_quick_settings)
        layoutStatusBadge = view.findViewById(R.id.layout_status_badge)
        viewStatusDot = view.findViewById(R.id.view_status_dot)
        tvStatusBadge = view.findViewById(R.id.tv_status_badge)
        indicatorView = view.findViewById(R.id.connection_indicator)
        tvTitle = view.findViewById(R.id.tv_connection_title)
        tvSubtitle = view.findViewById(R.id.tv_connection_subtitle)
        btnConnect = view.findViewById(R.id.btn_connect)
        layoutConnectedActions = view.findViewById(R.id.layout_connected_actions)
        btnDisconnect = view.findViewById(R.id.btn_disconnect)
        btnReconnect = view.findViewById(R.id.btn_reconnect)
        btnSettings = view.findViewById(R.id.btn_settings)
        histogramView = view.findViewById(R.id.network_histogram)
        tvSpeedDown = view.findViewById(R.id.tv_speed_down)
        tvSpeedUp = view.findViewById(R.id.tv_speed_up)
        ivSpeedDown = view.findViewById(R.id.iv_speed_down)
        ivSpeedUp = view.findViewById(R.id.iv_speed_up)
        ivSessionClock = view.findViewById(R.id.iv_session_clock)
        ivSessionStats = view.findViewById(R.id.iv_session_stats)
        tvSessionTime = view.findViewById(R.id.tv_session_time)
        tvDataTransferred = view.findViewById(R.id.tv_data_transferred)
        switchAutoLogin = view.findViewById(R.id.switch_auto_login)

        switchAutoLogin.isChecked = SettingsStore.isAutoLoginEnabled(ctx)
        switchAutoLogin.setOnCheckedChangeListener { buttonView, isChecked ->
            HapticHelper.toggle(buttonView)
            SettingsStore.setAutoLogin(ctx, isChecked)
        }

        btnSettings.setOnClickListener {
            HapticHelper.click(btnSettings)
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        btnConnect.setOnClickListener {
            handleConnectAction()
        }

        btnReconnect.setOnClickListener {
            handleConnectAction()
        }

        btnDisconnect.setOnClickListener {
            val c = context ?: return@setOnClickListener
            HapticHelper.click(btnDisconnect)

            // Immediate optimistic UI response
            indicatorView.setState(ConnectionIndicatorView.State.DISCONNECTED)
            tvTitle.text = getString(R.string.status_disconnected)
            tvSubtitle.text = "Logging out & releasing session…"
            tvStatusBadge.text = "Disconnecting…"
            val neutralColor = Color.parseColor("#9CA3AF")
            tvStatusBadge.setTextColor(neutralColor)
            viewStatusDot.backgroundTintList = ColorStateList.valueOf(neutralColor)

            btnConnect.visibility = View.VISIBLE
            layoutConnectedActions.visibility = View.GONE
            btnConnect.isEnabled = false
            btnConnect.text = "Disconnecting…"

            stopNetworkStatsSampling()
            resetMetricsDisplay()

            VitWifiManager.disconnect(c, object : VitWifiManager.LogoutCallback {
                override fun onSuccess(message: String) {
                    if (!isAdded) return
                    requireActivity().runOnUiThread {
                        btnConnect.isEnabled = true
                        btnConnect.text = getString(R.string.action_connect)
                        tvStatusBadge.text = "Disconnected"
                        tvTitle.text = getString(R.string.status_disconnected)
                        tvSubtitle.text = "Logged out from campus gateway"
                        Toast.makeText(c, message, Toast.LENGTH_SHORT).show()
                        HapticHelper.success(c)
                    }
                }

                override fun onFailure(message: String) {
                    if (!isAdded) return
                    requireActivity().runOnUiThread {
                        btnConnect.isEnabled = true
                        btnConnect.text = getString(R.string.action_connect)
                        tvStatusBadge.text = "Disconnected"
                        tvTitle.text = getString(R.string.status_disconnected)
                        tvSubtitle.text = "Session released"
                        Toast.makeText(c, "Disconnected from campus Wi-Fi", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        applyAccentTheme()
        refreshNetworkState()
    }

    private fun applyAccentTheme() {
        val ctx = context ?: return
        try {
            val primary = SettingsStore.getAccentColorHex(ctx)
            val secondary = SettingsStore.getSecondaryColorHex(ctx)
            val glow = SettingsStore.getGlowColorHex(ctx)

            // 1. Signature Atmospheric Ambient Glow Backdrop
            ambientBackdrop.setPaletteColors(primary, secondary)

            // 2. Dynamic Liquid Glass Cards Specular & Refraction Tinting
            cardHero.setAccentColor(primary, secondary)
            cardNetworkActivity.setAccentColor(primary, secondary)
            cardQuickSettings.setAccentColor(primary, secondary)

            // 3. Dynamic Connection Indicator (disconnected, connecting, connected)
            indicatorView.setAccentColor(primary, secondary)

            // 4. Network Histogram Activity Bar Visualizer
            histogramView.setAccentColor(primary, glow)

            // 5. iOS-grade Luminous Primary Action Buttons
            btnConnect.backgroundTintList = ColorStateList.valueOf(primary)
            btnReconnect.backgroundTintList = ColorStateList.valueOf(primary)
            val r = Color.red(primary)
            val g = Color.green(primary)
            val b = Color.blue(primary)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            val textColor = if (luminance > 0.58) Color.parseColor("#08090C") else Color.parseColor("#FFFFFF")
            btnConnect.setTextColor(textColor)
            btnReconnect.setTextColor(textColor)
            btnConnect.iconTint = ColorStateList.valueOf(textColor)
            btnReconnect.iconTint = ColorStateList.valueOf(textColor)

            // Disconnect button styling (subtle crimson accent stroke on dark glass)
            btnDisconnect.strokeColor = ColorStateList.valueOf(Color.argb(90, 239, 68, 68))
            btnDisconnect.setTextColor(Color.parseColor("#F87171"))
            btnDisconnect.iconTint = ColorStateList.valueOf(Color.parseColor("#F87171"))

            // Status Badge dot
            viewStatusDot.backgroundTintList = ColorStateList.valueOf(primary)

            // 6. Dynamic Switch Theme (Thumb & Track)
            applySwitchTheme(switchAutoLogin, primary)

            // 7. Dynamic Metric Icons Tinting
            val iconTint = ColorStateList.valueOf(primary)
            ivSpeedDown.imageTintList = iconTint
            ivSpeedUp.imageTintList = iconTint
            ivSessionClock.imageTintList = iconTint
            ivSessionStats.imageTintList = iconTint
        } catch (_: Exception) {}
    }

    private fun applySwitchTheme(switch: SwitchCompat, accentColor: Int) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(
            accentColor,
            Color.parseColor("#71717A")
        )
        val trackColors = intArrayOf(
            Color.argb(100, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
            Color.argb(50, 113, 113, 122)
        )
        switch.thumbTintList = ColorStateList(states, thumbColors)
        switch.trackTintList = ColorStateList(states, trackColors)
    }

    private fun showQuickLogoutDialog() {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.pref_session_logout)
            .setMessage("Release your active device MAC from the campus gateway? This allows you to log in from your laptop or another device.")
            .setPositiveButton("Logout") { _, _ ->
                VitWifiManager.logout(ctx, object : VitWifiManager.LogoutCallback {
                    override fun onSuccess(message: String) {
                        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                        HapticHelper.success(ctx)
                        refreshNetworkState()
                    }

                    override fun onFailure(message: String) {
                        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                    }
                })
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        stopNetworkStatsSampling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopNetworkStatsSampling()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Refreshes the UI state only.
     * CRITICAL: Never initiates auto-connection or background re-authentication here!
     * Doing so caused rapid duplicate authentication POSTs to Pronto, terminating the campus session!
     */
    fun refreshNetworkState() {
        if (!isAdded || isConnecting) return
        val ctx = context ?: return

        // If user explicitly disconnected, keep UI strictly in disconnected state!
        if (SettingsStore.isExplicitlyDisconnected(ctx)) {
            indicatorView.setState(ConnectionIndicatorView.State.DISCONNECTED)
            tvTitle.text = getString(R.string.status_disconnected)
            tvSubtitle.text = "Session released · Disconnected"
            tvStatusBadge.text = "Offline · Ready"
            val neutralColor = Color.parseColor("#9CA3AF")
            tvStatusBadge.setTextColor(neutralColor)
            viewStatusDot.backgroundTintList = ColorStateList.valueOf(neutralColor)

            btnConnect.visibility = View.VISIBLE
            layoutConnectedActions.visibility = View.GONE
            btnConnect.isEnabled = true
            btnConnect.text = getString(R.string.action_connect)
            stopNetworkStatsSampling()
            resetMetricsDisplay()
            return
        }

        // Lightweight ephemeral thread to query network capabilities without blocking UI
        Thread {
            val isWifiConnected = NetworkDetector.isWifiConnected(ctx)
            val isOnline = if (isWifiConnected) NetworkDetector.isWifiActuallyOnline(ctx) else false
            val isVitNetwork = if (isWifiConnected && !isOnline) NetworkDetector.isVitNetwork(ctx) else false
            val isVpnActive = NetworkDetector.isVpnActive(ctx)
            val hasCredentials = CredentialStore.hasCredentials(ctx)
            val ssid = NetworkDetector.getConnectedSsid(ctx)

            handler.post {
                if (!isAdded || isConnecting) return@post
                val primaryColor = SettingsStore.getAccentColorHex(ctx)

                when {
                    isOnline -> {
                        indicatorView.setState(ConnectionIndicatorView.State.CONNECTED)
                        tvTitle.text = getString(R.string.status_connected)
                        tvSubtitle.text = if (!ssid.isNullOrEmpty()) "$ssid · Online" else getString(R.string.label_internet_active)
                        tvStatusBadge.text = "Connected · Campus 5G"
                        tvStatusBadge.setTextColor(primaryColor)
                        viewStatusDot.backgroundTintList = ColorStateList.valueOf(primaryColor)

                        btnConnect.visibility = View.GONE
                        layoutConnectedActions.visibility = View.VISIBLE
                        btnDisconnect.isEnabled = true
                        btnReconnect.isEnabled = true

                        NetworkStatsManager.onConnected()
                        startNetworkStatsSampling()
                    }
                    isVpnActive && !isOnline -> {
                        indicatorView.setState(ConnectionIndicatorView.State.DISCONNECTED)
                        tvTitle.text = "VPN is active"
                        tvSubtitle.text = "Pause VPN to allow campus gateway login"
                        tvStatusBadge.text = "VPN active · Pause needed"
                        val warningColor = Color.parseColor("#F59E0B")
                        tvStatusBadge.setTextColor(warningColor)
                        viewStatusDot.backgroundTintList = ColorStateList.valueOf(warningColor)

                        btnConnect.visibility = View.VISIBLE
                        layoutConnectedActions.visibility = View.GONE
                        btnConnect.text = "Open VPN Settings"
                        btnConnect.isEnabled = true
                        stopNetworkStatsSampling()
                        NetworkStatsManager.onDisconnected()
                        resetMetricsDisplay()
                    }
                    isVitNetwork -> {
                        indicatorView.setState(ConnectionIndicatorView.State.DISCONNECTED)
                        tvStatusBadge.text = "Campus gateway detected"
                        tvStatusBadge.setTextColor(primaryColor)
                        viewStatusDot.backgroundTintList = ColorStateList.valueOf(primaryColor)

                        btnConnect.visibility = View.VISIBLE
                        layoutConnectedActions.visibility = View.GONE
                        if (hasCredentials) {
                            tvTitle.text = getString(R.string.status_ready)
                            tvSubtitle.text = if (!ssid.isNullOrEmpty()) "$ssid · Portal detected" else getString(R.string.label_auth_needed)
                            btnConnect.text = getString(R.string.action_connect)
                        } else {
                            tvTitle.text = getString(R.string.status_credentials_required)
                            tvSubtitle.text = getString(R.string.label_auth_needed)
                            btnConnect.text = getString(R.string.pref_update_credentials)
                        }
                        btnConnect.isEnabled = true
                        stopNetworkStatsSampling()
                        NetworkStatsManager.onDisconnected()
                        resetMetricsDisplay()
                    }
                    isWifiConnected -> {
                        indicatorView.setState(ConnectionIndicatorView.State.DISCONNECTED)
                        tvStatusBadge.text = "Campus network ready"
                        val neutralColor = Color.parseColor("#9CA3AF")
                        tvStatusBadge.setTextColor(neutralColor)
                        viewStatusDot.backgroundTintList = ColorStateList.valueOf(neutralColor)

                        btnConnect.visibility = View.VISIBLE
                        layoutConnectedActions.visibility = View.GONE
                        tvTitle.text = if (hasCredentials) getString(R.string.status_ready) else getString(R.string.status_not_vit)
                        tvSubtitle.text = if (!ssid.isNullOrEmpty()) ssid else getString(R.string.label_other_network)
                        btnConnect.text = getString(R.string.action_connect)
                        btnConnect.isEnabled = true
                        stopNetworkStatsSampling()
                        NetworkStatsManager.onDisconnected()
                        resetMetricsDisplay()
                    }
                    else -> {
                        indicatorView.setState(ConnectionIndicatorView.State.DISCONNECTED)
                        tvStatusBadge.text = "Offline · Ready"
                        val neutralColor = Color.parseColor("#9CA3AF")
                        tvStatusBadge.setTextColor(neutralColor)
                        viewStatusDot.backgroundTintList = ColorStateList.valueOf(neutralColor)

                        btnConnect.visibility = View.VISIBLE
                        layoutConnectedActions.visibility = View.GONE
                        tvTitle.text = getString(R.string.status_disconnected)
                        tvSubtitle.text = getString(R.string.label_wifi_disabled)
                        btnConnect.text = getString(R.string.action_connect)
                        btnConnect.isEnabled = true
                        stopNetworkStatsSampling()
                        NetworkStatsManager.onDisconnected()
                        resetMetricsDisplay()
                    }
                }
            }
        }.start()
    }

    private fun handleConnectAction() {
        if (!isAdded || isConnecting) return
        val ctx = requireContext()
        SettingsStore.setExplicitlyDisconnected(ctx, false)
        HapticHelper.click(btnConnect)

        // Handle VPN case: guide user to pause VPN
        if (NetworkDetector.isVpnActive(ctx) && !NetworkDetector.isWifiActuallyOnline(ctx)) {
            Toast.makeText(ctx, "VPN blocks campus authentication. Please pause your VPN.", Toast.LENGTH_LONG).show()
            try {
                val vpnIntent = android.content.Intent("android.net.vpn.SETTINGS").apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(vpnIntent)
            } catch (_: Exception) {
                try {
                    val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(settingsIntent)
                } catch (_: Exception) {}
            }
            return
        }

        if (!CredentialStore.hasCredentials(ctx)) {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, CredentialsFragment())
                .addToBackStack(null)
                .commit()
            return
        }

        isConnecting = true
        btnConnect.visibility = View.VISIBLE
        layoutConnectedActions.visibility = View.GONE
        btnConnect.isEnabled = false
        indicatorView.setState(ConnectionIndicatorView.State.CONNECTING)
        tvTitle.text = getString(R.string.status_connecting)
        tvSubtitle.text = "Authenticating with campus gateway…"
        tvStatusBadge.text = "Authenticating…"

        VitWifiManager.connectAndAuthenticate(ctx, object : VitWifiManager.ConnectionCallback {
            override fun onProgress(message: String) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    tvSubtitle.text = message
                    indicatorView.setState(ConnectionIndicatorView.State.AUTHENTICATING)
                }
            }

            override fun onSuccess(message: String) {
                isConnecting = false
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    indicatorView.setState(ConnectionIndicatorView.State.CONNECTED)
                    tvTitle.text = getString(R.string.status_connected)
                    val ssid = NetworkDetector.getConnectedSsid(ctx)
                    tvSubtitle.text = if (!ssid.isNullOrEmpty()) "$ssid · Online" else getString(R.string.label_internet_active)
                    val primaryColor = SettingsStore.getAccentColorHex(ctx)
                    tvStatusBadge.text = "Connected · Campus 5G"
                    tvStatusBadge.setTextColor(primaryColor)
                    viewStatusDot.backgroundTintList = ColorStateList.valueOf(primaryColor)

                    btnConnect.visibility = View.GONE
                    layoutConnectedActions.visibility = View.VISIBLE
                    btnDisconnect.isEnabled = true
                    btnReconnect.isEnabled = true

                    NetworkStatsManager.onConnected()
                    startNetworkStatsSampling()
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(message: String, isCredentialsError: Boolean) {
                isConnecting = false
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    btnConnect.visibility = View.VISIBLE
                    layoutConnectedActions.visibility = View.GONE
                    indicatorView.setState(ConnectionIndicatorView.State.ERROR)
                    val friendlyMsg = when {
                        isCredentialsError -> "Invalid registration number or password"
                        message.contains("EPERM", ignoreCase = true) || message.contains("permitted", ignoreCase = true) ->
                            "Network access restricted by device. Retrying direct connection…"
                        message.contains("timeout", ignoreCase = true) ->
                            "Portal connection timed out. Please try again"
                        message.contains("ssl", ignoreCase = true) ->
                            "Security handshake failed. Retrying HTTP connection"
                        message.isBlank() -> "Authentication failed"
                        else -> message
                    }
                    if (isCredentialsError) {
                        tvTitle.text = getString(R.string.status_invalid_credentials)
                        tvSubtitle.text = friendlyMsg
                        btnConnect.text = getString(R.string.pref_update_credentials)
                    } else {
                        tvTitle.text = getString(R.string.status_error)
                        tvSubtitle.text = friendlyMsg
                        btnConnect.text = getString(R.string.action_connect)
                    }
                    btnConnect.isEnabled = true
                    Toast.makeText(ctx, friendlyMsg, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun startNetworkStatsSampling() {
        if (isSamplingRunning) return
        isSamplingRunning = true
        handler.removeCallbacks(statsSamplerRunnable)
        handler.post(statsSamplerRunnable)
    }

    private fun stopNetworkStatsSampling() {
        isSamplingRunning = false
        handler.removeCallbacks(statsSamplerRunnable)
    }

    private fun resetMetricsDisplay() {
        val speedUnit = SettingsStore.getSpeedUnits(requireContext())
        tvSpeedDown.text = NetworkStatsManager.formatSpeed(0L, speedUnit)
        tvSpeedUp.text = NetworkStatsManager.formatSpeed(0L, speedUnit)
        histogramView.setValues(emptyList())
        tvSessionTime.text = "00:00"
        tvDataTransferred.text = "0 B"
    }
}
