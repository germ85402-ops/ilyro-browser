from __future__ import annotations

from pathlib import Path
import hashlib
import json
import shutil
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/assets/ilyro-adblock"
BRIDGE = ROOT / "app/src/main/ubo/ilyro-native-bridge.js"
URL = "https://addons.mozilla.org/firefox/downloads/file/4981431/ublock_origin-1.74.0.xpi"
SHA256 = "175756d74468c9ba45863f7fc333d3be670f82d5b066314e915814dd547d1652"


def bundle_is_ready() -> bool:
    manifest_path = TARGET / "manifest.json"
    start_path = TARGET / "js/start.js"
    bridge_path = TARGET / "js/ilyro-native-bridge.js"
    if not (manifest_path.is_file() and start_path.is_file() and bridge_path.is_file()):
        return False
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        gecko = manifest.get("browser_specific_settings", {}).get("gecko", {})
        permissions = set(manifest.get("permissions", []))
        start = start_path.read_text(encoding="utf-8")
    except Exception:
        return False
    return (
        gecko.get("id") == "adblock@ilyro"
        and {"geckoViewAddons", "nativeMessaging"} <= permissions
        and "import './ilyro-native-bridge.js';" in start
    )


if bundle_is_ready():
    print("Bundled uBlock Origin assets already prepared.")
    raise SystemExit(0)

with tempfile.TemporaryDirectory(prefix="ilyro-ubo-") as tmp_dir:
    tmp = Path(tmp_dir)
    archive = tmp / "ublock.xpi"
    request = urllib.request.Request(URL, headers={"User-Agent": "ILYRO-build/1"})
    with urllib.request.urlopen(request, timeout=45) as response, archive.open("wb") as output:
        shutil.copyfileobj(response, output)

    with archive.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    if digest != SHA256:
        raise SystemExit(
            f"uBlock Origin checksum mismatch: expected {SHA256}, got {digest}"
        )

    extracted = tmp / "extracted"
    extracted.mkdir()
    with zipfile.ZipFile(archive) as bundle:
        bundle.extractall(extracted)

    manifest_path = extracted / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    gecko = manifest.setdefault("browser_specific_settings", {}).setdefault("gecko", {})
    gecko["id"] = "adblock@ilyro"

    permissions = manifest.setdefault("permissions", [])
    for permission in ("geckoViewAddons", "nativeMessaging"):
        if permission not in permissions:
            permissions.append(permission)

    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    bridge_dst = extracted / "js/ilyro-native-bridge.js"
    shutil.copyfile(BRIDGE, bridge_dst)

    start_path = extracted / "js/start.js"
    start = start_path.read_text(encoding="utf-8")
    import_line = "import './ilyro-native-bridge.js';"
    if import_line not in start:
        needle = "import µb from './background.js';"
        if needle not in start:
            raise SystemExit("Could not locate uBO background import anchor")
        start = start.replace(needle, needle + "\n" + import_line, 1)
        start_path.write_text(start, encoding="utf-8")

    if TARGET.exists():
        shutil.rmtree(TARGET)
    shutil.copytree(extracted, TARGET)

if not bundle_is_ready():
    raise SystemExit("Prepared uBlock Origin bundle failed validation")

print("Pinned uBlock Origin + ILYRO bridge prepared and verified.")
