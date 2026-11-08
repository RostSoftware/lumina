# Design system (Android)

The tokens are defined in the root [`AGENTS.md`](../../AGENTS.md) ("Shared Design System") and implemented in
`core/designsystem`. They must stay identical to `lumina-web/src/index.css`.

| Piece | Location |
| --- | --- |
| Colors (light/dark) | `theme/Color.kt` — `LightLuminaColors`, `DarkLuminaColors`, `brandGradient()` |
| Typography (Inter variable font, sp-based) | `theme/Type.kt`, `res/font/inter_variable.ttf` (OFL, see `docs/INTER_LICENSE.txt`) |
| Spacing / sizes / shapes / motion | `theme/Dimens.kt` (`LuminaDimens`, `LuminaShapes`, `LuminaMotion`) |
| Theme + M3 mapping | `theme/Theme.kt` — `LuminaTheme(themeMode)`; **dynamic color is off** |
| Icons | `icon/LucideGlyphs.kt` (generated), `icon/LuminaIcon.kt` |
| Components | `component/` — top/bottom bars, `ContentColumn`, buttons, text field, switch, chip, settings row, skeletons, empty/error states, bottom sheet, avatar, wordmark |

Use `LuminaTheme.colors.*` and `LuminaTheme.typography.*`. Never hardcode colors (photo overlays may use white/black).

## Theme

Light / dark / system, chosen in Settings → Appearance (`SettingsRepository.setTheme`). `MainActivity` switches the
status/navigation bar icon style to match. The window background and the splash screen follow the system night
mode (`values-night/colors.xml`).

## Icons

Lucide (ISC) glyphs are converted from `lucide-static` SVGs into path data by
[`docs/tools/gen_icons.py`](tools/gen_icons.py); the list lives in [`docs/tools/icons.txt`](tools/icons.txt).
To add an icon:

```bash
curl -sL https://registry.npmjs.org/lucide-static/-/lucide-static-<version>.tgz | tar xz   # → package/icons
python3 docs/tools/gen_icons.py package/icons \
  core/designsystem/src/main/kotlin/rocks/lumina/app/core/designsystem/icon/LucideGlyphs.kt \
  "$(cat docs/tools/icons.txt),new-icon-name"
```

`LuminaIcon(glyph, filled = active)` renders 24 dp with a 1.75 stroke. Active state = same glyph filled. Discover's
active state uses a heavier stroke and Create's active state is a filled square with a knocked-out plus, the same as on web.

## Layout rules

- Bottom bar on every window size: Home · Discover · Create · Notifications (heart) · Profile (avatar, 1.5 dp
  ring when active). 56 dp plus the inset, `surface`, hairline top border. Hidden on create, chat, media viewer, login.
- Top bar: 56 dp plus the status bar, `surface`, hairline bottom border. Home shows the gradient wordmark plus the messages icon.
- Content: centered column, max 470 dp. Feed photos are edge-to-edge in the column (radius 0).
- Grids: 3 columns, square, 2 dp gaps. Sheets: 16 dp top radius. Controls: 8 dp radius.
- Touch targets ≥ 48 dp, all icon buttons labelled, focus ring in `accent`.
