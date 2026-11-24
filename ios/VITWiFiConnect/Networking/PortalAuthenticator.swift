import Foundation
import WebKit

/// PortalAuthenticator for iOS
/// Executes captive portal authentication for Pronto Networks at VIT.
///
/// Tier 1: High-Speed Direct HTTP POST submission with SSL bypass.
/// Tier 2: Headless WebKit (WKWebView) auto-filler that dismisses modals,
/// fills credentials in DOM, and submits the form.
/// Tier 3: Session Logout to release MAC quota on campus gateway.
public final class PortalAuthenticator: NSObject {

    public static let shared = PortalAuthenticator()

    private var headlessWebView: WKWebView?
    private var webAuthCompletion: ((AuthResult) -> Void)?
    private var webWatchdogTimer: Timer?

    private override init() {
        super.init()
    }

    // MARK: - Primary Authentication Pipeline

    public func authenticate(regNumber: String, pass: String) async -> AuthResult {
        // Step 1: Direct High-Speed HTTP POST
        let postResult = await performDirectPost(regNumber: regNumber, pass: pass)
        if postResult.success {
            return postResult
        }

        // If credentials explicitly rejected, do not fall back to webview
        if case .error(let msg) = postResult.status, msg.lowercased().contains("invalid") {
            return postResult
        }

        // Step 2: Headless WebKit Fallback (DOM Autofill & Modal Dismissal)
        return await withCheckedContinuation { continuation in
            DispatchQueue.main.async {
                self.authenticateViaHeadlessWeb(regNumber: regNumber, pass: pass) { result in
                    continuation.resume(returning: result)
                }
            }
        }
    }

    // MARK: - Direct HTTP POST (Tier 1)

    private func performDirectPost(regNumber: String, pass: String) async -> AuthResult {
        guard let url = URL(string: CampusNetworkConstants.loginUrlHttp) else {
            return AuthResult(success: false, status: .error(message: "Invalid portal URL"))
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 8.0
        request.setValue("phc.prontonetworks.com", forHTTPHeaderField: "Host")
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15", forHTTPHeaderField: "User-Agent")
        request.setValue("http://phc.prontonetworks.com", forHTTPHeaderField: "Origin")
        request.setValue("http://phc.prontonetworks.com/cgi-bin/authlogin", forHTTPHeaderField: "Referer")
        request.setValue("close", forHTTPHeaderField: "Connection")

        let encodedReg = regNumber.trimmingCharacters(in: .whitespacesAndNewlines).urlFormEncoded()
        let encodedPass = pass.urlFormEncoded()
        let bodyString = "userId=\(encodedReg)&password=\(encodedPass)&serviceName=ProntoAuthentication&Submit22=Login"
        request.httpBody = bodyString.data(using: .utf8)

        let config = URLSessionConfiguration.ephemeral
        config.allowsCellularAccess = false
        let session = URLSession(configuration: config, delegate: TrustAllCertDelegate(), delegateQueue: nil)

        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                return AuthResult(success: false, status: .error(message: "No response from gateway"))
            }

            if httpResponse.statusCode == 200 || httpResponse.statusCode == 302 {
                let responseString = String(data: data, encoding: .utf8)?.lowercased() ?? ""

                let hasError = ["invalid", "incorrect", "wrong password", "authentication failed",
                                "user not found", "account expired", "session limit", "max session"]
                    .contains { responseString.contains($0) }

                if hasError {
                    return AuthResult(success: false, status: .error(message: "Invalid registration number or password"))
                }

                // Wait 600ms for router MAC authorization
                try? await Task.sleep(nanoseconds: 600_000_000)

                let isOnline = await NetworkDetector.shared.probeInternet()
                if isOnline {
                    return AuthResult(success: true, status: .connected, message: "Connected to VIT WiFi")
                }
            }
            return AuthResult(success: false, status: .error(message: "Direct POST verification pending"))
        } catch {
            return AuthResult(success: false, status: .error(message: error.localizedDescription))
        }
    }

    // MARK: - Headless WebKit Fallback (Tier 2)

    private func authenticateViaHeadlessWeb(regNumber: String, pass: String, completion: @escaping (AuthResult) -> Void) {
        self.webAuthCompletion = completion

        let webConfig = WKWebViewConfiguration()
        webConfig.websiteDataStore = .nonPersistent()

        let wv = WKWebView(frame: .zero, configuration: webConfig)
        self.headlessWebView = wv
        wv.navigationDelegate = self

        // 12-second watchdog timer
        webWatchdogTimer?.invalidate()
        webWatchdogTimer = Timer.scheduledTimer(withTimeInterval: 12.0, repeats: false) { [weak self] _ in
            Task { [weak self] in
                guard let self = self else { return }
                let online = await NetworkDetector.shared.probeInternet()
                await MainActor.run {
                    self.cleanupWebView()
                    if online {
                        completion(AuthResult(success: true, status: .connected, message: "Connected to VIT WiFi"))
                    } else {
                        completion(AuthResult(success: false, status: .error(message: "Authentication timed out. Please verify credentials.")))
                    }
                }
            }
        }

        let targetUrlStr = NetworkDetector.shared.lastDetectedPortalUrl ?? CampusNetworkConstants.loginUrlHttp
        if let targetUrl = URL(string: targetUrlStr) {
            wv.load(URLRequest(url: targetUrl))
        } else {
            cleanupWebView()
            completion(AuthResult(success: false, status: .error(message: "Invalid portal URL")))
        }
    }

    private func injectAutofillScript(into webView: WKWebView, regNumber: String, pass: String) {
        let safeReg = (try? String(data: JSONEncoder().encode(regNumber), encoding: .utf8)) ?? "\"\(regNumber)\""
        let safePass = (try? String(data: JSONEncoder().encode(pass), encoding: .utf8)) ?? "\"\(pass)\""

        let js = """
        (function() {
            try {
                function runLogin() {
                    // 1. Dismiss modal notices (e.g. "IMPORTANT INFO ... Note for returning students" [OKAY])
                    var btns = document.querySelectorAll('button, input[type="button"], a.btn, [onclick]');
                    for (var i = 0; i < btns.length; i++) {
                        var txt = (btns[i].innerText || btns[i].value || '').trim().toUpperCase();
                        if (txt === 'OKAY' || txt === 'OK' || txt === 'CLOSE' || txt === 'DISMISS' || txt === 'CONTINUE' || txt === 'AGREE') {
                            btns[i].click();
                            break;
                        }
                    }

                    // Remove overlays
                    var overlays = document.querySelectorAll('.modal, #myModal, .popup, [role="dialog"], .backdrop');
                    for (var m = 0; m < overlays.length; m++) {
                        if (overlays[m].style) overlays[m].style.display = 'none';
                        if (overlays[m].parentNode) overlays[m].parentNode.removeChild(overlays[m]);
                    }

                    // 2. Autofill Username
                    var uFields = ['input[name="userId"]', 'input[name="username"]', 'input[name="userName"]', 'input[type="text"]'];
                    for (var u = 0; u < uFields.length; u++) {
                        var el = document.querySelector(uFields[u]);
                        if (el) {
                            el.value = \(safeReg);
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                            break;
                        }
                    }

                    // 3. Autofill Password
                    var pFields = ['input[name="password"]', 'input[type="password"]', 'input[name="passwd"]'];
                    for (var p = 0; p < pFields.length; p++) {
                        var el = document.querySelector(pFields[p]);
                        if (el) {
                            el.value = \(safePass);
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                            break;
                        }
                    }

                    // 4. Click Submit
                    setTimeout(function() {
                        var submitBtn = document.querySelector('input[name="Submit22"]') ||
                                       document.querySelector('input[value="Login"]') ||
                                       document.querySelector('button[type="submit"]') ||
                                       document.querySelector('.btn-primary');
                        if (submitBtn) {
                            submitBtn.click();
                        } else if (document.forms.length > 0) {
                            document.forms[0].submit();
                        }
                    }, 250);
                }

                runLogin();
                setTimeout(runLogin, 500);
            } catch(e) {}
        })();
        """

        webView.evaluateJavaScript(js) { [weak self] _, _ in
            self?.pollForOnlineConfirmation(attempt: 0)
        }
    }

    private func pollForOnlineConfirmation(attempt: Int) {
        guard attempt < 10 else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
            Task { [weak self] in
                guard let self = self else { return }
                let isOnline = await NetworkDetector.shared.probeInternet()
                await MainActor.run {
                    if isOnline {
                        self.cleanupWebView()
                        self.webAuthCompletion?(AuthResult(success: true, status: .connected, message: "Connected to VIT WiFi"))
                        self.webAuthCompletion = nil
                    } else {
                        self.pollForOnlineConfirmation(attempt: attempt + 1)
                    }
                }
            }
        }
    }

    private func cleanupWebView() {
        webWatchdogTimer?.invalidate()
        webWatchdogTimer = nil
        headlessWebView?.stopLoading()
        headlessWebView?.navigationDelegate = nil
        headlessWebView = nil
    }

    // MARK: - Session Logout (Tier 3)

    public func logout() async -> Bool {
        SettingsStore.shared.clearSession()
        let config = URLSessionConfiguration.ephemeral
        config.allowsCellularAccess = false
        let session = URLSession(configuration: config, delegate: TrustAllCertDelegate(), delegateQueue: nil)

        let urls = [CampusNetworkConstants.logoutUrl, "https://phc.prontonetworks.com/cgi-bin/authlogout"]
        var success = false

        for urlString in urls {
            guard let url = URL(string: urlString) else { continue }
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            request.timeoutInterval = 3.5

            do {
                let (_, response) = try await session.data(for: request)
                if let httpResponse = response as? HTTPURLResponse,
                   httpResponse.statusCode == 200 || httpResponse.statusCode == 302 {
                    success = true
                    break
                }
            } catch {}
        }
        return success
    }
}

// MARK: - WKNavigationDelegate

extension PortalAuthenticator: WKNavigationDelegate {
    public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        let currentUrl = webView.url?.absoluteString.lowercased() ?? ""

        // If navigated away from Pronto login, we are connected
        if !currentUrl.isEmpty && !currentUrl.contains("phc.prontonetworks.com") && !currentUrl.contains("authlogin") {
            cleanupWebView()
            webAuthCompletion?(AuthResult(success: true, status: .connected, message: "Connected to VIT WiFi"))
            webAuthCompletion = nil
            return
        }

        // Fill credentials & dismiss modals
        if let reg = CredentialStore.shared.getRegNumber(),
           let pass = CredentialStore.shared.getPassword() {
            injectAutofillScript(into: webView, regNumber: reg, pass: pass)
        }
    }

    public func webView(
        _ webView: WKWebView,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        // Bypass self-signed university gateway certificates
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
           let trust = challenge.protectionSpace.serverTrust {
            completionHandler(.useCredential, URLCredential(trust: trust))
        } else {
            completionHandler(.performDefaultHandling, nil)
        }
    }
}

// MARK: - Form URL Encoding Helper

private extension String {
    func urlFormEncoded() -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._*")
        return self.addingPercentEncoding(withAllowedCharacters: allowed)?
            .replacingOccurrences(of: " ", with: "+") ?? self
    }
}

// MARK: - SSL Bypass for University Gateways

private class TrustAllCertDelegate: NSObject, URLSessionDelegate {
    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
           let trust = challenge.protectionSpace.serverTrust {
            completionHandler(.useCredential, URLCredential(trust: trust))
        } else {
            completionHandler(.performDefaultHandling, nil)
        }
    }
}
