# Contributing to AetherNode VPN

Thank you for helping keep **AetherNode** lightweight, transparent, and open-source!

## Code Style & Architecture Principles

1. **Zero Tracking & Transparency**: Never introduce analytics SDKs, telemetry trackers, or closed-source binary blobs. Every network action should emit a human-readable entry to `AetherVpnStateBus.appendLog(...)` so users can inspect it in the **Open Audit** tab.
2. **Lightweight Footprint**: Keep dependencies minimal in `app/build.gradle.kts` to maintain fast build times and a small APK size across Android 8.0 – Android 16+.
3. **Adding Verified Community Relay Nodes**:
   - Open `app/src/main/java/com/example/data/PublicNodeRepository.kt` and add verified public endpoints to `getVerifiedCommunityNodes()`, ensuring accurate country ISO codes, ports, and OpenVPN/WireGuard cipher descriptions.
4. **Testing Before Pushing**:
   - Run unit tests via `gradle :app:testDebugUnitTest` and verify compilation via `gradle :app:assembleDebug`.
   - Every push to `main` automatically triggers `.github/workflows/android-apk-release.yml` to build and publish a new GitHub Release with signed APKs and SHA-256 checksums.
