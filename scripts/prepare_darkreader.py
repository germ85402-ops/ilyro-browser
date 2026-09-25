from __future__ import annotations

from pathlib import Path
import hashlib
import json
import shutil
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/assets/ilyro-darkreader"
URL = "https://github.com/darkreader/darkreader/releases/download/v4.9.132/darkreader-firefox.xpi"
SHA256 = "969b7216228c0cff1c00fcdde2411525c6e0bd39c305f41f27e4b84c4a080edc"


def bundle_is_ready() -> bool:
    manifest_path = TARGET / "manifest.json"
    if not manifest_path.is_file():
        return False
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception:
        return False
    gecko = manifest.get("browser_specific_settings", {}).get("gecko", {})
    permissions = set(manifest.get("permissions", []))
    return gecko.get("id") == "darkreader@ilyro" and "geckoViewAddons" in permissions


if bundle_is_ready():
    print("Bundled Dark Reader assets already prepared.")
    raise SystemExit(0)

with tempfile.TemporaryDirectory(prefix="ilyro-darkreader-") as tmp_dir:
    tmp = Path(tmp_dir)
    archive = tmp / "darkreader.xpi"
    request = urllib.request.Request(URL, headers={"User-Agent": "ILYRO-build/1"})
    with urllib.request.urlopen(request, timeout=45) as response, archive.open("wb") as output:
        shutil.copyfileobj(response, output)

    with archive.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    if digest != SHA256:
        raise SystemExit(
            f"Dark Reader checksum mismatch: expected {SHA256}, got {digest}"
        )

    extracted = tmp / "extracted"
    extracted.mkdir()
    with zipfile.ZipFile(archive) as bundle:
        bundle.extractall(extracted)

    manifest_path = extracted / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    gecko = manifest.setdefault("browser_specific_settings", {}).setdefault("gecko", {})
    gecko["id"] = "darkreader@ilyro"

    applications = manifest.get("applications")
    if isinstance(applications, dict):
        applications.setdefault("gecko", {})["id"] = "darkreader@ilyro"

    permissions = manifest.setdefault("permissions", [])
    if "geckoViewAddons" not in permissions:
        permissions.append("geckoViewAddons")

    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    if TARGET.exists():
        shutil.rmtree(TARGET)
    shutil.copytree(extracted, TARGET)

if not bundle_is_ready():
    raise SystemExit("Prepared Dark Reader bundle failed validation")

print("Pinned Dark Reader bundle prepared and verified.")
