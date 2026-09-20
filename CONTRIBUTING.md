# Contributing to ILYRO

Thank you for helping improve ILYRO Browser.

## Workflow

1. Open an issue for significant features or behavioral changes.
2. Create a short-lived branch from `main`.
3. Keep changes focused and avoid unrelated formatting rewrites.
4. Run the same checks as CI.
5. Open a pull request and describe the user-visible effect and test coverage.

## Local checks

The project currently uses Gradle 9.6:

```bash
python3 scripts/prepare_ublock.py
python3 scripts/prepare_darkreader.py
gradle :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
node app/src/test/js/gesture-policy.test.js
```

Never commit APKs, keystores, passwords, OAuth secrets, local configuration, or generated build directories.

## Release changes

Production releases are created from signed version tags by maintainers. Do not change the application ID or release signing identity.
