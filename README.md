# ILYRO Browser

ILYRO is an independent Android browser built with Kotlin, Jetpack Compose, and Mozilla GeckoView.

> Current status: **0.26.0-rc23**. This is a release candidate, not the final stable release.

## Highlights

- GeckoView browser engine
- Multiple tabs, session restoration, tab groups, pinning, and search
- Custom search engines with editable URL templates
- Bookmarks, browsing history, private tabs, and per-site desktop mode
- Built-in downloads with event-driven background progress and media/HLS support
- uBlock Origin integration through ILYRO Shield
- Optional dark-site mode powered by Dark Reader
- Fullscreen video and Picture-in-Picture support
- Optional Google account and Drive app-data synchronization
- Seven built-in wallpaper styles with dedicated phone and tablet artwork selected automatically, plus custom themes, launcher icons, toolbar placement, and interface density
- Android and website language preferences
- Password vault backed by Android Keystore
- Consistent motion with Android's reduced-animation preference respected
- High-contrast accent controls, a shared private-browsing palette, and a clear system sans-serif type scale
- Neutral graphite browser chrome that works with built-in and custom wallpapers

## Platform

- Android 8.0+ (minSdk 26)
- Target SDK 36
- Compile SDK 37.2
- ARM64 release APK
- Kotlin 2.4.20
- Jetpack Compose BOM 2026.08.00
- GeckoView stable 155, pinned to build `155.0.20260903215306`
- Android Gradle Plugin 9.4
- Gradle 9.6

## Downloads

Signed builds are published on the [GitHub Releases](https://github.com/germ85402-ops/ilyro-browser/releases) page. Verify the SHA-256 checksum supplied with each release before installing an APK.

## Build

The repository includes the verified Gradle 9.6.0 Wrapper. Run `./gradlew :app:assembleDebug` for a local debug build. GitHub Actions runs lint, unit tests, helper JavaScript checks, and debug APK compilation for code changes on `main` and pull requests. Version tags such as `v0.26.0-rc23` start the signed release workflow.

Release signing requires repository secrets and uses the permanent ILYRO signing identity documented in [docs/release-signing.md](docs/release-signing.md). Never commit a keystore or signing password.

## Documentation

- [Changelog](CHANGELOG.md)
- [Privacy policy](PRIVACY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)
- [Release signing identity](docs/release-signing.md)
- [Release checklist](docs/release-checklist.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

## License

ILYRO's original source code is available under the [Mozilla Public License 2.0](LICENSE). Bundled and referenced third-party components remain under their respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Mozilla, GeckoView, uBlock Origin, Dark Reader, Google, and Android trademarks belong to their respective owners. ILYRO is an independent project and is not endorsed by those projects or companies.
