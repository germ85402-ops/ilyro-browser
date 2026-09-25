"""Validate release metadata and public-facing project links.

This check is intentionally network-free so it can run in every CI job and on
the release workflow before any signing material is loaded.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def main() -> int:
    failures: list[str] = []

    def require(condition: bool, message: str) -> None:
        if not condition:
            failures.append(message)

    required_files = (
        "app/build.gradle.kts",
        "README.md",
        "CHANGELOG.md",
        "PRIVACY.md",
        "THIRD_PARTY_NOTICES.md",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties",
        ".github/workflows/ci.yml",
        ".github/workflows/release.yml",
        "docs/index.html",
        "docs/privacy.html",
        "docs/terms.html",
    )
    missing_files = [relative for relative in required_files if not (ROOT / relative).is_file()]
    if missing_files:
        print("Release readiness check failed:", file=sys.stderr)
        for relative in missing_files:
            print(f"- missing required file: {relative}", file=sys.stderr)
        return 1

    gradle = read("app/build.gradle.kts")
    version_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    code_match = re.search(r"versionCode\s*=\s*(\d+)", gradle)
    require(version_match is not None, "app versionName is not declared")
    require(code_match is not None, "app versionCode is not declared")

    version_name = version_match.group(1) if version_match else ""
    version_code = int(code_match.group(1)) if code_match else 0
    require(
        bool(re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", version_name)),
        f"invalid versionName: {version_name or '<empty>'}",
    )
    require(version_code > 0, f"invalid versionCode: {version_code}")
    require('applicationId = "com.ilyro.browser"' in gradle, "production applicationId changed")
    require(
        'org.mozilla.geckoview:geckoview:155.0.20260903215306' in gradle,
        "the pinned stable GeckoView dependency is missing or changed",
    )
    require(
        f"**{version_name}**" in read("README.md"),
        "README current status does not match app versionName",
    )
    require(
        f"## {version_name}" in read("CHANGELOG.md"),
        "CHANGELOG has no section for app versionName",
    )

    wrapper = read("gradle/wrapper/gradle-wrapper.properties")
    require(
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.6.0-bin.zip" in wrapper,
        "Gradle wrapper is not pinned to Gradle 9.6.0",
    )
    require(
        re.search(r"^distributionSha256Sum=[0-9a-f]{64}$", wrapper, re.MULTILINE) is not None,
        "Gradle wrapper distribution checksum is missing or malformed",
    )

    notices = read("THIRD_PARTY_NOTICES.md")
    require("GeckoView stable 155" in read("README.md"), "README does not identify stable GeckoView 155")
    require("geckoview:155.0.20260903215306" in notices, "third-party notices do not match stable GeckoView 155")
    require("Nightly" not in notices and "158.0.20260911092915" not in notices, "stale GeckoView Nightly notice remains")

    home = read("docs/index.html")
    privacy_page = read("docs/privacy.html")
    terms_page = read("docs/terms.html")
    require('href="privacy.html"' in home, "website home is missing the Privacy Policy link")
    require('href="terms.html"' in home, "website home is missing the Terms link")
    require("Last updated:" in privacy_page, "Privacy Policy has no update date")
    require("Effective date:" in terms_page, "Terms of Service has no effective date")
    require("Alex Agapitov" in terms_page and "Habet Hayrapetyan" in terms_page, "legal page is missing both developers")

    settings = read("app/src/main/java/com/ilyro/browser/ui/SettingsSheet.kt")
    require("Buy Me a Coffee" in settings, "About screen is missing the support entry")
    require("Alex Agapitov" in settings and "Habet Hayrapetyan" in settings, "About screen is missing both developers")
    require("BUY_ME_A_COFFEE_URL" in settings, "support URL placeholder is missing")
    require("privacy.html" in settings and "terms.html" in settings, "About screen is missing public legal links")

    language_store = read("app/src/main/java/com/ilyro/browser/ui/BrowserSettingsStore.kt")
    for language in (
        "SPANISH",
        "CHINESE",
        "HINDI",
        "PORTUGUESE",
        "ARABIC",
        "FRENCH",
        "GERMAN",
        "JAPANESE",
        "KOREAN",
        "TURKISH",
        "ITALIAN",
        "INDONESIAN",
    ):
        require(f"{language}(" in language_store, f"expanded app language is missing: {language}")

    release_workflow = read(".github/workflows/release.yml")
    for secret in (
        "ILYRO_RELEASE_KEYSTORE_BASE64",
        "ILYRO_RELEASE_STORE_PASSWORD",
        "ILYRO_RELEASE_KEY_ALIAS",
        "ILYRO_RELEASE_KEY_PASSWORD",
    ):
        require(secret in release_workflow, f"release workflow is missing secret {secret}")

    try:
        tracked = subprocess.run(
            ["git", "ls-files", "-z"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=False,
        ).stdout.decode("utf-8", errors="replace").split("\0")
    except (OSError, subprocess.CalledProcessError) as error:
        failures.append(f"could not inspect tracked files: {error}")
        tracked = []

    forbidden_suffixes = {".aab", ".apk", ".jks", ".keystore", ".p12", ".pfx", ".pem"}
    forbidden_names = {"local.properties", "google-services.json"}
    forbidden_tracked = []
    for relative in tracked:
        if not relative:
            continue
        path = Path(relative)
        lower = relative.lower()
        if (
            path.name.lower() in forbidden_names
            or path.suffix.lower() in forbidden_suffixes
            or "service-account" in lower
        ):
            forbidden_tracked.append(relative)
    require(not forbidden_tracked, f"private build material is tracked: {', '.join(forbidden_tracked)}")

    if failures:
        print("Release readiness check failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print(f"Release readiness metadata verified for ILYRO {version_name} ({version_code}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
