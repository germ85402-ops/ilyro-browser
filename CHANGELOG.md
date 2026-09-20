# Changelog

## Unreleased

- Protect local settings and open tabs from being silently overwritten when another device changes them at the same time.
- Show background sync failures, retry recoverable errors with exponential backoff, and add a confirmed action to delete the Google Drive backup.
- Move the browser engine from GeckoView Nightly to Mozilla's stable GeckoView 155 release.
- Refresh the public third-party notice so it matches the stable GeckoView dependency used by the app.

## 0.26.0-rc1 — release candidate

First 0.26 release candidate. This cycle adds a first-party password manager and browser-data migration tools, with device-tested save/autofill flows and a final migration UI polish pass.

### Password manager

- Add an application-private AES-GCM password vault with its encryption key kept in Android Keystore.
- Integrate saved logins with GeckoView `Autocomplete.StorageDelegate` on ILYRO's existing Gecko runtime.
- Add explicit save/update/select confirmation instead of silently persisting credentials.
- Prevent newly entered private-browsing credentials from being written to the normal vault.
- Add a full password manager screen with search, masked credentials, delete, device-authenticated reveal and manual edit.
- Automatically hide revealed passwords after 30 seconds.
- Replace system-looking login prompts with ILYRO-styled Compose sheets for save/update and account selection.
- Quarantine unreadable/corrupted vault files so future password saves can recover with a clean encrypted vault.

### Import / export

- Import password CSV files from Chromium-family browsers and Firefox with validation, preview and deduplication.
- Export saved passwords to CSV only after an explicit plaintext warning and Android device authentication.
- Read imported CSV files in memory and write exports directly to the user-selected document without keeping a plaintext app copy.
- Import and export bookmarks using the standard Netscape HTML format used by Chrome, Firefox, Edge and Brave.
- Group password and bookmark migration controls into a dedicated ILYRO `Data transfer` section.

### Verification

- Harden Android 15+ `dataSync` foreground-service timeout handling so resumable Gecko/HLS downloads are persisted as paused before the service stops, and rejected foreground starts no longer leave a phantom starting notification.
- Added JVM coverage for password CSV parsing/export, exact-host matching, vault serialization and bookmark HTML round trips.
- Added Android instrumentation coverage for corrupted-vault quarantine and recovery behavior.
- dev2/dev3/dev4 were device-tested on the primary Android tablet for save/update/select/autofill, reveal/edit/device-auth, import/export and restart persistence.
- CI compiles the Android instrumentation suite and verifies the final APK still contains the bundled uBlock Origin integration.

## 0.25.0

Stability and release-readiness milestone. RC3 passed the manual P0 regression gate and was merged into `main`.

### Stability

- Fix a YouTube Desktop mode crash/exit caused by `m.youtube.com` and `www.youtube.com` using different per-site Desktop-mode persistence keys. All canonical YouTube hosts now share one `youtube.com` override, preventing the mobile/desktop rewrite feedback loop.
- Recover interrupted ordinary direct downloads as `Paused` after Android process death instead of leaving stale `Running` state.
- Remove interrupted HLS partial records/files after process death when their segmented worker can no longer be resumed safely.
- Harden pull-to-refresh ownership so nested/side scroll containers, overscroll traps and vertical-pan regions keep their gestures.
- Preserve existing Gecko fullscreen/PiP media behavior while auditing lifecycle transitions.

### Regression coverage

- Added a regression test proving `m.youtube.com`, `www.youtube.com` and `youtube.com` share one per-site Desktop mode override.
- Added browser settings persistence/migration coverage, including launcher icon/theme state.
- Added direct-download process-death recovery tests.
- Added per-site desktop-mode persistence and host-normalization tests.
- Expanded tab-session restore coverage for malformed v2 fallback, active-index clamping, metadata persistence and record/state alignment.
- Added a cold-launch Android instrumentation smoke test and compile the instrumentation APK in CI.
- Added JavaScript syntax checks and pull-to-refresh policy tests to CI.

### Repository / CI

- Removed obsolete one-shot migration/fix workflows from the RC branch.
- Retained the long-lived primary Android build workflow and removed obsolete one-shot/cleanup workflows before public preparation.
- Added an RC stability checklist and release exit criteria.
- Added privacy-policy and third-party-notice drafts for public-beta preparation.

## 0.25.0-rc1

First stabilization test candidate. Device testing exposed the YouTube Desktop mode host-normalization blocker fixed before RC3.

## 0.24.13

Baseline used to start the 0.25 stabilization branch.

- Fixed pink launcher-icon scaling.
- Restored the selected startup theme reliably.

Earlier 0.24.x builds introduced and refined the full-screen settings/tab UI, selectable launcher icons, wallpaper personalization, Google account/Drive sync, per-site desktop mode, site permissions, downloads/media handling, Gecko page tools, tab grouping/pinning/search, YouTube-specific presentation fixes, theme restoration, PiP/fullscreen integration and other browser-polish work.
