# ILYRO Browser — Third-Party Notices

_Last reviewed: September 15, 2026_

ILYRO Browser incorporates or depends on third-party software. This notice is an RC/release-preparation inventory and should be reviewed against the exact dependency graph and packaged artifacts before public distribution.

## Mozilla GeckoView

ILYRO uses Mozilla GeckoView as its browser engine.

- Project: Mozilla GeckoView / Gecko
- License family: Mozilla Public License 2.0 for Mozilla-covered source code
- License text: https://www.mozilla.org/MPL/2.0/

Mozilla trademarks are not granted by the MPL. ILYRO is an independent browser project and is not presented as a Mozilla product.

## uBlock Origin

ILYRO's build pipeline downloads and bundles uBlock Origin 1.74.0 and applies a minimal ILYRO integration bridge for GeckoView.

- Project: uBlock Origin
- Upstream: https://github.com/gorhill/uBlock
- License: GNU General Public License v3.0 (GPL-3.0)

uBlock Origin also uses filter lists and other assets that can have their own licenses. The exact bundled/default filter-list notices should be preserved or made available as required by their respective licenses.

## AndroidX / Jetpack Compose

ILYRO uses AndroidX libraries including Core, Activity, SplashScreen, Credentials, Compose UI, Foundation and Material components.

AndroidX is an Android Open Source Project component set. Individual artifacts and source files carry their own license notices, commonly Apache License 2.0. Distribution should preserve notices required by the exact packaged artifacts.

## Google authentication libraries

ILYRO uses Google Identity / Credential Manager integration and Google Play Services authentication libraries for optional Google account authorization.

These components are distributed under Google's applicable SDK/library terms. Their inclusion does not imply endorsement of ILYRO by Google.

## Kotlin and build tooling

ILYRO is written in Kotlin and built with the Android Gradle Plugin and Gradle. Build-time and runtime components can include additional third-party libraries with their own licenses.

## Test-only dependencies

The repository uses test dependencies including JUnit and `org.json`. Test-only dependencies are not necessarily shipped in the release APK, but their licenses should remain documented in development/source distributions where applicable.

## Distribution checklist

Before publishing a public release:

1. Generate the final resolved dependency list for the exact release build.
2. Verify the license/notice requirements of every runtime dependency included in the APK/AAB.
3. Preserve required license texts and copyright notices.
4. Verify the exact uBlock Origin package and bundled filter-list licenses used by CI.
5. Re-check any modified or redistributed third-party source/code for source-offer or attribution obligations.

This file is an engineering inventory, not legal advice or a substitute for reviewing the licenses themselves.
