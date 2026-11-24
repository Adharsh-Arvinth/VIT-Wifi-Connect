package com.vitwificonnect.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.vitwificonnect.R
import com.vitwificonnect.data.CredentialStore
import com.vitwificonnect.data.NetworkDetector
import com.vitwificonnect.data.SettingsStore
import com.vitwificonnect.data.VitWifiManager

class MainActivity : AppCompatActivity() {

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE
    )

    private val REQUEST_CODE_PERMISSIONS = 1001

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SettingsStore.init(this)
        CredentialStore.init(this)

        applyConfiguredTheme()

        // Enable ultra-smooth 120Hz / 120 FPS display mode on supported hardware
        enable120HzRefreshRate()

        // Edge-to-edge layout
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        val container = findViewById<android.view.View>(R.id.fragment_container)
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = insets.top, bottom = insets.bottom)
            windowInsets
        }

        if (allPermissionsGranted()) {
            navigateInitialFragment()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setupNetworkCallback()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is HomeFragment) {
            currentFragment.refreshNetworkState()
        }
    }

    private fun enable120HzRefreshRate() {
        try {
            val params = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                params.preferredRefreshRate = 120.0f
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = display
                if (display != null) {
                    val highMode = display.supportedModes
                        .filter { it.refreshRate >= 119.0f }
                        .maxByOrNull { it.refreshRate }
                    if (highMode != null) {
                        params.preferredDisplayModeId = highMode.modeId
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val display = windowManager.defaultDisplay
                val highMode = display.supportedModes
                    .filter { it.refreshRate >= 119.0f }
                    .maxByOrNull { it.refreshRate }
                if (highMode != null) {
                    params.preferredDisplayModeId = highMode.modeId
                }
            }
            window.attributes = params
        } catch (_: Exception) {}
    }

    private fun applyConfiguredTheme() {
        val theme = SettingsStore.getTheme(this)
        when (theme) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                    this,
                    "WiFi and internet permissions are required for campus authentication",
                    Toast.LENGTH_SHORT
                ).show()
            }
            navigateInitialFragment()
        }
    }

    private fun navigateInitialFragment() {
        val needsOnboarding = !SettingsStore.isOnboardingCompleted(this) && !CredentialStore.hasCredentials(this)
        val fragment = if (needsOnboarding) OnboardingFragment() else HomeFragment()

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun setupNetworkCallback() {
        if (SettingsStore.isNoBackgroundEnabled(this)) return

        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread {
                        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                        if (currentFragment is HomeFragment) {
                            currentFragment.refreshNetworkState()
                        }
                        checkAutoLogin()
                    }
                }

                override fun onLost(network: Network) {
                    runOnUiThread {
                        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                        if (currentFragment is HomeFragment) {
                            currentFragment.refreshNetworkState()
                        }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    runOnUiThread {
                        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                        if (currentFragment is HomeFragment) {
                            currentFragment.refreshNetworkState()
                        }
                    }
                }
            }

            networkCallback?.let { connectivityManager?.registerNetworkCallback(request, it) }
        } catch (_: Exception) {}
    }

    /**
     * Highly guarded auto-login:
     * Never triggers if:
     * 1. Already genuinely online.
     * 2. Already recently authenticated within 30 min cooldown window.
     * 3. No background mode is active.
     * 4. Too many failed attempts.
     */
    private fun checkAutoLogin() {
        val ctx = this
        if (SettingsStore.isExplicitlyDisconnected(ctx)) return
        if (!SettingsStore.isAutoLoginEnabled(ctx)) return
        if (SettingsStore.isNoBackgroundEnabled(ctx)) return
        if (!CredentialStore.hasCredentials(ctx)) return
        if (!SettingsStore.shouldAllowAuthAttempt(ctx)) return
        if (SettingsStore.isRecentlyAuthenticated(ctx)) return

        Thread {
            // Confirm we actually lack internet on Wi-Fi before initiating login
            if (NetworkDetector.isWifiActuallyOnline(ctx)) return@Thread
            if (!NetworkDetector.isVitNetwork(ctx)) return@Thread

            runOnUiThread {
                VitWifiManager.connectAndAuthenticate(ctx, object : VitWifiManager.ConnectionCallback {
                    override fun onProgress(message: String) {}

                    override fun onSuccess(message: String) {
                        runOnUiThread {
                            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                            if (currentFragment is HomeFragment) {
                                currentFragment.refreshNetworkState()
                            }
                            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(message: String, isCredentialsError: Boolean) {
                        runOnUiThread {
                            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                            if (currentFragment is HomeFragment) {
                                currentFragment.refreshNetworkState()
                            }
                        }
                    }
                })
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            try { connectivityManager?.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
    }
}
