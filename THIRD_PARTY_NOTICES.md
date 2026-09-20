# ILYRO Browser — Third-Party Notices

_Last reviewed: September 20, 2026_

ILYRO Browser incorporates or depends on third-party software. Each component remains subject to its own license and trademark terms.

## Mozilla GeckoView

ILYRO uses a pinned Mozilla GeckoView Nightly build as its browser engine.

- Project: Mozilla GeckoView / Gecko
- Pinned artifact: `org.mozilla.geckoview:geckoview-nightly:158.0.20260911092915`
- License family: Mozilla Public License 2.0 for Mozilla-covered source code
- License: https://www.mozilla.org/MPL/2.0/

Mozilla trademarks are not granted by the MPL. ILYRO is independent and is not presented as a Mozilla product.

## uBlock Origin

The build pipeline downloads and bundles uBlock Origin 1.74.0, verifies its SHA-256 digest, and applies a minimal ILYRO GeckoView bridge.

- Upstream: https://github.com/gorhill/uBlock
- License: GNU General Public License v3.0
- Bundled source package: https://addons.mozilla.org/firefox/addon/ublock-origin/

uBlock Origin filter lists and other assets may carry separate licenses. Their notices and source information must be preserved as required.

## Dark Reader

The build pipeline downloads and bundles Dark Reader 4.9.132, verifies its SHA-256 digest, and assigns an ILYRO-specific Gecko add-on ID.

- Upstream: https://github.com/darkreader/darkreader
- License: MIT
- Pinned package: `darkreader-firefox.xpi` from release `v4.9.132`

## AndroidX and Jetpack Compose

ILYRO uses AndroidX components including Core, Activity, SplashScreen, Credentials, Compose UI, Foundation, and Material. Individual artifacts commonly use the Apache License 2.0. Required notices distributed inside upstream artifacts must be preserved.

## Google authentication libraries

ILYRO uses Google Identity, Credential Manager, and Google Play Services authentication libraries for optional Google account authorization and Drive app-data synchronization. These components are governed by Google's applicable SDK and service terms. Their inclusion does not imply endorsement by Google.

## Kotlin and build tooling

ILYRO is written in Kotlin and built with the Android Gradle Plugin and Gradle. Build-time and runtime components may contain additional third-party notices.

## Verification

The release workflow records the resolved Gradle dependency graph for the exact release build and publishes it with the workflow artifacts. Maintainers must review material dependency or bundled-extension changes before publishing a stable release.

This file is an engineering notice, not legal advice.
