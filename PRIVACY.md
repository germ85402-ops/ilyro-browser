# ILYRO Browser Privacy Policy

_Last updated: September 20, 2026_

This document describes the privacy behavior of the current ILYRO Browser beta/RC codebase. It should be reviewed again before public store distribution, especially if analytics, advertising, cloud services, account features, or other data flows are added later.

## Summary

ILYRO is designed so ordinary browsing data stays on the user's device unless the user explicitly uses a feature that needs a network service, such as visiting a website, using a search engine, signing in with Google, or backing up browser data to Google Drive.

The current ILYRO codebase does not include ILYRO-operated analytics, advertising SDKs, Crashlytics, Sentry, or an ILYRO telemetry backend.

## Browsing and website data

Web pages are loaded through Mozilla GeckoView. Websites and services the user visits can receive normal web-request information such as the requested URL, IP address, cookies, headers, browser capabilities, and data the user submits to those sites.

ILYRO stores browser data locally where required for browser functionality, including items such as:

- browsing history, when history is enabled;
- bookmarks and quick links;
- normal-tab session restoration data;
- browser settings and personalization choices;
- site permissions and site-specific browser settings;
- downloads and download state.

Private tabs use a private Gecko session context. Private-page navigation is not added to ILYRO's normal browsing history, and private tabs are intentionally excluded from normal tab-session restoration and ILYRO Drive backup data.

## Saved passwords and data transfer

Saved passwords are stored in an application-private vault encrypted with AES-GCM. The encryption key is generated and kept in Android Keystore and is not stored in ILYRO settings or uploaded to ILYRO-operated servers.

ILYRO does not include saved passwords in Google Drive sync. The app disables Android backup and also declares explicit Android 12+ data-extraction rules that exclude application data from cloud backup and device-to-device transfer.

Revealing or editing a saved password requires Android device authentication when a device screen lock is configured. A revealed password is hidden again automatically after a short period.

Password CSV import is processed in memory. ILYRO does not keep a plaintext app copy of an imported CSV. Password CSV export is an explicit user action, requires device authentication, and writes plaintext credentials only to the document location selected by the user. Exported CSV files are not encrypted by ILYRO, so users should store and share them carefully.

## Search and address suggestions

When the user performs a search, the query is sent to the search provider selected in ILYRO. If remote search suggestions are enabled/used by the selected provider, suggestion text may be sent to that provider as part of the feature.

Private tabs are designed not to expose local browsing-history suggestions or send remote suggestion queries through ILYRO's omnibox suggestion feature.

## Google account and Google Drive sync

Google account features are optional. When the user chooses to sign in and authorize sync, ILYRO can use Google authentication and the Google Drive `appDataFolder` area to upload and restore an ILYRO browser snapshot.

The current sync snapshot can include:

- browser settings;
- browsing history;
- bookmarks;
- quick links;
- normal tab URLs, pin state, group names, and active-tab index.

The current sync format intentionally excludes:

- passwords;
- website cookies and authenticated website sessions;
- private tabs;
- downloads and downloaded files;
- Gecko serialized page/session state.

Google's own terms and privacy practices apply to Google authentication and Google Drive services.

## Permissions

ILYRO may request Android permissions when a website or browser feature needs them, including camera, microphone, location, notifications, and file/camera access. Site permission prompts are shown by ILYRO/GeckoView before supported website permissions are granted.

Permission choices can also be controlled through Android system settings and ILYRO's site controls where available.

When the user explicitly chooses to open a downloaded APK, ILYRO can ask Android to allow package installation from ILYRO. Package installation is initiated and confirmed by the user; ILYRO does not silently install APK files.

## Downloads

Files downloaded through ILYRO are stored on the user's device. Direct and media/HLS downloads can use Android foreground-service notifications so transfer progress remains visible while a download is active.

ILYRO does not upload downloaded files to an ILYRO server.

## Content blocking

ILYRO bundles uBlock Origin integration for content blocking. uBlock Origin is a third-party open-source project. Its filter lists and related assets can involve network requests according to uBlock Origin's own behavior and configuration.

## Data sharing by ILYRO

The current codebase does not contain an ILYRO-operated advertising network, analytics collector, or telemetry server to which ordinary browsing history is intentionally uploaded.

Network data can still be sent to third parties when the user uses their services, including websites, selected search engines, Google account/Drive services, extension/filter-list infrastructure, and other destinations requested by the user.

## Data deletion

Users can clear supported local browser data from ILYRO settings. Android application-data controls can also remove the app's local data. Google Drive sync data can be removed through the account/sync controls when that action is available and authorized.

## Changes to this policy

This policy must be updated when ILYRO adds or materially changes data collection, synchronization, analytics, advertising, account, or cloud functionality.

## Contact

For privacy questions or support requests, open an issue at https://github.com/germ85402-ops/ilyro-browser/issues. Do not include passwords, authentication codes, browsing history, or other sensitive personal data in a public issue.
