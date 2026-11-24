# VIT WiFi Connect

A zero-click campus Wi-Fi and captive portal authenticator designed for high reliability, complete on-device privacy, and a refined liquid glass interface.

---

## Downloads

- **Android App**: [`VIT_WiFi_Connect.apk`](./VIT_WiFi_Connect.apk) (Direct installation for Android 7.0+)
- **iOS Package**: [`VITWiFiConnect.ipa`](./VITWiFiConnect.ipa) (Direct installation package for iOS)

---

## Why VIT WiFi Connect

Unlike traditional portal login approaches and other campus utilities, this project focuses on seamless system-level integration:

### 1. Control Center Quick Settings Tile (1-Tap Connect & Disconnect)
- **Zero App Launches**: Authenticate directly from your notification panel or Quick Settings shade.
- **Smart Radio Recovery**: Automatically turns on your Wi-Fi radio if disabled, associates with the campus network, and completes portal login.
- **Clean Gateway Session Termination**: Disconnecting through the tile sends an explicit logout signal to the gateway, releasing your hardware MAC session immediately so other devices can connect.
- **Long Press Shortcut**: Holding the tile opens the application directly.

### 2. Home Screen Widget
- Compact home screen widget offering live connectivity status and immediate one-tap connect/disconnect actions.

### 3. Liquid Glass Visual Craft
- Built with a dark obsidian canvas (`#08090C`), frosted squircle cards, 1px top specular highlights, and 6 switchable color themes.
- Responsive micro-spring button animations with tactile feedback.

### 4. 120 FPS High Refresh Rate Support
- Supports native high-refresh rate displays (120Hz) on supported Android and iOS devices for smooth transitions and visualizer graphs.

### 5. Zero-Drain Background Mode
- Background monitoring completely pauses once a stable connection is verified, guaranteeing 0% background battery and memory usage.

### 6. Campus Network Disconnect Prevention
- Guides users to configure device MAC addressing over randomized MACs to prevent periodic router-side session drops on campus access points.

### 7. Hardware Keystore Encryption
- Credentials are encrypted on-device via Android Keystore (AES-256-GCM) and iOS Keychain.
- Zero analytics, zero tracking, and zero external network traffic. Communication occurs solely between your device and the local gateway.

---

## Installation

### Android
1. Download [`VIT_WiFi_Connect.apk`](./VIT_WiFi_Connect.apk).
2. Tap the downloaded file to install.
3. Open the app, save your registration number and password once, and tap **Connect**.
4. Edit your Quick Settings panel to add the **VIT WiFi** tile for 1-tap usage.

### iOS
1. Download [`VITWiFiConnect.ipa`](./VITWiFiConnect.ipa).
2. Sideload via your preferred tool (Sideloadly, AltStore, or TrollStore).
3. Trust the developer profile under **Settings > General > VPN & Device Management**.

---

## Architecture

- **Android**: Kotlin, Coroutines, AndroidX Security Crypto, Material 3, Custom Rendered Shader Views.
- **iOS**: Swift, SwiftUI, Network.framework, Security Keychain Services.
- **Authentication**: Multi-endpoint captive portal state machine with automatic fallback.

---

## License

This software is released under the MIT License.
