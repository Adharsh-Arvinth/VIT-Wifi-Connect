import SwiftUI

public struct HomeView: View {

    @ObservedObject var settingsStore = SettingsStore.shared
    @ObservedObject var networkDetector = NetworkDetector.shared

    @State private var connectionStatus: ConnectionStatus = .disconnected
    @State private var statusTitle: String = "Ready to connect"
    @State private var statusSubtitle: String = "VIT WiFi"
    @State private var isConnecting: Bool = false
    @State private var showSettings: Bool = false
    @State private var showCredentials: Bool = false
    @State private var showLogoutAlert: Bool = false

    // Session stats
    @State private var sessionSeconds: Int = 0
    @State private var sessionTimer: Timer?
    @State private var histogramValues: [Double] = Array(repeating: 0.1, count: 16)
    @State private var statsTimer: Timer?

    public init() {}

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // Dynamic ambient gradient backdrop
            ambientBackdrop

            ScrollView {
                VStack(spacing: 20) {
                    // Top Bar
                    headerBar

                    // 1. Primary Liquid Glass Connection Card
                    primaryConnectionCard

                    // 2. Network Activity & Histogram Card
                    networkActivityCard

                    // 3. Auto-Login Switch Card
                    autoLoginCard

                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 20)
                .frame(maxWidth: 640) // Optimal for iPad and iPhone
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsView()
        }
        .sheet(isPresented: $showCredentials) {
            CredentialsView()
        }
        .alert("Logout from Campus Gateway?", isPresented: $showLogoutAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Logout", role: .destructive) {
                Task {
                    await MainActor.run {
                        settingsStore.isExplicitlyDisconnected = true
                        connectionStatus = .disconnected
                        statusTitle = "Disconnected"
                        statusSubtitle = "Logged out from campus gateway"
                        stopSessionTimers()
                    }
                    _ = await PortalAuthenticator.shared.logout()
                    await MainActor.run {
                        HapticHelper.click()
                    }
                }
            }
        } message: {
            Text("Release your active device MAC address from Pronto so you can connect from your laptop or another device.")
        }
        .onAppear {
            refreshState()
        }
        .onReceive(NotificationCenter.default.publisher(for: .vitWifiPerformAction)) { notification in
            guard let action = notification.object as? String else { return }
            switch action {
            case "disconnect", "logout":
                Task {
                    await MainActor.run {
                        settingsStore.isExplicitlyDisconnected = true
                        connectionStatus = .disconnected
                        statusTitle = "Disconnected"
                        statusSubtitle = "Logged out from campus gateway"
                        stopSessionTimers()
                    }
                    _ = await PortalAuthenticator.shared.logout()
                    await MainActor.run {
                        HapticHelper.click()
                    }
                }
            case "connect", "login":
                handleConnectAction()
            case "toggle":
                Task {
                    let online = await networkDetector.probeInternet()
                    await MainActor.run {
                        if online && !settingsStore.isExplicitlyDisconnected {
                            Task {
                                await MainActor.run {
                                    settingsStore.isExplicitlyDisconnected = true
                                    connectionStatus = .disconnected
                                    statusTitle = "Disconnected"
                                    statusSubtitle = "Logged out from campus gateway"
                                    stopSessionTimers()
                                }
                                _ = await PortalAuthenticator.shared.logout()
                                await MainActor.run {
                                    HapticHelper.click()
                                }
                            }
                        } else {
                            handleConnectAction()
                        }
                    }
                }
            default:
                break
            }
        }
    }

    // MARK: - Ambient Backdrop
    private var ambientBackdrop: some View {
        ZStack {
            Circle()
                .fill(settingsStore.currentPalette.primaryColor.opacity(0.18))
                .blur(radius: 110)
                .offset(x: -80, y: -200)

            Circle()
                .fill(settingsStore.currentPalette.secondaryColor.opacity(0.14))
                .blur(radius: 120)
                .offset(x: 100, y: -80)

            Circle()
                .fill(settingsStore.currentPalette.glowColor.opacity(0.08))
                .blur(radius: 90)
                .offset(x: -40, y: 180)
        }
    }

    // MARK: - Header Bar
    private var headerBar: some View {
        HStack {
            Text("VIT WiFi Connect")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.white)

            Spacer()

            Button(action: {
                HapticHelper.click()
                showSettings = true
            }) {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
            }
            .buttonStyle(AppleMusicCircleButtonStyle())
        }
        .padding(.top, 14)
    }

    // MARK: - Primary Connection Card
    private var primaryConnectionCard: some View {
        VStack(spacing: 16) {
            // Micro Status Capsule / Network Badge
            HStack(spacing: 6) {
                Circle()
                    .fill(statusDotColor)
                    .frame(width: 6, height: 6)
                Text(statusBadgeText)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.white.opacity(0.85))
            }
            .padding(.horizontal, 12)
            .frame(height: 28)
            .background(.ultraThinMaterial, in: Capsule())
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
            .padding(.top, 4)

            // Liquid Glass Orb
            ConnectionOrbView(
                status: connectionStatus,
                accentColor: settingsStore.currentPalette.primaryColor,
                secondaryAccentColor: settingsStore.currentPalette.secondaryColor
            )
            .padding(.top, 4)

            // Status Title & Subtitle
            VStack(spacing: 4) {
                Text(statusTitle)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .animation(.spring(response: 0.35, dampingFraction: 0.7), value: statusTitle)

                Text(statusSubtitle)
                    .font(.system(size: 14))
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
                    .animation(.spring(response: 0.35, dampingFraction: 0.7), value: statusSubtitle)
            }

            // Connect & Disconnect Action Buttons
            HStack(spacing: 12) {
                if case .connected = connectionStatus {
                    Button(action: {
                        HapticHelper.click()
                        showLogoutAlert = true
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "wifi.slash")
                                .font(.system(size: 16, weight: .semibold))
                            Text("Disconnect")
                        }
                    }
                    .buttonStyle(AppleMusicSecondaryButtonStyle(
                        strokeColor: Color.red.opacity(0.35),
                        textColor: Color(red: 248/255, green: 113/255, blue: 113/255)
                    ))

                    Button(action: handleConnectAction) {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 16, weight: .semibold))
                            Text("Reconnect")
                        }
                    }
                    .buttonStyle(AppleMusicPrimaryButtonStyle(
                        primaryColor: settingsStore.currentPalette.primaryColor,
                        secondaryColor: settingsStore.currentPalette.secondaryColor,
                        textColor: buttonTextColor
                    ))
                    .disabled(isConnecting)
                } else {
                    Button(action: handleConnectAction) {
                        HStack(spacing: 8) {
                            if isConnecting {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: buttonTextColor))
                                    .scaleEffect(0.9)
                            } else {
                                Image(systemName: "wifi")
                                    .font(.system(size: 16, weight: .semibold))
                            }
                            Text(buttonLabel)
                        }
                    }
                    .buttonStyle(AppleMusicPrimaryButtonStyle(
                        primaryColor: settingsStore.currentPalette.primaryColor,
                        secondaryColor: settingsStore.currentPalette.secondaryColor,
                        textColor: buttonTextColor
                    ))
                    .disabled(isConnecting)
                }
            }
            .padding(.horizontal, 4)
            .padding(.bottom, 4)
        }
        .padding(20)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24))
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(
                    LinearGradient(
                        colors: [
                            settingsStore.currentPalette.primaryColor.opacity(0.45),
                            Color.white.opacity(0.18),
                            Color.white.opacity(0.04)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1.2
                )
        )
    }

    // MARK: - Network Activity Card
    private var networkActivityCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Network Activity")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                Spacer()
                HStack(spacing: 8) {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.down")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(settingsStore.currentPalette.primaryColor)
                        Text("0.0 bps")
                            .font(.system(size: 11, weight: .medium))
                            .monospacedDigit()
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 8)
                    .frame(height: 24)
                    .background(Color.white.opacity(0.08), in: Capsule())
                    .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 1))

                    HStack(spacing: 4) {
                        Image(systemName: "arrow.up")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(settingsStore.currentPalette.primaryColor)
                        Text("0.0 bps")
                            .font(.system(size: 11, weight: .medium))
                            .monospacedDigit()
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 8)
                    .frame(height: 24)
                    .background(Color.white.opacity(0.08), in: Capsule())
                    .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 1))
                }
            }

            // Visualizer Histogram
            NetworkHistogramView(
                values: histogramValues,
                accentColor: settingsStore.currentPalette.primaryColor
            )

            Divider().background(Color.white.opacity(0.08))

            // Session Stats
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Session time")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.gray)
                    Text(formattedSessionTime)
                        .font(.system(size: 15, weight: .semibold))
                        .monospacedDigit()
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Divider()
                    .frame(height: 28)
                    .background(Color.white.opacity(0.08))

                VStack(alignment: .leading, spacing: 3) {
                    Text("Data transferred")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.gray)
                    Text("0 B")
                        .font(.system(size: 15, weight: .semibold))
                        .monospacedDigit()
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(18)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(
                    LinearGradient(
                        colors: [
                            settingsStore.currentPalette.primaryColor.opacity(0.35),
                            Color.white.opacity(0.14),
                            Color.white.opacity(0.04)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1.2
                )
        )
    }

    // MARK: - Auto-Login Card
    private var autoLoginCard: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text("Auto-login on Connect")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.white)
                Text("Authenticate automatically when VIT WiFi is detected")
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
            }
            Spacer()
            Toggle("", isOn: $settingsStore.autoLoginEnabled)
                .labelsHidden()
                .tint(settingsStore.currentPalette.primaryColor)
        }
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(
                    LinearGradient(
                        colors: [
                            settingsStore.currentPalette.primaryColor.opacity(0.30),
                            Color.white.opacity(0.12),
                            Color.white.opacity(0.04)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1.2
                )
        )
    }

    private var buttonTextColor: Color {
        settingsStore.currentPalette.id == "titanium" ? Color(red: 13/255, green: 13/255, blue: 16/255) : .white
    }

    // MARK: - Computed Properties
    private var buttonLabel: String {
        switch connectionStatus {
        case .connected: return "Reconnect"
        case .connecting, .authenticating: return "Connecting…"
        default: return "Connect"
        }
    }

    private var formattedSessionTime: String {
        let mins = sessionSeconds / 60
        let secs = sessionSeconds % 60
        return String(format: "%02d:%02d", mins, secs)
    }

    private var statusBadgeText: String {
        switch connectionStatus {
        case .connected: return "Connected · Campus 5G"
        case .connecting, .authenticating: return "Connecting…"
        case .vpnBlocked: return "VPN active · Pause needed"
        default: return "Offline · Ready"
        }
    }

    private var statusDotColor: Color {
        switch connectionStatus {
        case .connected: return settingsStore.currentPalette.primaryColor
        case .connecting, .authenticating: return .orange
        case .vpnBlocked: return .yellow
        default: return .gray
        }
    }

    // MARK: - Logic & Actions

    private func refreshState() {
        if isConnecting { return }

        if settingsStore.isExplicitlyDisconnected {
            connectionStatus = .disconnected
            statusTitle = "Ready to connect"
            statusSubtitle = "VIT WiFi"
            stopSessionTimers()
            return
        }

        Task {
            let isOnline = await networkDetector.probeInternet()
            await MainActor.run {
                if self.settingsStore.isExplicitlyDisconnected {
                    self.connectionStatus = .disconnected
                    self.statusTitle = "Ready to connect"
                    self.statusSubtitle = "VIT WiFi"
                    self.stopSessionTimers()
                    return
                }

                if isOnline {
                    connectionStatus = .connected
                    statusTitle = "Connected"
                    statusSubtitle = "VIT WiFi · Online"
                    startSessionTimers()
                } else if networkDetector.isVpnActive {
                    connectionStatus = .vpnBlocked
                    statusTitle = "VPN is Active"
                    statusSubtitle = "Please pause VPN to allow campus gateway login"
                    stopSessionTimers()
                } else {
                    connectionStatus = .disconnected
                    statusTitle = "Ready to connect"
                    statusSubtitle = "VIT WiFi"
                    stopSessionTimers()
                }
            }
        }
    }

    private func handleConnectAction() {
        guard !isConnecting else { return }
        settingsStore.isExplicitlyDisconnected = false
        HapticHelper.medium()

        // Verify credentials
        guard let reg = CredentialStore.shared.getRegNumber(),
              let pass = CredentialStore.shared.getPassword(),
              !reg.isEmpty, !pass.isEmpty else {
            showCredentials = true
            return
        }

        isConnecting = true
        connectionStatus = .connecting(step: "Connecting…")
        statusTitle = "Connecting"
        statusSubtitle = "Contacting campus network gateway…"

        Task {
            // Adaptive Portal Polling: handles random iOS redirect timing
            CaptivePortalMonitor.shared.waitForPortalRedirect(
                onPortalDetected: { _ in
                    Task {
                        await self.executeAuthentication(reg: reg, pass: pass)
                    }
                },
                onAlreadyOnline: {
                    self.onAuthenticationSuccess("Already connected to VIT WiFi")
                },
                onTimeout: {
                    Task {
                        // Fall back to direct submission
                        await self.executeAuthentication(reg: reg, pass: pass)
                    }
                }
            )
        }
    }

    private func executeAuthentication(reg: String, pass: String) async {
        await MainActor.run {
            connectionStatus = .authenticating
            statusTitle = "Authenticating"
            statusSubtitle = "Verifying credentials with Pronto…"
        }

        let result = await PortalAuthenticator.shared.authenticate(regNumber: reg, pass: pass)

        await MainActor.run {
            self.isConnecting = false
            if result.success {
                self.onAuthenticationSuccess(result.message ?? "Connected to VIT WiFi")
            } else {
                self.onAuthenticationFailure(result.message ?? "Authentication failed")
            }
        }
    }

    private func onAuthenticationSuccess(_ message: String) {
        connectionStatus = .connected
        statusTitle = "Connected"
        statusSubtitle = "VIT WiFi · Online"
        settingsStore.onAuthSuccess()
        HapticHelper.success()
        startSessionTimers()

        // Zero-drain mode: stop background monitors once connected
        if settingsStore.noBackgroundMode {
            networkDetector.stopMonitoring()
        }
    }

    private func onAuthenticationFailure(_ message: String) {
        connectionStatus = .error(message: message)
        statusTitle = "Authentication Failed"
        statusSubtitle = message
        settingsStore.onAuthFail()
        HapticHelper.error()
        stopSessionTimers()
    }

    private func startSessionTimers() {
        stopSessionTimers()
        sessionTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            sessionSeconds += 1
        }
        statsTimer = Timer.scheduledTimer(withTimeInterval: 0.8, repeats: true) { _ in
            withAnimation {
                histogramValues = (0..<16).map { _ in Double.random(in: 0.15...0.9) }
            }
        }
    }

    private func stopSessionTimers() {
        sessionTimer?.invalidate()
        sessionTimer = nil
        statsTimer?.invalidate()
        statsTimer = nil
        histogramValues = Array(repeating: 0.08, count: 16)
        sessionSeconds = 0
    }
}
