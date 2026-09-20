# ILYRO Browser

ILYRO is a modern Android browser built with Kotlin, Jetpack Compose and Mozilla GeckoView.

## Current milestone — 0.25.0 RC

The current branch is focused on stability, regression coverage and release readiness rather than large new features. The RC baseline was created from app version 0.24.13.

- GeckoView-based browser engine
- Search/address field with URL normalization
- Multiple live tabs, tab switcher and Gecko SessionState restoration (history, scroll, zoom and form state)
- Tablet layouts include an ILYRO-styled persistent horizontal tab strip with real same-site favicons, cached for fast reuse, plus quick switching, close controls and one-tap new tab
- Tab search, pinning and groups
- Bookmarks and browsing history
- Private tabs with isolated session context; private tabs are excluded from normal tab restoration and Drive backup
- Built-in downloads with progress, background keep-alive and media/HLS support
- Direct media and HLS downloads surface their Android notification immediately and keep it continuous while the real transfer is prepared
- Interrupted ordinary direct downloads recover safely after process death instead of remaining stuck as Running
- Omnibox suggestions combine visited sites with search-engine suggestions; private tabs do not expose browsing history or send suggestion queries
- The home omnibox shows the active search engine and supports long-press engine switching
- Rounded interaction/ripple states across onboarding, home shortcuts and omnibox surfaces
- Wallpaper-driven interface accents and customizable home backgrounds, including no-wallpaper mode
- Selectable launcher icons
- Visual onboarding for theme, wallpaper and address-bar position; browser language follows Android by default
- Ordered website-language preferences are passed directly to GeckoView independently of the ILYRO UI translation set
- YouTube performance helper hides oversized Community/Post feed cards while preserving videos and Shorts
- Pull-to-refresh is guarded against nested/side site scrollers and has dedicated CI policy tests
- ILYRO-managed downloads show their system notification immediately and support pause/resume from both the Downloads UI and Android notifications
- Normal file downloads follow Gecko navigation and use GeckoView external responses, preserving redirects, cookies and the visible page
- APK/archive payload validation prevents HTML or challenge responses from being reported as completed files
- APK downloads preserve filename/MIME hints across redirects and generic .bin responses are upgraded to .apk when the completed payload is an Android package
- RFC 5987 Content-Disposition filenames are recognized, and completed Android DownloadManager .bin fallbacks are verified and converted to .apk only when the payload is a real Android package
- APK download naming has regression coverage for opaque redirects, generic binary MIME types and ordinary non-APK binary files
- Downloaded APK files open through the Android package installer, including the per-app unknown-source permission flow when required
- Gecko session activity and surfaces are restored across app background/foreground transitions to avoid blank or black tabs after resume
- Site permission prompts for camera, microphone, geolocation and supported Gecko permissions
- Per-site desktop mode and global desktop-mode default
- Native Gecko page translation with automatic language detection and on-device translation models
- Reader mode for article pages using Gecko's built-in `about:reader` experience
- Light, dark and system themes synchronized with GeckoView and Android system bars
- Extension hosting with bundled uBlock Origin support
- Fullscreen media and Picture-in-Picture support with Gecko MediaSession/PiP compositor integration
- Optional Google account + Google Drive appData sync for settings, history, bookmarks, quick links and normal tab metadata
- Tab previews are downscaled and LRU-capped to reduce RAM use with many tabs
- JVM regression tests, helper-JavaScript checks and an Android cold-launch instrumentation smoke test are part of RC CI
- Android 16 / API 36 target
- Compilation against Android API 37.2
- ARM64 release APK builds through GitHub Actions

## Stack

- Kotlin 2.4.20
- Jetpack Compose BOM 2026.08.00
- Mozilla GeckoView 154 stable
- Android Gradle Plugin 9.4
- Gradle 9.6
- Min SDK 26 / Target SDK 36 / Compile SDK 37.2

## Release preparation

- RC checklist: `docs/0.25.0-stability-checklist.md`
- Changelog: `CHANGELOG.md`
- Privacy-policy draft: `PRIVACY.md`
- Third-party notices: `THIRD_PARTY_NOTICES.md`

## Direction

ILYRO is being developed as a full Gecko-based mobile browser with a focus on stability, privacy, performance, media playback, reliable downloads, customizable browser chrome and strong content blocking.
