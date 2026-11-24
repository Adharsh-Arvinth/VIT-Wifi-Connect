package com.vitwificonnect.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

/**
 * CredentialStore
 *
 * Provides rock-solid, zero-drop local storage for campus Wi-Fi credentials.
 * Uses atomic synchronous commits (.commit()) to ensure credentials are fully persisted
 * to flash storage before returning, eliminating silent drops or keystore corruption.
 */
object CredentialStore {

    private const val PREFS_NAME_V2 = "vit_wifi_credentials_v2"
    private const val PREFS_NAME_LEGACY = "vit_wifi_credentials"
    private const val KEY_REG_NUMBER = "reg_number"
    private const val KEY_PASSWORD = "password"

    // In-memory cache for instant retrieval without disk I/O
    @Volatile
    private var cachedReg: String? = null
    @Volatile
    private var cachedPass: String? = null

    fun init(context: Context) {
        // Warm up cache on app launch
        if (cachedReg == null || cachedPass == null) {
            cachedReg = readReg(context)
            cachedPass = readPassword(context)
        }
    }

    fun saveCredentials(context: Context, regNumber: String, password: String): Boolean {
        val cleanReg = regNumber.trim().uppercase()
        val cleanPass = password.trim()
        if (cleanReg.isBlank() || cleanPass.isBlank()) return false

        // Update memory cache immediately
        cachedReg = cleanReg
        cachedPass = cleanPass

        var success = false
        try {
            // Write to primary V2 storage with synchronous commit
            val spV2 = context.getSharedPreferences(PREFS_NAME_V2, Context.MODE_PRIVATE)
            val committedV2 = spV2.edit()
                .putString(KEY_REG_NUMBER, cleanReg)
                .putString(KEY_PASSWORD, encode(cleanPass))
                .commit()

            // Also mirror to legacy storage for multi-version resilience
            val spLegacy = context.getSharedPreferences(PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
            val committedLegacy = spLegacy.edit()
                .putString(KEY_REG_NUMBER, cleanReg)
                .putString(KEY_PASSWORD, cleanPass)
                .commit()

            success = committedV2 || committedLegacy
        } catch (_: Exception) {
            success = false
        }

        return success && hasCredentials(context)
    }

    fun getRegNumber(context: Context): String? {
        cachedReg?.let { if (it.isNotBlank()) return it }
        val reg = readReg(context)
        if (!reg.isNullOrBlank()) {
            cachedReg = reg
            return reg
        }
        return null
    }

    fun getPassword(context: Context): String? {
        cachedPass?.let { if (it.isNotBlank()) return it }
        val pass = readPassword(context)
        if (!pass.isNullOrBlank()) {
            cachedPass = pass
            return pass
        }
        return null
    }

    fun hasCredentials(context: Context): Boolean {
        val reg = getRegNumber(context)
        val pwd = getPassword(context)
        return !reg.isNullOrBlank() && !pwd.isNullOrBlank()
    }

    fun clearCredentials(context: Context) {
        cachedReg = null
        cachedPass = null
        try {
            context.getSharedPreferences(PREFS_NAME_V2, Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences(PREFS_NAME_LEGACY, Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Exception) {}
    }

    private fun readReg(context: Context): String? {
        // Try V2 storage first
        try {
            val spV2 = context.getSharedPreferences(PREFS_NAME_V2, Context.MODE_PRIVATE)
            val reg = spV2.getString(KEY_REG_NUMBER, null)?.trim()?.uppercase()
            if (!reg.isNullOrBlank()) return reg
        } catch (_: Exception) {}

        // Fall back to legacy storage
        try {
            val spLegacy = context.getSharedPreferences(PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
            val reg = spLegacy.getString(KEY_REG_NUMBER, null)?.trim()?.uppercase()
            if (!reg.isNullOrBlank()) return reg
        } catch (_: Exception) {}

        return null
    }

    private fun readPassword(context: Context): String? {
        // Try V2 storage first
        try {
            val spV2 = context.getSharedPreferences(PREFS_NAME_V2, Context.MODE_PRIVATE)
            val encoded = spV2.getString(KEY_PASSWORD, null)
            if (!encoded.isNullOrBlank()) {
                val decoded = decode(encoded)
                if (decoded.isNotBlank()) return decoded
            }
        } catch (_: Exception) {}

        // Fall back to legacy storage
        try {
            val spLegacy = context.getSharedPreferences(PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
            val raw = spLegacy.getString(KEY_PASSWORD, null)
            if (!raw.isNullOrBlank()) return raw
        } catch (_: Exception) {}

        return null
    }

    private fun encode(plain: String): String {
        return try {
            val bytes = plain.toByteArray(Charsets.UTF_8)
            val masked = ByteArray(bytes.size)
            for (i in bytes.indices) {
                masked[i] = (bytes[i].toInt() xor 0x5A).toByte()
            }
            Base64.encodeToString(masked, Base64.NO_WRAP)
        } catch (_: Exception) {
            plain
        }
    }

    private fun decode(encoded: String): String {
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val unmasked = ByteArray(bytes.size)
            for (i in bytes.indices) {
                unmasked[i] = (bytes[i].toInt() xor 0x5A).toByte()
            }
            String(unmasked, Charsets.UTF_8)
        } catch (_: Exception) {
            encoded
        }
    }
}
