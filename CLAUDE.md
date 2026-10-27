# CLAUDE.md — Crosshair Overlay

Android app by Bensal that draws a configurable crosshair on top of every app, for FPS games (COD Mobile, PUBG Mobile, Free Fire and others; landscape, touch controls). Public repo: write docs and changelog for players, not about the development history. Neon cyan/magenta "futuristic" UI.

## Repo layout

```
/
├── CrosshairOverlay/              ← Android project (edit files here directly)
├── .github/workflows/build.yml    ← CI build + release
├── docs/                         ← README images + HOW-TO-USE.md guide
├── tools/changelog.py            ← writes CHANGELOG.md from the in-app changelog
├── CHANGELOG.md, LICENSE
├── CLAUDE.md
└── README.md
```

The source is committed as normal files. Do **not** upload a zip.

## Build and release

- Every push builds a signed release APK on GitHub Actions. JDK 17, Gradle 8.7 (installed by CI, no wrapper), AGP 8.5.2, Kotlin 1.9.24.
- **Only pushes to `main` publish a GitHub Release**, one per app version: tag `v<versionName>`, title "Crosshair v<versionName>", APK `crosshair-v<versionName>.apk`, notes taken from that version's entry in the in-app changelog. A push that doesn't bump the version makes no new release. Other branches only upload a workflow artifact, so test risky changes on a branch first. No Android SDK is available in Claude's sandbox; the CI run is the compile check.
- Signing: the workflow uses only the `KS_BASE64` (base64 of the keystore) and `KS_PASS` GitHub Secrets. Key alias `crosshair`. This repo was started fresh in Oct 2026 so the key never appears in its history; never commit key files (`.gitignore` blocks them). The repo is meant to be public.
- CI security: the keystore is decoded only inside the "Build signed release" step (temp file, deleted on exit) and `KS_PASS` is passed as that step's env only, never via `GITHUB_ENV`. Third-party actions are pinned to full commit SHAs (Dependabot updates them). Keep it that way. The build also checks the APK's certificate equals the official SHA-256 (`EXPECTED_CERT` in build.yml, public value) and that it isn't debuggable, and adds the APK hash + cert to the release notes.
- Isolation: `allowBackup=false` + `data_extraction_rules.xml` exclude all app data from cloud backup and device transfer. No INTERNET permission; the unit test `appIsIsolatedAndHasNoNetworkAccess` fails if one is added. See SECURITY.md.
- **Never create or replace the signing key.** A new key means updates won't install over the existing app. The workflow fails rather than generating one.

## App facts

- Package / applicationId: `com.example.crosshair`, minSdk 26, targetSdk/compileSdk 34
- **On every user-visible change bump `versionCode` and `versionName`** in `app/build.gradle.kts` (now 14 / 1.0; versionCode must always go up, even though the public version restarted at 1.0) and add an entry at the top of the `changelog` string in `MainActivity.kt`. Then run `python3 tools/changelog.py > CHANGELOG.md`.
- No AndroidX, no Compose, no XML layouts: UI is built in code with plain `android.widget` views. No third-party dependencies. Keep it that way unless asked.

## Source files (`CrosshairOverlay/app/src/main/java/com/example/crosshair/`)

| File | Role |
|---|---|
| `MainActivity.kt` | Settings UI: preview, style chips, colours + custom hex, sliders, toggles, Position card (portrait/landscape), presets, device card, Changelog/About. Two columns when `screenWidthDp >= 720`. |
| `Cfg.kt` | Shared settings helpers: defaults, preset save/apply/names, current preset label, reset, backup code (`CROSSHAIR1:` + base64 JSON) and restore. Used by the activity and the service. |
| `CrosshairView.kt` | Draws the crosshair, pixel-snapped around the exact screen centre. Picks the landscape offset when `width > height`. Preview-only fields: `forceLand`, `offsetScale`. |
| `OverlayService.kt` | Foreground service with a full-screen, non-touchable overlay window. Notification actions: Next preset (`NEXT`), Hide/Show (`HIDE`), Stop (`STOP`). Refreshes the Quick Settings tile on start/stop. `START_STICKY`: if Android kills it, it comes back only when `want_on` is true. Window is crosshair-sized (`relayout()`, positioned at centre + offset, recomputed on rotation); full-screen only while center guide lines are on. |
| `Backup.kt` | Reads at most 64 KB on restore (`readLimited`). Saves the backup code to `Download/Crosshair/crosshair-backup.txt` (MediaStore on 10+, legacy file + permission on 8-9), auto-backup after preset save, reads a picked file for restore. |
| `ToggleTile.kt` | Quick Settings tile to start/stop. |
| `GridBackground.kt` | Neon grid behind the preview. |

### Touch pass-through (important for games)
On Android 12+ the overlay window's `alpha` is set to `InputManager.maximumObscuringOpacityForTouch` (usually 0.8). Above that, Android blocks taps from reaching the game. Don't remove this or raise the window alpha. The app preview uses the same cap and the Opacity slider shows the on-screen value.

## Settings storage

SharedPreferences file `cfg`:
- Ints: `color`, `style`, `size`, `thick` (dp), `alpha` (%), `gap` (%), `ox`/`oy` (portrait offset dp), `oxL`/`oyL` (landscape offset dp), `ocolor` (outline colour), `othick` (outline thickness, half-dp steps)
- Booleans: `outline`, `cdot`, `guide` (guide is not saved in presets)
- `preset1..preset3`: JSON of the keys above plus `name`
- `cur_preset`: active slot, 0 = Custom (any manual edit sets it to 0)
- `rev`: bumped when a preset is applied from the notification (or restored), so the activity re-syncs on resume
- `want_on`: user wants the overlay running (false after Stop)
- `batt_dismiss`, `notif_asked`: banner state
- `custom_color`: last colour picked with sliders/hex (extra swatch)
- `auto_backup` (default true), `last_backup`

If you change how the crosshair is sized, keep `CrosshairView.extentPx()` covering the whole drawing, or the small overlay window will clip it.

Styles: 0 Dot, 1 Plus, 2 Gap plus, 3 T-shape, 4 Circle, 5 Ring + dot, 6 X. Adding one means updating `styleNames` in `MainActivity.kt` and the `when` in `CrosshairView.shape()`.

Every number read from prefs, presets or a backup goes through `Cfg.clamp` (slider ranges), so tampered files can't break the overlay. Don't rename keys, or users lose saved settings. When adding a preset key, handle old presets that lack it (see `Cfg.applyPreset`).

## Theme

- Cyan `#00F0FF`, magenta `#FF2BD6`, muted text `#8FA3C8`
- Backgrounds `#050810` / `#060A12` → `#0E1530` gradient
- Panels: translucent `#141F3D`, 1dp cyan stroke, 16dp corners
- Buttons: cyan → `#2D6BFF` gradient (idle), magenta → `#FF3B3B` (running)
- Uppercase headings with wide letter spacing
- Swatches: green `#00FF66` (default), red, cyan, yellow, white, magenta, orange, blue

Match this for new UI. The theme is also reused in Bensal's GST calculator app.

## Working style

- Bensal installs the APK from the GitHub Release on his phone; he doesn't use Android Studio. A broken build means no APK.
- Suggest changes and wait for his approval before implementing.
- Keep explanations short and non-technical.
- About screen: creator is Bensal; owner shows the device name.
