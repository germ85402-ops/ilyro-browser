# Changelog

## Unreleased

## 0.26.0-rc20 — release candidate

- Add four distinct built-in wallpaper styles, each with dedicated phone and tablet artwork selected automatically by device size.

## 0.26.0-rc19 — release candidate

- Replace the six previous built-in wallpapers with three ILYRO landscapes, each with portrait phone and wide tablet artwork selected automatically by device size.
- Preserve custom wallpapers and migrate saved selections of removed presets to the new Stillwater default.
- Keep only the three newest prereleases and their tags when publishing future releases.

## 0.26.0-rc18 — release candidate

- Choose readable foreground colors for accent controls based on actual contrast, including wallpaper-derived accents in dark mode.
- Unify private-browsing accents across the home page, address bar, toolbar, loading indicator, and tab overview; use a matching readable icon color on the private new-tab button.
- Bring dark surfaces closer to neutral graphite while retaining ILYRO's cool blue accent, and align utility-screen backdrops with the shared dark palette.
- Set a shared system sans-serif type scale for headings, body text, and labels without adding bundled font files.

## 0.26.0-rc17 — release candidate

- Unify animation timing and easing across onboarding, toolbar feedback, notices, tab cards, and loading indicators.
- Honor Android's system animation setting, including reduced or removed animations.
- Add directional transitions between mobile Settings categories and a subtle cross-fade on larger screens.
- Align tab-sheet and tab-dismissal timing, remove compounded close motion, and add a light haptic when a swipe closes a tab.

## 0.26.0-rc16 — release candidate

- Require screen-lock confirmation before Autofill releases a saved password, fail closed if the confirmation flow cannot launch, and match credentials to the domain of the field being filled.
- Keep site-icon requests first-party, private-tab aware, bounded, and isolated from page paths and queries; preserve non-default ports and reject cross-origin redirects in the fallback loader.
- Show explicit WebExtension permission prompts and bind each response to the exact request, preventing one prompt from approving another request.
- Keep ILYRO's own network requests on TLS and enable Gecko tracking protection explicitly.
- Wait for Drive restore writes off the UI thread before reading restored tabs, preventing old queued data from overwriting the restored snapshot.
- Reject unexpected restored APK-install URIs and include the remote sync snapshot validation fix.

## 0.26.0-rc15 — release candidate

- Size the YouTube fullscreen video container against the viewport instead of the shorter intermediate player, fixing the top-aligned video and bottom gap confirmed by on-device geometry diagnostics.
- Update the bundled fullscreen helper to 1.8.3. The user confirmed the fix in the debug build; signed-release device validation remains pending.

## 0.26.0-rc14 — release candidate

- Pin the Activity-owned GeckoView host to the full native window for the complete web-fullscreen lifetime instead of relying on transient Compose bounds during phone rotation.
- Keep recording normal Compose page bounds while fullscreen is active and restore them immediately on exit, preserving existing tablet orientation and input routing behavior.
- Reapply native fullscreen bounds whenever the root window changes size so YouTube cannot stay stuck on the pre-rotation portrait rectangle.

## 0.26.0-rc13 — release candidate

- Wait for the bundled media fullscreen helper before opening the first page, preventing release-only YouTube fullscreen sizing races.
- Make the website APK buttons resolve the newest published ARM64 release automatically, including prereleases.


## 0.26.0-rc12 — release candidate

- Re-measure the Compose content rectangle and native Gecko host after phone fullscreen rotation so YouTube expands to the full available landscape surface without cropping.
- Keep tablet orientation behavior unchanged and avoid restoring Gecko input focus or recreating the media surface during the layout refresh.

## 0.26.0-rc11 — release candidate

- Match Chrome-style fullscreen rotation on phones: enter landscape even when system auto-rotate is off, then restore the previous orientation on exit.
- Keep tablets and larger screens in their current orientation during fullscreen playback.
- Keep the Gecko media surface attached across configuration changes instead of recreating the display, reducing pauses and startup buffering.
- Separate media-surface restoration from Gecko input focus to prevent fullscreen transitions from reopening the keyboard.
- Improve YouTube fullscreen sizing across phones and tablets with dynamic viewport sizing while preserving aspect ratio.


## 0.26.0-rc10 — release candidate

- Include the complete mobile YouTube/fullscreen surface fix in the release tree.
- Keep playing YouTube media through transient focus and orientation changes without recreating the surface unnecessarily.
- Restore playback only when Android interrupted a video that was already playing.
- Preserve the stale-tab-surface fix while switching tabs.

## 0.26.0-rc9 — release candidate

- Refine mobile YouTube/fullscreen surface restoration to reduce black frames and accidental playback interruptions.
- Prevent stale browser surfaces from flashing when switching between tabs.
- Show a compact in-browser notice when a download finishes, with a shortcut to Downloads.
- Replace the support card's coffee icon with a cleaner theme-aware cup, saucer, coffee and steam illustration.

## 0.26.0-rc8 — release candidate

- Add custom search engines in Settings with editable names, URL templates, default selection, and local/sync persistence.
- Disable remote suggestions for custom engines and keep compact mobile settings and notice layouts readable.


## 0.26.0-rc7 — release candidate

- Report password-vault read and save failures to Autofill and Gecko instead of treating them as an empty vault or a successful save.
- Replace the fixed download-screen polling loop with download progress events and cache recovered MediaStore sizes between real state changes.
- Pause disallowed downloads promptly when the active network becomes metered, including closing a currently blocked stream read.
- Protect Google Drive sync uploads with conditional `If-Match` updates and retryable remote-conflict handling.

## 0.26.0-rc6 — release candidate

- Fix YouTube fullscreen sizing so the video uses the available surface while staying centered, uncropped, and aspect-preserving.
- Keep YouTube Ambient Mode visible around videos with different aspect ratios.
- Apply the selected or system browser theme to supported websites immediately, including when the resolved appearance stays the same while the setting changes.


## 0.26.0-rc5 — release candidate

- Center fullscreen videos without stretching them and preserve the ambient background glow.
- Make the About screen support action a direct Buy Me a Coffee link with a clearer call-to-action.
- Refresh the developers block with a compact, consistent presentation.


## 0.26.0-rc4 — release candidate

- Remove YouTube media detection and downloading so the media sheet remains focused on other websites.
- Remove the unused yt-dlp/FFmpeg fallback and its native runtime payload from the APK.
- Keep direct video/audio and supported HLS downloads for non-YouTube sites.

## 0.26.0-rc2 — release candidate

- Complete the production-readiness pass for Google account synchronization without changing the existing OAuth client, package identity, signing identity, or Drive app-data scope.
- Protect local settings and open tabs from being silently overwritten when another device changes them at the same time.
- Show background sync failures, retry recoverable errors with exponential backoff, and add a confirmed action to delete the Google Drive backup.
- Move the browser engine from GeckoView Nightly to Mozilla's stable GeckoView 155 release.
- Refresh the public third-party notice so it matches the stable GeckoView dependency used by the app.
- Add direct Privacy Policy and Terms of Service links to the About screen.
- Add global browser-interface language choices with Android System detection and a compact picker.
- Keep website-language preferences independent from the app interface language and preserve them in sync.

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
