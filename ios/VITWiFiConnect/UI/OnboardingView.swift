import SwiftUI

public struct OnboardingView: View {

    @ObservedObject var settingsStore = SettingsStore.shared
    public var onComplete: () -> Void

    @State private var regNumber: String = ""
    @State private var password: String = ""
    @State private var isPasswordVisible: Bool = false
    @State private var errorMessage: String?

    public init(onComplete: @escaping () -> Void) {
        self.onComplete = onComplete
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            Circle()
                .fill(settingsStore.currentPalette.primaryColor.opacity(0.15))
                .blur(radius: 100)
                .offset(y: -180)

            VStack(alignment: .leading, spacing: 20) {
                Spacer()

                // Icon / Logo
                Image(systemName: "wifi.router.fill")
                    .font(.system(size: 48))
                    .foregroundColor(settingsStore.currentPalette.primaryColor)
                    .padding(.bottom, 8)

                Text("Campus WiFi")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.white)

                Text("Automatic, reliable authentication for VIT campus Wi-Fi networks.")
                    .font(.system(size: 16))
                    .foregroundColor(.gray)

                // Input form
                VStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("REGISTRATION NUMBER")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.gray)

                        TextField("e.g. 21BCE1001", text: $regNumber)
                            .textCase(.uppercase)
                            .autocapitalization(.allCharacters)
                            .disableAutocorrection(true)
                            .foregroundColor(.white)
                            .padding(14)
                            .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("PASSWORD")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.gray)

                        HStack {
                            if isPasswordVisible {
                                TextField("WiFi Password", text: $password)
                                    .autocapitalization(.none)
                                    .disableAutocorrection(true)
                                    .foregroundColor(.white)
                            } else {
                                SecureField("WiFi Password", text: $password)
                                    .foregroundColor(.white)
                            }

                            Button(action: {
                                isPasswordVisible.toggle()
                                HapticHelper.click()
                            }) {
                                Image(systemName: isPasswordVisible ? "eye.slash.fill" : "eye.fill")
                                    .foregroundColor(.gray)
                            }
                        }
                        .padding(14)
                        .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .padding(20)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.1), lineWidth: 1))

                HStack(spacing: 8) {
                    Image(systemName: "lock.shield.fill")
                        .font(.system(size: 13))
                        .foregroundColor(.gray)
                    Text("Stored locally on your device with hardware encryption.")
                        .font(.system(size: 12))
                        .foregroundColor(.gray)
                }
                .padding(.horizontal, 4)

                if let err = errorMessage {
                    Text(err)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.red)
                }

                Spacer()

                Button(action: saveAndProceed) {
                    HStack {
                        Spacer()
                        Text("Save and Connect")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(settingsStore.currentPalette.id == "titanium" ? Color(red: 13/255, green: 13/255, blue: 16/255) : .white)
                        Spacer()
                    }
                    .padding(.vertical, 16)
                    .background(
                        LinearGradient(
                            colors: [
                                settingsStore.currentPalette.primaryColor,
                                settingsStore.currentPalette.secondaryColor
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        in: RoundedRectangle(cornerRadius: 16)
                    )
                    .shadow(color: settingsStore.currentPalette.primaryColor.opacity(0.35), radius: 8, y: 3)
                }
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 24)
        }
    }

    private func saveAndProceed() {
        let cleanReg = regNumber.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let cleanPass = password.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanReg.isEmpty else {
            errorMessage = "Please enter your registration number"
            HapticHelper.error()
            return
        }

        guard !cleanPass.isEmpty else {
            errorMessage = "Please enter your password"
            HapticHelper.error()
            return
        }

        CredentialStore.shared.saveCredentials(reg: cleanReg, pass: cleanPass)
        settingsStore.isOnboardingCompleted = true
        HapticHelper.success()
        onComplete()
    }
}
