import SwiftUI

public struct SettingsView: View {

    @Environment(\.presentationMode) var presentationMode
    @ObservedObject var settingsStore = SettingsStore.shared

    @State private var showCredentialsSheet = false
    @State private var showClearConfirmAlert = false
    @State private var showLogoutConfirmAlert = false
    @State private var showMacGuideAlert = false
    @State private var statusToast: String?

    public init() {}

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // Dynamic Ambient Backdrop
            Circle()
                .fill(settingsStore.currentPalette.primaryColor.opacity(0.14))
                .blur(radius: 120)
                .offset(x: -80, y: -250)

            Circle()
                .fill(settingsStore.currentPalette.secondaryColor.opacity(0.10))
                .blur(radius: 130)
                .offset(x: 100, y: -100)

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Header
                    HStack {
                        Button(action: {
                            HapticHelper.click()
                            presentationMode.wrappedValue.dismiss()
                        }) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                        }
                        .buttonStyle(AppleMusicCircleButtonStyle())

                        Spacer()

                        Text("Settings")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)

                        Spacer()

                        Color.clear.frame(width: 38, height: 38)
                    }
                    .padding(.top, 16)

                    // SECTION 1: ACCOUNT & SESSION
                    sectionHeader("Account & Session")

                    VStack(spacing: 0) {
                        // Auto-login toggle
                        Toggle(isOn: $settingsStore.autoLoginEnabled) {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("Auto-login on Connect")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(.white)
                                Text("Authenticate automatically when campus Wi-Fi is detected")
                                    .font(.system(size: 12))
                                    .foregroundColor(.gray)
                            }
                        }
                        .tint(settingsStore.currentPalette.primaryColor)
                        .padding(.vertical, 14)
                        .padding(.horizontal, 16)

                        Divider().background(Color.white.opacity(0.08))

                        // Zero-Drain Mode toggle
                        Toggle(isOn: $settingsStore.noBackgroundMode) {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("Zero-Drain Mode")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(.white)
                                Text("Stop all background monitoring when connected (0% RAM / battery)")
                                    .font(.system(size: 12))
                                    .foregroundColor(.gray)
                            }
                        }
                        .tint(settingsStore.currentPalette.primaryColor)
                        .padding(.vertical, 14)
                        .padding(.horizontal, 16)

                        Divider().background(Color.white.opacity(0.08))

                        // Update Credentials row
                        Button(action: {
                            HapticHelper.click()
                            showCredentialsSheet = true
                        }) {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text("Update Credentials")
                                        .font(.system(size: 15, weight: .medium))
                                        .foregroundColor(.white)
                                    Text(CredentialStore.shared.getRegNumber() ?? "Registration number and password")
                                        .font(.system(size: 12))
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 14))
                                    .foregroundColor(.gray)
                            }
                            .padding(.vertical, 14)
                            .padding(.horizontal, 16)
                        }

                        Divider().background(Color.white.opacity(0.08))

                        // Session Logout row
                        Button(action: {
                            HapticHelper.click()
                            showLogoutConfirmAlert = true
                        }) {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text("Logout from Campus Gateway")
                                        .font(.system(size: 15, weight: .medium))
                                        .foregroundColor(.white)
                                    Text("Release your MAC address session from Pronto router")
                                        .font(.system(size: 12))
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                                Image(systemName: "arrow.right.circle")
                                    .font(.system(size: 16))
                                    .foregroundColor(.orange)
                            }
                            .padding(.vertical, 14)
                            .padding(.horizontal, 16)
                        }

                        Divider().background(Color.white.opacity(0.08))

                        // Clear Credentials row
                        Button(action: {
                            HapticHelper.click()
                            showClearConfirmAlert = true
                        }) {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text("Clear Saved Credentials")
                                        .font(.system(size: 15, weight: .medium))
                                        .foregroundColor(.red)
                                    Text("Remove credentials from device storage")
                                        .font(.system(size: 12))
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                                Image(systemName: "trash")
                                    .font(.system(size: 15))
                                    .foregroundColor(.red)
                            }
                            .padding(.vertical, 14)
                            .padding(.horizontal, 16)
                        }
                    }
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.white.opacity(0.08), lineWidth: 1))

                    // SECTION 2: APPEARANCE & PALETTES
                    sectionHeader("Color Palette")

                    VStack(spacing: 12) {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 100))], spacing: 12) {
                            ForEach(SettingsStore.palettes) { palette in
                                Button(action: {
                                    HapticHelper.click()
                                    withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                                        settingsStore.selectedPaletteId = palette.id
                                    }
                                }) {
                                    VStack(spacing: 6) {
                                        Circle()
                                            .fill(
                                                LinearGradient(
                                                    colors: [palette.primaryColor, palette.secondaryColor],
                                                    startPoint: .topLeading,
                                                    endPoint: .bottomTrailing
                                                )
                                            )
                                            .frame(width: 34, height: 34)
                                            .overlay(
                                                Circle()
                                                    .stroke(Color.white, lineWidth: settingsStore.selectedPaletteId == palette.id ? 2.5 : 0)
                                            )
                                            .shadow(color: palette.primaryColor.opacity(0.45), radius: 6)

                                        Text(palette.name)
                                            .font(.system(size: 11, weight: settingsStore.selectedPaletteId == palette.id ? .bold : .medium))
                                            .foregroundColor(.white)

                                        Text(palette.subtitle)
                                            .font(.system(size: 9))
                                            .foregroundColor(.gray)
                                            .lineLimit(1)
                                    }
                                    .padding(.vertical, 12)
                                    .frame(maxWidth: .infinity)
                                    .background(
                                        settingsStore.selectedPaletteId == palette.id ?
                                        palette.primaryColor.opacity(0.15) : Color.white.opacity(0.04),
                                        in: RoundedRectangle(cornerRadius: 14)
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14)
                                            .stroke(
                                                settingsStore.selectedPaletteId == palette.id ?
                                                palette.primaryColor.opacity(0.45) : Color.clear,
                                                lineWidth: 1
                                            )
                                    )
                                }
                            }
                        }
                        .padding(14)
                    }
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(
                                LinearGradient(
                                    colors: [
                                        settingsStore.currentPalette.primaryColor.opacity(0.35),
                                        Color.white.opacity(0.12),
                                        Color.white.opacity(0.04)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 1
                            )
                    )

                    // SECTION 3: CAMPUS WI-FI STABILITY
                    sectionHeader("Campus Wi-Fi Stability")

                    VStack(spacing: 0) {
                        Button(action: {
                            HapticHelper.click()
                            showMacGuideAlert = true
                        }) {
                            HStack {
                                Image(systemName: "shield.lefthalf.filled")
                                    .font(.system(size: 20))
                                    .foregroundColor(settingsStore.currentPalette.primaryColor)
                                    .frame(width: 32)

                                VStack(alignment: .leading, spacing: 3) {
                                    Text("Disable Private Wi-Fi Address")
                                        .font(.system(size: 15, weight: .medium))
                                        .foregroundColor(.white)
                                    Text("Fixes random campus Wi-Fi disconnects on iPhone / iPad")
                                        .font(.system(size: 12))
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 14))
                                    .foregroundColor(.gray)
                            }
                            .padding(.vertical, 14)
                            .padding(.horizontal, 16)
                        }
                    }
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.white.opacity(0.08), lineWidth: 1))

                    // SECTION 4: PRIVACY
                    sectionHeader("Privacy & Security")

                    VStack(alignment: .leading, spacing: 8) {
                        Text("100% Local & Private")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)

                        Text("VIT WiFi Connect contains zero tracking, zero crash analytics, and zero external telemetry. Your credentials never leave your device except directly to the campus authentication gateway.")
                            .font(.system(size: 12))
                            .foregroundColor(.gray)
                            .lineSpacing(3)
                    }
                    .padding(16)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.white.opacity(0.08), lineWidth: 1))

                    Spacer(minLength: 32)
                }
                .padding(.horizontal, 20)
            }

            if let toast = statusToast {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding(.bottom, 24)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .sheet(isPresented: $showCredentialsSheet) {
            CredentialsView()
        }
        .alert("Disable Private Wi-Fi Address", isPresented: $showMacGuideAlert) {
            Button("Got it", role: .cancel) {}
        } message: {
            Text("VIT routers track active login sessions by hardware MAC address.\n\nBy default, iOS randomizes your MAC with 'Private Wi-Fi Address', which causes the router to drop your connection every few minutes.\n\nTo fix:\n1. Open iOS Settings > Wi-Fi.\n2. Tap the (i) icon next to your connected VIT network (e.g. F-VIT).\n3. Turn OFF 'Private Wi-Fi Address'.\n\nYour session will now remain permanently stable without disconnecting!")
        }
        .alert("Logout from Campus Gateway?", isPresented: $showLogoutConfirmAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Logout", role: .destructive) {
                Task {
                    let success = await PortalAuthenticator.shared.logout()
                    await MainActor.run {
                        HapticHelper.click()
                        showToast(success ? "Logged out from campus gateway" : "Logout request sent")
                    }
                }
            }
        } message: {
            Text("This will send a logout request to Pronto Networks and release your device MAC address so you can use other devices.")
        }
        .alert("Clear Saved Credentials?", isPresented: $showClearConfirmAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Clear", role: .destructive) {
                CredentialStore.shared.clearCredentials()
                HapticHelper.success()
                showToast("Credentials cleared")
            }
        } message: {
            Text("Are you sure you want to remove your registration number and password?")
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 13, weight: .medium))
            .foregroundColor(.gray)
            .padding(.leading, 4)
    }

    private func showToast(_ message: String) {
        withAnimation {
            statusToast = message
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation {
                self.statusToast = nil
            }
        }
    }
}
