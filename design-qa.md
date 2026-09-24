# Design QA

## Previous review

- Selected visual target: option 3, `/workspace/scratch/b269e941acbb/generated_images/exec-d311f2a3-6c7d-4ed5-b2d8-435900f93afe.png` (1487 × 1058).
- Prototype capture was blocked because the preview runtime could not load `live-server`; visual and browser interaction checks were not performed.
- Static checks passed for JavaScript syntax, local HTML asset references, the app icon, and `git diff --check`.
- Translated screenshot assets were reviewed; the site uses the official deer icon and groups English screenshots under `docs/screenshots/`.

## Current review — 2026-09-24

References: selected tablet direction 2 and phone direction 5, each shown in light and dark themes.

| Severity | Count |
|---|---:|
| P0 | 0 |
| P1 | 0 |
| P2 | 0 |
| P3 | 0 |

### Checks

- Tablet layout: 4 benefit cards across, full navigation, copy before browser screenshot, no horizontal overflow.
- Phone layout: browser screenshot before copy and download action, stacked benefits, menu navigation works, no horizontal overflow.
- Light and dark modes render with matching browser chrome colors; the theme control cycles through system, light, and dark.
- Russian and English controls work; the Russian phone heading fits its content width.
- The hero screenshot and both theme wallpapers load in the local preview.
- No console errors or warnings were recorded from the preview page origin.
- `node --check docs/script.js`, HTML parsing for `docs/index.html`, and `git diff --check` pass.

The responsive browser frame reserves 15 CSS pixels for its scrollbar, so checked content widths were 753 px for the 768 px tablet frame and 375 px for the 390 px phone frame.

Preview: http://terminal.local:4173/
