package com.vitwificonnect.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.OutputStream
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class WiFiAuthenticator(private val context: Context) {

    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val webAuthenticator = PortalWebAuthenticator(context)

    interface AuthCallback {
        fun onSuccess(message: String)
        fun onFailure(message: String, status: ConnectionStatus)
    }

    companion object {
        // All-trusting SSL context for self-signed university gateway certificates
        private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        })

        val trustingSSLSocketFactory: SSLSocketFactory by lazy {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            sslContext.socketFactory
        }

        val trustingHostnameVerifier = HostnameVerifier { _, _ -> true }
    }

    fun authenticate(regNumber: String, password: String, callback: AuthCallback) {
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }
        executor.execute {
            try {
                val result = performLogin(regNumber, password)

                if (result.success) {
                    handler.post {
                        notifySystemNetworkValidated()
                        callback.onSuccess(result.message ?: "Connected to VIT WiFi")
                    }
                    return@execute
                }

                if (result.status == ConnectionStatus.Failed("invalid_credentials")) {
                    handler.post {
                        callback.onFailure("Invalid registration number or password", result.status)
                    }
                    return@execute
                }

                // Tier 3: WebView-based fallback — auto-fills and submits the portal form
                handler.post {
                    webAuthenticator.authenticateHeadless(regNumber, password, object : PortalWebAuthenticator.Callback {
                        override fun onSuccess(message: String) {
                            notifySystemNetworkValidated()
                            callback.onSuccess(message)
                        }
                        override fun onFailure(message: String) {
                            callback.onFailure(message, ConnectionStatus.Failed("failure"))
                        }
                    })
                }

            } catch (_: Exception) {
                handler.post {
                    webAuthenticator.authenticateHeadless(regNumber, password, object : PortalWebAuthenticator.Callback {
                        override fun onSuccess(message: String) {
                            notifySystemNetworkValidated()
                            callback.onSuccess(message)
                        }
                        override fun onFailure(message: String) {
                            callback.onFailure("Unable to connect to campus network", ConnectionStatus.Failed("network_error"))
                        }
                    })
                }
            }
        }
    }

    private fun performLogin(regNumber: String, password: String): AuthResult {
        val wifiNetwork = NetworkDetector.getWifiNetwork(context)

        // Priority 1: HTTP endpoint — the standard Pronto URL with query param
        val httpResult = tryLogin(LOGIN_URL_HTTP, regNumber, password, wifiNetwork)
        if (httpResult.success) return AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
        if (httpResult.status == ConnectionStatus.Failed("invalid_credentials")) {
            return AuthResult(false, ConnectionStatus.Failed("invalid_credentials"), "Invalid registration number or password")
        }

        // Priority 2: Detected redirect URL (captured during portal probe)
        val detectedUrl = NetworkDetector.lastDetectedLoginUrl
        if (!detectedUrl.isNullOrEmpty() && detectedUrl != LOGIN_URL_HTTP) {
            // Only use HTTP URLs — never retry HTTPS on a Pronto portal (causes SSL handshake failure)
            val safeUrl = if (detectedUrl.startsWith("https://", ignoreCase = true)) {
                detectedUrl.replaceFirst("https://", "http://", ignoreCase = true)
            } else detectedUrl
            val probeResult = tryLogin(safeUrl, regNumber, password, wifiNetwork)
            if (probeResult.success) return AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
            if (probeResult.status == ConnectionStatus.Failed("invalid_credentials")) {
                return AuthResult(false, ConnectionStatus.Failed("invalid_credentials"), "Invalid registration number or password")
            }
        }

        // Priority 3: Plain HTTP endpoint without query string
        val cleanUrl = "http://$PRONTO_PORTAL_HOST/cgi-bin/authlogin"
        val cleanResult = tryLogin(cleanUrl, regNumber, password, wifiNetwork)
        if (cleanResult.success) return AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
        if (cleanResult.status == ConnectionStatus.Failed("invalid_credentials")) {
            return AuthResult(false, ConnectionStatus.Failed("invalid_credentials"), "Invalid registration number or password")
        }

        // Priority 4: Gateway IP fallback (bypasses DNS resolution failures)
        val gatewayIp = NetworkDetector.getWifiGatewayIp(context)
        if (!gatewayIp.isNullOrEmpty() && gatewayIp != PRONTO_PORTAL_HOST) {
            val gatewayUrl = "http://$gatewayIp/cgi-bin/authlogin"
            val gatewayResult = tryLogin(gatewayUrl, regNumber, password, wifiNetwork)
            if (gatewayResult.success) return AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
            if (gatewayResult.status == ConnectionStatus.Failed("invalid_credentials")) {
                return AuthResult(false, ConnectionStatus.Failed("invalid_credentials"), "Invalid registration number or password")
            }
        }

        // If Wi-Fi is somehow already online, treat as success
        if (NetworkDetector.isWifiActuallyOnline(context)) {
            return AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
        }

        return AuthResult(false, ConnectionStatus.Failed("needs_web_auth"), "Pending web verification")
    }

    /**
     * Tries to login via a specific URL.
     * Uses the Wi-Fi Network object for openConnection — this is the EPERM-safe approach.
     * bindProcessToNetwork() is intentionally NOT called here (it throws EPERM with VPN active).
     */
    private fun tryLogin(urlStr: String, userId: String, password: String, wifiNetwork: Network?): AuthResult {
        // Always prefer HTTP for Pronto portal — it does not support HTTPS from client side
        val httpUrl = if (urlStr.startsWith("https://", ignoreCase = true)) {
            urlStr.replaceFirst("https://", "http://", ignoreCase = true)
        } else urlStr

        val targetUrl = try { URL(httpUrl) } catch (_: Exception) {
            return AuthResult(false, ConnectionStatus.Failed("invalid_url"), "Invalid portal URL")
        }

        // Strategy 1: Use the Wi-Fi Network object directly (EPERM-safe, bypasses VPN routing)
        if (wifiNetwork != null) {
            val result = executeLoginRequest(targetUrl, wifiNetwork, userId, password)
            if (result != null && (result.success || result.status == ConnectionStatus.Failed("invalid_credentials"))) {
                return result
            }
        }

        // Strategy 2: Unbound connection (fallback if wifiNetwork is null or openConnection fails)
        val unboundResult = executeLoginRequest(targetUrl, null, userId, password)
        if (unboundResult != null && (unboundResult.success || unboundResult.status == ConnectionStatus.Failed("invalid_credentials"))) {
            return unboundResult
        }

        return AuthResult(false, ConnectionStatus.Failed("network_error"), "Unable to reach VIT login portal")
    }

    private fun executeLoginRequest(targetUrl: URL, network: Network?, userId: String, password: String): AuthResult? {
        var connection: HttpURLConnection? = null
        return try {
            CookieHandler.setDefault(cookieManager)

            val conn = if (network != null) {
                network.openConnection(targetUrl) as HttpURLConnection
            } else {
                targetUrl.openConnection() as HttpURLConnection
            }
            connection = conn

            // Apply trust-all SSL only if somehow connecting over HTTPS (self-signed cert)
            if (conn is HttpsURLConnection) {
                conn.sslSocketFactory = trustingSSLSocketFactory
                conn.hostnameVerifier = trustingHostnameVerifier
            }

            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Host", "phc.prontonetworks.com")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.setRequestProperty("Origin", "http://$PRONTO_PORTAL_HOST")
            conn.setRequestProperty("Referer", "http://$PRONTO_PORTAL_HOST/cgi-bin/authlogin")
            conn.setRequestProperty("Connection", "close")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            // Exact canonical Pronto POST payload
            val postData = "userId=${URLEncoder.encode(userId.trim(), "UTF-8")}" +
                "&password=${URLEncoder.encode(password, "UTF-8")}" +
                "&serviceName=ProntoAuthentication" +
                "&Submit22=Login"

            conn.outputStream.use { os: OutputStream ->
                os.write(postData.toByteArray(UTF_8))
            }

            val responseCode = conn.responseCode
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val response = try { conn.inputStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
                    val responseLower = response.lowercase()

                    val hasExplicitError = listOf(
                        "invalid", "incorrect", "wrong password", "authentication failed",
                        "user not found", "account expired", "session limit",
                        "max session", "login failed", "exceeded limit"
                    ).any { it in responseLower }

                    if (hasExplicitError) {
                        AuthResult(false, ConnectionStatus.Failed("invalid_credentials"), "Invalid registration number or password")
                    } else {
                        // Notify system the Wi-Fi network is now validated
                        notifySystemNetworkValidated()

                        val hasSuccessSignature = listOf(
                            "logout", "authlogout", "successful", "welcome", "logged in", "window.location"
                        ).any { it in responseLower } || !responseLower.contains("campus wifi login")

                        Thread.sleep(600)
                        notifySystemNetworkValidated()

                        if (hasSuccessSignature || NetworkDetector.isWifiActuallyOnline(context)) {
                            AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
                        } else {
                            AuthResult(false, ConnectionStatus.Failed("needs_web_auth"), "Direct login pending verification")
                        }
                    }
                }
                HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER -> {
                    notifySystemNetworkValidated()
                    val location = conn.headerFields["Location"]?.firstOrNull()
                    if (location != null) {
                        followRedirect(location, userId, password, network)
                    } else {
                        AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
                    }
                }
                else -> AuthResult(false, ConnectionStatus.Failed("failure"), "Authentication failed (HTTP $responseCode)")
            }
        } catch (e: java.net.SocketException) {
            // EPERM, connection reset, etc. — return null so caller can try next strategy
            null
        } catch (_: SecurityException) { null }
        catch (_: javax.net.ssl.SSLHandshakeException) { null }
        catch (_: java.net.UnknownHostException) { null }
        catch (_: java.net.SocketTimeoutException) { null }
        catch (_: Exception) { null }
        finally {
            try { connection?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun followRedirect(location: String, userId: String, password: String, network: Network?): AuthResult {
        // Always use HTTP for redirect on Pronto portal
        val redirectUrl = when {
            location.startsWith("http://") || location.startsWith("https://") -> {
                if (location.startsWith("https://", ignoreCase = true)) {
                    location.replaceFirst("https://", "http://", ignoreCase = true)
                } else location
            }
            else -> "http://$PRONTO_PORTAL_HOST$location"
        }
        val targetUrl = try { URL(redirectUrl) } catch (_: Exception) {
            return AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
        }

        val res = executeRedirectRequest(targetUrl, network)
        if (res != null) return res
        val fallback = executeRedirectRequest(targetUrl, null)
        return fallback ?: AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
    }

    private fun executeRedirectRequest(targetUrl: URL, network: Network?): AuthResult? {
        var connection: HttpURLConnection? = null
        return try {
            val conn = if (network != null) {
                network.openConnection(targetUrl) as HttpURLConnection
            } else {
                targetUrl.openConnection() as HttpURLConnection
            }
            connection = conn

            if (conn is HttpsURLConnection) {
                conn.sslSocketFactory = trustingSSLSocketFactory
                conn.hostnameVerifier = trustingHostnameVerifier
            }

            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val responseCode = conn.responseCode
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val response = try { conn.inputStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
                    val responseLower = response.lowercase()

                    val hasError = listOf(
                        "invalid", "incorrect", "wrong password", "authentication failed",
                        "user not found", "account expired", "session limit", "max session"
                    ).any { it in responseLower }

                    if (hasError) {
                        AuthResult(false, ConnectionStatus.Failed("invalid_credentials"), "Invalid registration number or password")
                    } else {
                        Thread.sleep(600)
                        if (NetworkDetector.isWifiActuallyOnline(context)) {
                            AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
                        } else {
                            AuthResult(false, ConnectionStatus.Failed("needs_web_auth"), "Pending verification")
                        }
                    }
                }
                HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER ->
                    AuthResult(true, ConnectionStatus.Success, "Successfully connected to VIT WiFi")
                else ->
                    AuthResult(false, ConnectionStatus.Failed("failure"), "Redirect status HTTP $responseCode")
            }
        } catch (_: Throwable) { null }
        finally {
            try { connection?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun notifySystemNetworkValidated() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifiNetwork = NetworkDetector.getWifiNetwork(context)
            if (cm != null && wifiNetwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.reportNetworkConnectivity(wifiNetwork, true)
            }
        } catch (_: Exception) {}
    }

    fun cancel() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(300, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
