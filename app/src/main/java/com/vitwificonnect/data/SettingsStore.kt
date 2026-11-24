package com.vitwificonnect.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * SettingsStore — plain SharedPreferences only, zero EncryptedSharedPreferences.
 * Highly robust, zero crash/corruption risk across all Android versions and OEM skins.
 */
object SettingsStore {

    private const val PREFS_NAME = "vit_wifi_settings_plain"

    private const val KEY_AUTO_LOGIN = "pref_auto_login"
    private const val KEY_LAST_AUTH_SUCCESS = "pref_last_auth_success"
    private const val KEY_LAST_AUTH_FAIL = "pref_last_auth_fail"
    private const val KEY_AUTH_FAIL_COUNT = "pref_auth_fail_count"
    private const val KEY_THEME = "pref_theme"
    private const val KEY_ACCENT_COLOR = "pref_accent_color"
    private const val KEY_DYNAMIC_COLORS = "pref_dynamic_colors"
    private const val KEY_CHART_COLORS = "pref_chart_colors"
    private const val KEY_HAPTIC = "pref_haptic_feedback"
    private const val KEY_SPEED_UNITS = "pref_speed_units"
    private const val KEY_ONBOARDING_DONE = "pref_onboarding_done"
    private const val KEY_NO_BACKGROUND = "pref_no_background"
    private const val KEY_EXPLICIT_DISCONNECT = "pref_explicit_disconnect"

    // Curated iOS / Apple Music Grade Color Palettes
    val PALETTES = listOf(
        Palette("emerald", "Emerald Aurora", "#10B981", "#059669", "#34D399", "Campus Neo Emerald"),
        Palette("ocean", "Midnight Ocean", "#3B82F6", "#1D4ED8", "#60A5FA", "Apple Deep Azure"),
        Palette("sunset", "Sunset Flare", "#F97316", "#C2410C", "#FB923C", "Apple Music Sunset Amber"),
        Palette("violet", "Royal Amethyst", "#8B5CF6", "#6D28D9", "#A78BFA", "Royal Velvet Purple"),
        Palette("rose", "Rose Nebula", "#F43F5E", "#BE123C", "#FB7185", "Neon Rose & Magenta"),
        Palette("titanium", "Cyber Titanium", "#E2E8F0", "#94A3B8", "#FFFFFF", "Luminescent Platinum Slate")
    )

    data class Palette(
        val id: String,
        val name: String,
        val hex: String,
        val secondaryHex: String,
        val glowHex: String,
        val description: String
    )

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun requirePrefs(context: Context): SharedPreferences {
        return prefs ?: run {
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            p
        }
    }

    // Onboarding
    fun isOnboardingCompleted(context: Context): Boolean =
        try { requirePrefs(context).getBoolean(KEY_ONBOARDING_DONE, false) } catch (_: Exception) { false }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        try { requirePrefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply() } catch (_: Exception) {}
    }

    // Auto-login
    fun isAutoLoginEnabled(context: Context): Boolean =
        try { requirePrefs(context).getBoolean(KEY_AUTO_LOGIN, true) } catch (_: Exception) { true }

    fun setAutoLogin(context: Context, enabled: Boolean) {
        try { requirePrefs(context).edit().putBoolean(KEY_AUTO_LOGIN, enabled).apply() } catch (_: Exception) {}
    }

    // Zero-drain / No background activity
    fun isNoBackgroundEnabled(context: Context): Boolean =
        try { requirePrefs(context).getBoolean(KEY_NO_BACKGROUND, false) } catch (_: Exception) { false }

    fun setNoBackground(context: Context, enabled: Boolean) {
        try { requirePrefs(context).edit().putBoolean(KEY_NO_BACKGROUND, enabled).apply() } catch (_: Exception) {}
    }

    // Theme (system, light, dark)
    fun getTheme(context: Context): String =
        try { requirePrefs(context).getString(KEY_THEME, "system") ?: "system" } catch (_: Exception) { "system" }

    fun setTheme(context: Context, theme: String) {
        try { requirePrefs(context).edit().putString(KEY_THEME, theme).apply() } catch (_: Exception) {}
    }

    // Accent Color Palette
    fun getAccentColor(context: Context): String =
        try { requirePrefs(context).getString(KEY_ACCENT_COLOR, "emerald") ?: "emerald" } catch (_: Exception) { "emerald" }

    fun setAccentColor(context: Context, accent: String) {
        try { requirePrefs(context).edit().putString(KEY_ACCENT_COLOR, accent).apply() } catch (_: Exception) {}
    }

    fun getCurrentPalette(context: Context): Palette {
        val id = getAccentColor(context)
        return PALETTES.find { it.id == id } ?: PALETTES.first()
    }

    fun getAccentColorHex(context: Context): Int {
        val palette = getCurrentPalette(context)
        return try {
            Color.parseColor(palette.hex)
        } catch (_: Exception) {
            Color.parseColor("#10B981")
        }
    }

    fun getSecondaryColorHex(context: Context): Int {
        val palette = getCurrentPalette(context)
        return try {
            Color.parseColor(palette.secondaryHex)
        } catch (_: Exception) {
            Color.parseColor("#059669")
        }
    }

    fun getGlowColorHex(context: Context): Int {
        val palette = getCurrentPalette(context)
        return try {
            Color.parseColor(palette.glowHex)
        } catch (_: Exception) {
            Color.parseColor("#34D399")
        }
    }

    fun getAccentColorName(context: Context): String {
        return getCurrentPalette(context).name
    }

    // Dynamic Colors (Material You)
    fun isDynamicColorsEnabled(context: Context): Boolean =
        try { requirePrefs(context).getBoolean(KEY_DYNAMIC_COLORS, false) } catch (_: Exception) { false }

    fun setDynamicColors(context: Context, enabled: Boolean) {
        try { requirePrefs(context).edit().putBoolean(KEY_DYNAMIC_COLORS, enabled).apply() } catch (_: Exception) {}
    }

    // Chart Bar Colors
    fun getChartColorMode(context: Context): String =
        try { requirePrefs(context).getString(KEY_CHART_COLORS, "dynamic") ?: "dynamic" } catch (_: Exception) { "dynamic" }

    fun setChartColorMode(context: Context, mode: String) {
        try { requirePrefs(context).edit().putString(KEY_CHART_COLORS, mode).apply() } catch (_: Exception) {}
    }

    // Haptic feedback
    fun isHapticFeedbackEnabled(context: Context): Boolean =
        try { requirePrefs(context).getBoolean(KEY_HAPTIC, true) } catch (_: Exception) { true }

    fun setHapticFeedback(context: Context, enabled: Boolean) {
        try { requirePrefs(context).edit().putBoolean(KEY_HAPTIC, enabled).apply() } catch (_: Exception) {}
    }

    // Speed units (bps, kbps, mbps)
    fun getSpeedUnits(context: Context): String =
        try { requirePrefs(context).getString(KEY_SPEED_UNITS, "bps") ?: "bps" } catch (_: Exception) { "bps" }

    fun setSpeedUnits(context: Context, units: String) {
        try { requirePrefs(context).edit().putString(KEY_SPEED_UNITS, units).apply() } catch (_: Exception) {}
    }

    // Last auth success time (epoch millis)
    fun getLastAuthSuccessTime(context: Context): Long =
        try { requirePrefs(context).getLong(KEY_LAST_AUTH_SUCCESS, 0L) } catch (_: Exception) { 0L }

    fun setLastAuthSuccessTime(context: Context, time: Long) {
        try { requirePrefs(context).edit().putLong(KEY_LAST_AUTH_SUCCESS, time).apply() } catch (_: Exception) {}
    }

    fun getLastAuthFailTime(context: Context): Long =
        try { requirePrefs(context).getLong(KEY_LAST_AUTH_FAIL, 0L) } catch (_: Exception) { 0L }

    fun setLastAuthFailTime(context: Context, time: Long) {
        try { requirePrefs(context).edit().putLong(KEY_LAST_AUTH_FAIL, time).apply() } catch (_: Exception) {}
    }

    fun getAuthFailCount(context: Context): Int =
        try { requirePrefs(context).getInt(KEY_AUTH_FAIL_COUNT, 0) } catch (_: Exception) { 0 }

    fun setAuthFailCount(context: Context, count: Int) {
        try { requirePrefs(context).edit().putInt(KEY_AUTH_FAIL_COUNT, count).apply() } catch (_: Exception) {}
    }

    fun shouldAllowAuthAttempt(context: Context): Boolean {
        val failCount = getAuthFailCount(context)
        val lastFail = getLastAuthFailTime(context)
        val now = System.currentTimeMillis()
        if (failCount >= 3 && now - lastFail < 3 * 60 * 1000) return false
        if (failCount >= 5 && now - lastFail < 10 * 60 * 1000) return false
        return true
    }

    fun onAuthSuccess(context: Context) {
        setLastAuthSuccessTime(context, System.currentTimeMillis())
        setAuthFailCount(context, 0)
    }

    fun onAuthFail(context: Context) {
        setLastAuthFailTime(context, System.currentTimeMillis())
        val current = getAuthFailCount(context)
        setAuthFailCount(context, current + 1)
    }

    // User explicitly disconnected (prevents background auto-login until user clicks connect again)
    fun isExplicitlyDisconnected(context: Context): Boolean =
        try { requirePrefs(context).getBoolean(KEY_EXPLICIT_DISCONNECT, false) } catch (_: Exception) { false }

    fun setExplicitlyDisconnected(context: Context, disconnected: Boolean) {
        try { requirePrefs(context).edit().putBoolean(KEY_EXPLICIT_DISCONNECT, disconnected).apply() } catch (_: Exception) {}
    }

    fun isRecentlyAuthenticated(context: Context): Boolean {
        val lastSuccess = getLastAuthSuccessTime(context)
        if (lastSuccess == 0L) return false
        return System.currentTimeMillis() - lastSuccess < 30 * 60 * 1000
    }
}
