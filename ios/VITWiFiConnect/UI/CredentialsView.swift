import SwiftUI

public struct CredentialsView: View {

    @Environment(\.presentationMode) var presentationMode
    @ObservedObject var settingsStore = SettingsStore.shared

    @State private var regNumber: String = ""
    @State private var password: String = ""
    @State private var isPasswordVisible: Bool = false
    @State private var errorMessage: String?
    @State private var showSavedAlert: Bool = false

    public init() {}

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // Subtle background glow
            Circle()
                .fill(settingsStore.currentPalette.primaryColor.opacity(0.12))
                .blur(radius: 90)
                .offset(y: -140)

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

                    Text("Credentials")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)

                    Spacer()

                    // Balance spacing
                    Color.clear.frame(width: 38, height: 38)
                }
                .padding(.top, 16)

                Text("Campus Authentication")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(.white)

                Text("Your registration number and password are used strictly to authenticate with the campus Wi-Fi gateway. Saved locally on device.")
                    .font(.system(size: 14))
                    .foregroundColor(.gray)
                    .lineSpacing(4)

                // Input card
                VStack(spacing: 16) {
                    // Registration number field
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Registration number")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.gray)

                        HStack {
                            Image(systemName: "person.fill")
                                .foregroundColor(.gray)
                                .frame(width: 20)

                            TextField("e.g. 21BCE1001", text: $regNumber)
                                .textCase(.uppercase)
                                .autocapitalization(.allCharacters)
                                .disableAutocorrection(true)
                                .foregroundColor(.white)
                        }
                        .padding(14)
                        .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.10), lineWidth: 1))
                    }

                    // Password field
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Password")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.gray)

                        HStack {
                            Image(systemName: "lock.fill")
                                .foregroundColor(.gray)
                                .frame(width: 20)

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
                        .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.10), lineWidth: 1))
                    }
                }
                .padding(20)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
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

                if let err = errorMessage {
                    Text(err)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.red)
                        .padding(.horizontal, 4)
                }

                Spacer()

                // Save button
                Button(action: saveAction) {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 16, weight: .semibold))
                        Text("Save Credentials")
                    }
                }
                .buttonStyle(AppleMusicPrimaryButtonStyle(
                    primaryColor: settingsStore.currentPalette.primaryColor,
                    secondaryColor: settingsStore.currentPalette.secondaryColor,
                    textColor: settingsStore.currentPalette.id == "titanium" ? Color(red: 13/255, green: 13/255, blue: 16/255) : .white
                ))
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 20)
        }
        .onAppear {
            if let savedReg = CredentialStore.shared.getRegNumber() {
                regNumber = savedReg
            }
            if let savedPass = CredentialStore.shared.getPassword() {
                password = savedPass
            }
        }
    }

    private func saveAction() {
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

        let saved = CredentialStore.shared.saveCredentials(reg: cleanReg, pass: cleanPass)
        if saved {
            HapticHelper.success()
            presentationMode.wrappedValue.dismiss()
        } else {
            errorMessage = "Failed to save credentials to local storage"
            HapticHelper.error()
        }
    }
}
