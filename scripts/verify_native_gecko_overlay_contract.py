import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/ilyro/browser/MainActivity.kt"
APP = ROOT / "app/src/main/java/com/ilyro/browser/ui/IlyroApp.kt"

main = MAIN.read_text(encoding="utf-8")
app = APP.read_text(encoding="utf-8")


def fail(message: str) -> None:
    raise SystemExit(f"Native Gecko overlay contract failed: {message}")


if re.search(
    r"NativeBrowserHost\\.install\\(\\s*this(?:\\s*,|\\s*\\))",
    main
) is None:
    fail("MainActivity is not using the Activity-owned native Gecko host")

if "NativeBrowserHostCoordinator.setBounds(" not in app:
    fail("Compose is not reporting browser-content bounds to the native Gecko host")

if "NativeBrowserHostCoordinator.setInputEnabled(" not in app:
    fail("Compose is not controlling native Gecko input routing")

outer_expected = (
    "color = if (onboardingComplete) Color.Transparent else "
    "MaterialTheme.colorScheme.background"
)
if outer_expected not in app:
    fail("IlyroApp root is not transparent while the browser is active")

browser_start = app.find("private fun BrowserScreen(")
if browser_start < 0:
    fail("BrowserScreen was not found")

surface_start = app.find("    Surface(", browser_start)
if surface_start < 0:
    fail("BrowserScreen root Surface was not found")

surface_end = app.find(") {", surface_start)
if surface_end < 0:
    fail("BrowserScreen root Surface could not be parsed")

root_surface = app[surface_start:surface_end + 3]
if "color = Color.Transparent" not in root_surface:
    fail(
        "BrowserScreen root became opaque and would cover the Activity-owned GeckoView; "
        f"root Surface was: {root_surface!r}"
    )

if "Spacer(modifier = Modifier.fillMaxSize())" not in app:
    fail("Compose web-content slot is not the transparent geometry placeholder expected by native Gecko")

print("Native Gecko/Compose transparency contract verified: browser root remains transparent.")
