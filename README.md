# 🛡️ AetherNode — Open-Source Worldwide Free VPN for Android

**AetherNode** is a featherweight, transparent, open-source Android VPN client built with Kotlin, Jetpack Compose (Material 3), and Android's native `VpnService` API. It connects to worldwide free public relay nodes (including live synchronization with the academic **VPN Gate Public Relay Directory** and verified community endpoints) with one-tap simplicity.

---

## ✨ Core Features

- **One-Tap Encrypted Connectivity**:
  - Instant connection via a single central power orb or **Quick Fastest Node** auto-selection.
  - Native Android `VpnService` (`tun0` interface, `MTU 1400`, virtual IP `10.8.0.2/24`, foreground service notification with one-tap disconnect).
- **Worldwide Free Relay Nodes**:
  - Syncs live public servers from `https://www.vpngate.net/api/iphone/` alongside 16 verified global community nodes across Asia-Pacific, Europe, the Americas, and MEA.
  - **Concurrent TCP Latency Sweeps**: Measure real network round-trip latency (`ms`) across all nodes or ping individual servers.
  - Filter by region or starred favorites, sort by lowest ping/fastest bandwidth/uptime, inspect `.ovpn` profiles, or add custom community relay nodes.
- **Open-Source Transparency & Zero Tracking**:
  - Live cryptographic & socket audit log inside the app (`Open Audit` tab).
  - Selectable encrypted DNS resolvers (`Cloudflare 1.1.1.1`, `Quad9 9.9.9.9`, `AdGuard`, `OpenDNS`) and Safe Split-Tunnel routing controls.
- **Wide Android Compatibility**:
  - Supports **Android 8.0 Oreo (API 26)** up to **Android 16 (API 36)** (with backward compatibility down to `minSdk 24`).

---

## 🚀 Automated GitHub Repository & APK Release Flow

This repository includes a pre-configured **GitHub Actions CI/CD Release Pipeline** at [`.github/workflows/android-apk-release.yml`](.github/workflows/android-apk-release.yml).

### How to Push to GitHub & Trigger Automatic APK Releases

1. **Create / Push to GitHub from AI Studio**:
   - Click the **GitHub / Export** icon in the top toolbar of Google AI Studio and select **Push to GitHub** to create your repository and push this codebase.
2. **Automatic Release on Every Push**:
   - Whenever you push to `main`, `master`, `release/**`, or push a `v*` tag, GitHub Actions automatically runs `.github/workflows/android-apk-release.yml`.
   - The workflow:
     1. Runs unit tests (`gradle :app:testDebugUnitTest`).
     2. Builds the signed **Release APK**, **Debug APK**, and **Release AAB**.
     3. Computes **SHA-256** and **MD5** checksums and file sizes.
     4. Generates a complete commit changelog and device compatibility table.
     5. Publishes a new **GitHub Release** (`v1.0.<run_number>`) with all downloadable APKs and checksum manifests attached.

### Optional Custom Production Keystore Secrets

Out of the box, the workflow automatically generates a self-signed release keystore if no secrets are configured, so **your very first push will immediately publish a working signed APK release**.

To sign releases with your own persistent production keystore, add these optional Repository Secrets under **GitHub Repo → Settings → Secrets and variables → Actions**:
- `RELEASE_KEYSTORE_BASE64`: Base64-encoded `.jks` keystore (`base64 -w 0 my-upload-key.jks`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password (for alias `upload`)

---

## 🏗️ Project Architecture

```text
app/src/main/java/com/example/
├── MainActivity.kt              # Edge-to-edge M3 scaffold, VpnService permission flow & modals
├── model/
│   └── VpnModels.kt             # Clean data models for nodes, protocols, telemetry & audit logs
├── data/
│   └── PublicNodeRepository.kt  # Live VPN Gate CSV API parser, TCP ping sweeps & DataStore prefs
├── vpn/
│   ├── AetherVpnService.kt      # Android VpnService (TUN establishment, socket probe & packet loop)
│   └── AetherVpnStateBus.kt     # Reactive StateFlow bridge for telemetry & real-time audit logs
└── ui/
    ├── VpnViewModel.kt          # MVVM state holder for node filtering, latency testing & intents
    ├── ConnectScreen.kt         # Featherweight one-tap connect dashboard & live telemetry cards
    ├── NodeListScreen.kt        # Searchable, filterable worldwide free node browser
    └── OpenSourceAuditScreen.kt # DNS/routing controls, CI/CD release inspector & live audit log
```

---

## 🤝 Contributing

Community contributions, new public relay directories, and protocol enhancements are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details.
