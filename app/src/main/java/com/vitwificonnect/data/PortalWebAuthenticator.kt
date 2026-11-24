package com.vitwificonnect.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/**
 * PortalWebAuthenticator
 *
 * Fallback authenticator using an invisible WebView.
 * 1. Binds the WebView to Wi-Fi network (bypasses captive portal routing).
 * 2. Dismisses the "IMPORTANT INFO" modal automatically.
 * 3. Auto-fills username and password in the DOM.
 * 4. Clicks Login button and polls for connectivity confirmation.
 * 5. Bypasses SSL certificate errors (self-signed gateway certs).
 */
class PortalWebAuthenticator(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var isCompleted = false
    private var timeoutRunnable: Runnable? = null

    interface Callback {
        fun onSuccess(message: String)
        fun onFailure(message: String)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun authenticateHeadless(regNumber: String, pass: String, callback: Callback) {
        handler.post {
            try {
                isCompleted = false
                // Bind process to Wi-Fi so the WebView (Chromium) routes directly to the campus gateway
                // This can silently fail (EPERM) if VPN is active — that's acceptable since
                // the WebView still has a path to the portal via the system's routing tables
                NetworkDetector.bindToWifi(context)

                val wv = WebView(context.applicationContext)
                webView = wv

                val settings = wv.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    settings.safeBrowsingEnabled = false
                }

                // 15-second watchdog — if portal hasn't responded, check if we're online anyway
                val timeout = Runnable {
                    if (!isCompleted) {
                        isCompleted = true
                        cleanup()
                        if (NetworkDetector.isWifiActuallyOnline(context)) {
                            notifySystemNetworkValidated()
                            callback.onSuccess("Connected to VIT WiFi")
                        } else {
                            callback.onFailure("Portal timed out. Please check your credentials and try again.")
                        }
                    }
                }
                timeoutRunnable = timeout
                handler.postDelayed(timeout, 15000L)

                // Auto-dismiss any JS alerts/confirms/prompts from the portal page
                wv.webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                        result?.confirm(defaultValue ?: "")
                        return true
                    }
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        // Proceed past self-signed certificate errors on the university gateway
                        handler?.proceed()
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // If navigation goes away from the pronto portal, we're authenticated
                        if (checkIfRedirectedAway(url, callback)) return
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (isCompleted) return
                        if (checkIfRedirectedAway(url, callback)) return
                        // Portal page loaded — inject autofill + submit script
                        injectAutofillScript(view, regNumber, pass, callback)
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        // On network error, check if Wi-Fi came online anyway (race condition)
                        if (!isCompleted) {
                            Thread {
                                if (NetworkDetector.isWifiActuallyOnline(context)) {
                                    handler.post {
                                        if (!isCompleted) {
                                            isCompleted = true
                                            cleanup()
                                            notifySystemNetworkValidated()
                                            callback.onSuccess("Connected to VIT WiFi")
                                        }
                                    }
                                }
                            }.start()
                        }
                    }
                }

                // Load the portal URL — use detected redirect URL or standard HTTP endpoint
                val targetUrl = NetworkDetector.lastDetectedLoginUrl?.let { url ->
                    // Always use HTTP (not HTTPS) for Pronto portal
                    if (url.startsWith("https://", ignoreCase = true)) {
                        url.replaceFirst("https://", "http://", ignoreCase = true)
                    } else url
                } ?: LOGIN_URL_HTTP
                wv.loadUrl(targetUrl)

            } catch (e: Exception) {
                if (!isCompleted) {
                    isCompleted = true
                    cleanup()
                    callback.onFailure("Failed to initialize web authenticator: ${e.message}")
                }
            }
        }
    }

    /**
     * Returns true if the current URL is not the Pronto portal (meaning we've been redirected post-auth).
     */
    private fun checkIfRedirectedAway(url: String?, callback: Callback): Boolean {
        if (url != null && url != "about:blank" &&
            !url.contains("phc.prontonetworks.com", ignoreCase = true) &&
            !url.contains("authlogin", ignoreCase = true) &&
            !url.startsWith("data:")) {
            if (!isCompleted) {
                isCompleted = true
                cleanup()
                notifySystemNetworkValidated()
                callback.onSuccess("Connected to VIT WiFi")
                return true
            }
        }
        return false
    }

    private fun injectAutofillScript(view: WebView?, regNumber: String, pass: String, callback: Callback) {
        // JSON-encode to safely embed in JS string literals
        val safeReg = JSONObject.quote(regNumber)
        val safePass = JSONObject.quote(pass)

        val js = """
            (function() {
                try {
                    function dismissModalsAndLogin() {
                        // 1. Dismiss any modal/popup (e.g. "IMPORTANT INFO" for returning students)
                        var allButtons = document.querySelectorAll('input[type="button"], button, a.btn, [onclick]');
                        for (var i = 0; i < allButtons.length; i++) {
                            var txt = (allButtons[i].innerText || allButtons[i].value || '').trim().toUpperCase();
                            if (txt === 'OKAY' || txt === 'OK' || txt === 'CLOSE' || txt === 'DISMISS' ||
                                txt === 'CONTINUE' || txt === 'AGREE' || txt === 'GOT IT') {
                                allButtons[i].click();
                                break;
                            }
                        }

                        // Remove any visible overlay or modal backdrop
                        var overlays = document.querySelectorAll('.modal, #myModal, .popup, [role="dialog"], .overlay, .dialog-bg');
                        for (var m = 0; m < overlays.length; m++) {
                            if (overlays[m].style) overlays[m].style.display = 'none';
                        }
                        var backdrops = document.querySelectorAll('.modal-backdrop, .backdrop, .overlay-bg');
                        for (var b = 0; b < backdrops.length; b++) {
                            if (backdrops[b].parentNode) backdrops[b].parentNode.removeChild(backdrops[b]);
                        }

                        // 2. Fill Username / Registration Number
                        var userField = document.querySelector('input[name="userId"]') ||
                                        document.querySelector('input[name="username"]') ||
                                        document.querySelector('input[name="userName"]') ||
                                        document.querySelector('input[type="text"]:not([type="hidden"])');
                        if (userField) {
                            userField.focus();
                            userField.value = $safeReg;
                            userField.dispatchEvent(new Event('input', { bubbles: true }));
                            userField.dispatchEvent(new Event('change', { bubbles: true }));
                        }

                        // 3. Fill Password
                        var passField = document.querySelector('input[name="password"]') ||
                                        document.querySelector('input[type="password"]') ||
                                        document.querySelector('input[name="passwd"]');
                        if (passField) {
                            passField.focus();
                            passField.value = $safePass;
                            passField.dispatchEvent(new Event('input', { bubbles: true }));
                            passField.dispatchEvent(new Event('change', { bubbles: true }));
                        }

                        // 4. Click Login — try specific selectors then fall back to form submit
                        setTimeout(function() {
                            var submitBtn = document.querySelector('input[name="Submit22"]') ||
                                           document.querySelector('input[value="Login"]') ||
                                           document.querySelector('button[type="submit"]') ||
                                           document.querySelector('input[type="submit"]') ||
                                           document.querySelector('.btn-primary');

                            if (!submitBtn) {
                                // Scan all visible buttons for one labeled "Login"
                                var candidates = document.querySelectorAll('button, input[type="submit"], input[type="button"]');
                                for (var k = 0; k < candidates.length; k++) {
                                    var label = (candidates[k].innerText || candidates[k].value || '').trim();
                                    if (/^login$/i.test(label)) { submitBtn = candidates[k]; break; }
                                }
                            }

                            if (submitBtn) {
                                submitBtn.click();
                            } else if (document.forms.length > 0) {
                                document.forms[0].submit();
                            }
                        }, 300);
                    }

                    // Run immediately, and once more after 600ms in case the modal renders dynamically
                    dismissModalsAndLogin();
                    setTimeout(dismissModalsAndLogin, 600);
                    return "SUBMITTED";
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()

        view?.evaluateJavascript(js) {
            // Start polling for connectivity after submitting
            scheduleConnectivityCheck(callback, 0)
        }
    }

    private fun scheduleConnectivityCheck(callback: Callback, attempt: Int) {
        if (isCompleted || attempt >= 12) return
        val delay = if (attempt < 5) 700L else 1000L
        handler.postDelayed({
            if (isCompleted) return@postDelayed
            Thread {
                val online = NetworkDetector.isWifiActuallyOnline(context)
                handler.post {
                    if (online && !isCompleted) {
                        isCompleted = true
                        cleanup()
                        notifySystemNetworkValidated()
                        callback.onSuccess("Connected to VIT WiFi")
                    } else if (!isCompleted && attempt < 11) {
                        scheduleConnectivityCheck(callback, attempt + 1)
                    }
                }
            }.start()
        }, delay)
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

    private fun cleanup() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
        try {
            webView?.stopLoading()
            webView?.destroy()
        } catch (_: Exception) {}
        webView = null
        NetworkDetector.unbindFromWifi(context)
    }
}
