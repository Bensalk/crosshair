# Changelog

Generated from the in-app changelog (`MainActivity.kt`) with `python3 tools/changelog.py > CHANGELOG.md`.
Download any version from the [Releases page](https://github.com/Bensalk/crosshair/releases).


## v1.0

- First public release: a light crosshair overlay for FPS games on Android
- 7 styles (Dot, Plus, Gap plus, T-shape, Circle, Ring + dot, X) with size, thickness, gap and opacity
- Any colour: swatches, hue and brightness sliders or a hex code, plus an outline in black or white
- Taps always pass through the crosshair to your game (Android 12+)
- Separate position for portrait and landscape, with 1 dp + / - steps, Center exactly and Fit size to screen
- Three named presets; switch preset, hide / show or stop from the notification during a match
- Quick Settings tile to start and stop
- Comes back on its own if battery saver closes it
- Backup to a file in Download/Crosshair, auto-backup on preset save, and restore
- Device card, changelog and About; two-column layout on tablets
