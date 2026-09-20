# ILYRO release checklist

Use this checklist before publishing a public release or release candidate.

The automated metadata gate can be run locally with
`python3 scripts/verify_release_readiness.py`. It also runs in Android CI, the
release workflow, and the GitHub Pages workflow.

## Repository

- [ ] Android CI is green for the code revision being released.
- [ ] The Gradle Wrapper JAR/distribution validation passes with the pinned Gradle version and checksum.
- [ ] Only intended long-lived branches and workflows remain.
- [ ] No APKs, keystores, signing passwords, local.properties, service-account files, or other private build material are committed.
- [ ] Commit metadata is suitable for a public repository (use a GitHub noreply address if the maintainer's personal email should remain private).
- [ ] GitHub Private vulnerability reporting is enabled, or SECURITY.md accurately describes the available private reporting path.
- [ ] GitHub Pages is configured before publishing the website.

## Version and signing

- [ ] app/build.gradle.kts has the intended versionCode and versionName.
- [ ] The release tag exactly matches versionName with a leading v.
- [ ] Repository Actions secrets exist for:
  - ILYRO_RELEASE_KEYSTORE_BASE64
  - ILYRO_RELEASE_STORE_PASSWORD
  - ILYRO_RELEASE_KEY_ALIAS
  - ILYRO_RELEASE_KEY_PASSWORD
- [ ] The release certificate fingerprint matches docs/release-signing.md.
- [ ] No replacement signing key is introduced accidentally.

## Verification

- [ ] Android lint passes.
- [ ] JVM unit tests pass.
- [ ] Debug APK and Android test APK compile.
- [ ] Bundled uBlock Origin and Dark Reader manifests pass their pinned integrity checks.
- [ ] Cold launch and first-run onboarding work on a clean install.
- [ ] Light/dark/system theme behavior works on first launch and after restart.
- [ ] Tabs, restore, close/undo, home preview, bookmarks, history, and downloads work.
- [ ] Password save/update/select, autofill, reveal/edit, import/export, and restart persistence work.
- [ ] Private browsing does not persist private history, private tabs, or new private credentials.
- [ ] Fullscreen/PiP and return from an external media player work.
- [ ] Site permissions and file/camera prompts work.
- [ ] Google sign-in/Drive sync works when enabled.
- [ ] ILYRO Shield/uBlock and dark-site mode work.
- [ ] Launcher icon switching and the selected startup appearance work on a clean restart.

## Release publication

- [ ] Create a signed tag only after all checks above are complete.
- [ ] The GitHub Release is marked as a pre-release when versionName contains a suffix such as -rc1.
- [ ] Release assets include the signed ARM64 APK, SHA-256 checksum, LICENSE, THIRD_PARTY_NOTICES.md, and release dependency list.
- [ ] Install the published APK from GitHub Releases and verify its signing certificate.
- [ ] Confirm the website and README download links point to the published release.
