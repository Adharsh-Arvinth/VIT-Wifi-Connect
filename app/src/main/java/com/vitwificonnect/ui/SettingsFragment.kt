package com.vitwificonnect.ui

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vitwificonnect.R
import com.vitwificonnect.data.CredentialStore
import com.vitwificonnect.data.NetworkStatsManager
import com.vitwificonnect.data.SettingsStore
import com.vitwificonnect.data.VitWifiManager
import com.vitwificonnect.tile.VitWifiTileService
import com.vitwificonnect.util.HapticHelper
import com.vitwificonnect.widget.VitWifiWidgetProvider
import java.util.Locale

class SettingsFragment : Fragment() {

    private lateinit var tvCredentialsSummary: TextView
    private lateinit var tvThemeSummary: TextView
    private lateinit var tvAccentSummary: TextView
    private lateinit var tvChartSummary: TextView
    private lateinit var tvSpeedUnitsSummary: TextView

    private lateinit var switchAutoLogin: SwitchCompat
    private lateinit var switchNoBackground: SwitchCompat
    private lateinit var switchDynamicColors: SwitchCompat
    private lateinit var switchHaptic: SwitchCompat

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        val btnBack: ImageButton = view.findViewById(R.id.btn_settings_back)
        switchAutoLogin = view.findViewById(R.id.switch_settings_auto_login)
        switchNoBackground = view.findViewById(R.id.switch_no_background)
        switchDynamicColors = view.findViewById(R.id.switch_dynamic_colors)
        switchHaptic = view.findViewById(R.id.switch_haptic_feedback)

        val rowSessionLogout: View = view.findViewById(R.id.row_session_logout)
        val rowUpdateCredentials: View = view.findViewById(R.id.row_update_credentials)
        val rowClearCredentials: View = view.findViewById(R.id.row_clear_credentials)
        tvCredentialsSummary = view.findViewById(R.id.tv_credentials_summary)

        val rowTheme: View = view.findViewById(R.id.row_theme)
        tvThemeSummary = view.findViewById(R.id.tv_theme_summary)

        val rowAccent: View = view.findViewById(R.id.row_accent_color)
        tvAccentSummary = view.findViewById(R.id.tv_accent_color_summary)

        val rowChartColors: View = view.findViewById(R.id.row_chart_colors)
        tvChartSummary = view.findViewById(R.id.tv_chart_colors_summary)

        val rowMacGuide: View = view.findViewById(R.id.row_mac_guide)

        val rowSpeedUnits: View = view.findViewById(R.id.row_speed_units)
        tvSpeedUnitsSummary = view.findViewById(R.id.tv_speed_units_summary)

        val rowClearStats: View = view.findViewById(R.id.row_clear_stats)
        val rowTileShortcut: View = view.findViewById(R.id.row_tile_shortcut)
        val rowWidgetShortcut: View = view.findViewById(R.id.row_widget_shortcut)

        // Bind initial values
        switchAutoLogin.isChecked = SettingsStore.isAutoLoginEnabled(ctx)
        switchNoBackground.isChecked = SettingsStore.isNoBackgroundEnabled(ctx)
        switchDynamicColors.isChecked = SettingsStore.isDynamicColorsEnabled(ctx)
        switchHaptic.isChecked = SettingsStore.isHapticFeedbackEnabled(ctx)

        updateDisplaySummaries()
        applyAccentThemeToSettings()

        // 1. Back
        btnBack.setOnClickListener {
            HapticHelper.click(btnBack)
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 2. Auto-login switch
        switchAutoLogin.setOnCheckedChangeListener { buttonView, isChecked ->
            HapticHelper.toggle(buttonView)
            SettingsStore.setAutoLogin(ctx, isChecked)
        }

        // 3. Zero-Drain Mode (No Background) switch
        switchNoBackground.setOnCheckedChangeListener { buttonView, isChecked ->
            HapticHelper.toggle(buttonView)
            SettingsStore.setNoBackground(ctx, isChecked)
            if (isChecked) {
                Toast.makeText(ctx, "Background activity disabled when connected", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Session Logout
        rowSessionLogout.setOnClickListener {
            HapticHelper.click(rowSessionLogout)
            showLogoutDialog()
        }

        // 5. Update credentials
        rowUpdateCredentials.setOnClickListener {
            HapticHelper.click(rowUpdateCredentials)
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, CredentialsFragment())
                .addToBackStack(null)
                .commit()
        }

        // 6. Clear credentials
        rowClearCredentials.setOnClickListener {
            HapticHelper.click(rowClearCredentials)
            showClearCredentialsDialog()
        }

        // 7. Theme
        rowTheme.setOnClickListener {
            HapticHelper.click(rowTheme)
            showThemeDialog()
        }

        // 8. Color Palette
        rowAccent.setOnClickListener {
            HapticHelper.click(rowAccent)
            showAccentDialog()
        }

        // 9. Dynamic Colors switch
        switchDynamicColors.setOnCheckedChangeListener { buttonView, isChecked ->
            HapticHelper.toggle(buttonView)
            SettingsStore.setDynamicColors(ctx, isChecked)
        }

        // 10. Chart Bar Colors
        rowChartColors.setOnClickListener {
            HapticHelper.click(rowChartColors)
            showChartColorsDialog()
        }

        // 11. Haptic Feedback switch
        switchHaptic.setOnCheckedChangeListener { buttonView, isChecked ->
            HapticHelper.toggle(buttonView)
            SettingsStore.setHapticFeedback(ctx, isChecked)
        }

        // 12. MAC Randomization Guide
        rowMacGuide.setOnClickListener {
            HapticHelper.click(rowMacGuide)
            showMacGuideDialog()
        }

        // 13. Speed Units
        rowSpeedUnits.setOnClickListener {
            HapticHelper.click(rowSpeedUnits)
            showSpeedUnitsDialog()
        }

        // 14. Clear Stats
        rowClearStats.setOnClickListener {
            HapticHelper.click(rowClearStats)
            showClearStatsDialog()
        }

        // 15. Control Center Tile Shortcut
        rowTileShortcut.setOnClickListener {
            HapticHelper.click(rowTileShortcut)
            requestAddTileOrShowGuide()
        }

        // 16. Home Screen Widget Shortcut
        rowWidgetShortcut.setOnClickListener {
            HapticHelper.click(rowWidgetShortcut)
            requestPinWidgetOrShowGuide()
        }
    }

    override fun onResume() {
        super.onResume()
        updateDisplaySummaries()
    }

    private fun updateDisplaySummaries() {
        if (!isAdded) return
        val ctx = requireContext()

        val reg = CredentialStore.getRegNumber(ctx)
        if (!reg.isNullOrBlank()) {
            tvCredentialsSummary.text = reg
        } else {
            tvCredentialsSummary.text = getString(R.string.status_credentials_required)
        }

        val themeKey = SettingsStore.getTheme(ctx)
        tvThemeSummary.text = when (themeKey) {
            "light" -> "Light"
            "dark" -> "Dark"
            else -> "System Default"
        }

        tvAccentSummary.text = SettingsStore.getAccentColorName(ctx)

        val chartMode = SettingsStore.getChartColorMode(ctx)
        tvChartSummary.text = if (chartMode == "monochrome") "Monochrome" else "Material Dynamic"

        val speedUnit = SettingsStore.getSpeedUnits(ctx)
        tvSpeedUnitsSummary.text = when (speedUnit.lowercase(Locale.ROOT)) {
            "kbps" -> "Kilobits per second (Kbps)"
            "mbps" -> "Megabits per second (Mbps)"
            else -> "Bits per second (bps)"
        }
    }

    private fun applyAccentThemeToSettings() {
        val ctx = context ?: return
        try {
            val accentColor = SettingsStore.getAccentColorHex(ctx)
            applySwitchTheme(switchAutoLogin, accentColor)
            applySwitchTheme(switchNoBackground, accentColor)
            applySwitchTheme(switchDynamicColors, accentColor)
            applySwitchTheme(switchHaptic, accentColor)
        } catch (_: Exception) {}
    }

    private fun applySwitchTheme(switch: SwitchCompat, accentColor: Int) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(
            accentColor,
            android.graphics.Color.parseColor("#71717A")
        )
        val trackColors = intArrayOf(
            android.graphics.Color.argb(100, android.graphics.Color.red(accentColor), android.graphics.Color.green(accentColor), android.graphics.Color.blue(accentColor)),
            android.graphics.Color.argb(50, 113, 113, 122)
        )
        switch.thumbTintList = android.content.res.ColorStateList(states, thumbColors)
        switch.trackTintList = android.content.res.ColorStateList(states, trackColors)
    }

    private fun showLogoutDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.pref_session_logout)
            .setMessage("Do you want to logout from the campus Wi-Fi gateway? This will disconnect your device from Pronto and release your MAC address.")
            .setPositiveButton("Logout") { _, _ ->
                VitWifiManager.logout(ctx, object : VitWifiManager.LogoutCallback {
                    override fun onSuccess(message: String) {
                        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                        HapticHelper.success(ctx)
                    }

                    override fun onFailure(message: String) {
                        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                    }
                })
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showMacGuideDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.dialog_mac_guide_title)
            .setMessage(R.string.dialog_mac_guide_message)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    private fun showThemeDialog() {
        val ctx = requireContext()
        val entries = arrayOf("System Default", "Light", "Dark")
        val values = arrayOf("system", "light", "dark")
        val current = SettingsStore.getTheme(ctx)
        val currentIndex = values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.pref_theme)
            .setSingleChoiceItems(entries, currentIndex) { dialog, which ->
                val selected = values[which]
                SettingsStore.setTheme(ctx, selected)
                tvThemeSummary.text = entries[which]
                HapticHelper.click(requireView())
                applyThemeMode(selected)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun applyThemeMode(theme: String) {
        when (theme) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun showAccentDialog() {
        val ctx = requireContext()
        val palettes = SettingsStore.PALETTES
        val entries = palettes.map { "${it.name}\n${it.description}" }.toTypedArray()
        val currentId = SettingsStore.getAccentColor(ctx)
        val currentIndex = palettes.indexOfFirst { it.id == currentId }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.pref_accent_color)
            .setSingleChoiceItems(entries, currentIndex) { dialog, which ->
                val selected = palettes[which]
                SettingsStore.setAccentColor(ctx, selected.id)
                tvAccentSummary.text = selected.name
                applyAccentThemeToSettings()
                HapticHelper.click(requireView())
                Toast.makeText(ctx, "Applied ${selected.name} palette", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showChartColorsDialog() {
        val ctx = requireContext()
        val entries = arrayOf("Material Dynamic", "Monochrome")
        val values = arrayOf("dynamic", "monochrome")
        val current = SettingsStore.getChartColorMode(ctx)
        val currentIndex = values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.pref_chart_colors)
            .setSingleChoiceItems(entries, currentIndex) { dialog, which ->
                val selected = values[which]
                SettingsStore.setChartColorMode(ctx, selected)
                tvChartSummary.text = entries[which]
                HapticHelper.click(requireView())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showSpeedUnitsDialog() {
        val ctx = requireContext()
        val entries = arrayOf("Bits per second (bps)", "Kilobits per second (Kbps)", "Megabits per second (Mbps)")
        val values = arrayOf("bps", "kbps", "mbps")
        val current = SettingsStore.getSpeedUnits(ctx)
        val currentIndex = values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.pref_speed_units)
            .setSingleChoiceItems(entries, currentIndex) { dialog, which ->
                val selected = values[which]
                SettingsStore.setSpeedUnits(ctx, selected)
                tvSpeedUnitsSummary.text = entries[which]
                HapticHelper.click(requireView())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showClearStatsDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.dialog_clear_stats_title)
            .setMessage(R.string.dialog_clear_stats_message)
            .setPositiveButton(R.string.dialog_clear_stats_confirm) { _, _ ->
                NetworkStatsManager.resetStats()
                HapticHelper.success(ctx)
                Toast.makeText(ctx, getString(R.string.msg_stats_cleared), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun requestAddTileOrShowGuide() {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val statusBarManager = ctx.getSystemService(StatusBarManager::class.java)
                val component = ComponentName(ctx, VitWifiTileService::class.java)
                val icon = Icon.createWithResource(ctx, R.drawable.ic_wifi_connected)
                statusBarManager?.requestAddTileService(
                    component,
                    getString(R.string.tile_label),
                    icon,
                    ContextCompat.getMainExecutor(ctx)
                ) { result ->
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                        Toast.makeText(ctx, getString(R.string.msg_tile_requested), Toast.LENGTH_SHORT).show()
                    } else if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        Toast.makeText(ctx, "Tile is already in Control Center", Toast.LENGTH_SHORT).show()
                    }
                }
                return
            } catch (_: Exception) {}
        }

        // Show step by step guide dialog
        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.dialog_tile_guide_title)
            .setMessage(R.string.dialog_tile_guide_message)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    private fun requestPinWidgetOrShowGuide() {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val appWidgetManager = ctx.getSystemService(AppWidgetManager::class.java)
                val component = ComponentName(ctx, VitWifiWidgetProvider::class.java)
                if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                    appWidgetManager.requestPinAppWidget(component, null, null)
                    Toast.makeText(ctx, getString(R.string.msg_widget_requested), Toast.LENGTH_SHORT).show()
                    return
                }
            } catch (_: Exception) {}
        }

        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.dialog_widget_guide_title)
            .setMessage(R.string.dialog_widget_guide_message)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    private fun showClearCredentialsDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx, R.style.VitDialogTheme)
            .setTitle(R.string.dialog_clear_credentials_title)
            .setMessage(R.string.dialog_clear_credentials_message)
            .setPositiveButton(R.string.dialog_clear_credentials_confirm) { _, _ ->
                CredentialStore.clearCredentials(ctx)
                updateDisplaySummaries()
                HapticHelper.success(ctx)
                Toast.makeText(ctx, getString(R.string.msg_credentials_cleared), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
