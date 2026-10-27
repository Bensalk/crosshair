# How to use Crosshair

A step-by-step guide, from installing the app to using it in a match. Back to the [README](../README.md).

- [1. Install](#1-install)
- [2. Allow the permissions](#2-allow-the-permissions)
- [3. Design your crosshair](#3-design-your-crosshair)
- [4. Start the overlay](#4-start-the-overlay)
- [5. Line it up with your game](#5-line-it-up-with-your-game)
- [6. Save presets](#6-save-presets)
- [7. Control it during a match](#7-control-it-during-a-match)
- [8. Backup and restore](#8-backup-and-restore)
- [Tips for FPS games](#tips-for-fps-games)
- [Troubleshooting](#troubleshooting)

---

### 1. Install

1. Download `crosshair-v1.0.apk` from the [latest release](https://github.com/Bensalk/crosshair/releases/latest).
2. Open the file. If Android asks, allow **Install unknown apps** for your browser or file manager.
3. Updates install over the existing app and keep your settings and presets.

### 2. Allow the permissions

When you tap **▶ START OVERLAY** for the first time, Android asks for:

| Permission | Why |
|---|---|
| **Display over other apps** | Needed to draw the crosshair on top of your game |
| **Notifications** | Shows the control buttons (Next preset, Hide / Show, Stop) |
| **Battery: Unrestricted** | Stops Android from closing the crosshair in the middle of a match |

If any of these are blocked, the app shows a banner at the top. Tap it to fix the setting.

> **"Display over other apps" is greyed out?** Go to **App info → ⋮ menu → Allow restricted settings**, then try again.

### 3. Design your crosshair

The preview at the top shows exactly what you'll get. Tap the preview to test it on a light background.

- **Style**: Dot, Plus, Gap plus, T-shape, Circle, Ring + dot or X.
- **Color**: tap a swatch, or use the **Hue** and **Brightness** sliders to pick any colour. You can also type a hex code such as `00F0FF` and tap **SET**. Your last custom colour is kept as an extra swatch.
- **Size and look**:
  - **Size** and **Thickness**
  - **Gap**, the space in the middle (for Gap plus, T and X)
  - **Opacity**, which shows how solid it really looks on screen
  - **Center dot** adds a dot in the middle of any style
- **Outline** (for better visibility): a black or white edge with its own thickness, so the crosshair stands out on any background.

### 4. Start the overlay

Tap **▶ START OVERLAY**. The crosshair appears in the centre of the screen on top of every app. The button changes to **■ STOP OVERLAY**.

You can also start or stop it from the **Quick Settings tile**:

1. Pull down the notification shade twice and tap the ✏️ edit button.
2. Drag the **Crosshair** tile into your tiles.
3. Tap the tile to turn the crosshair on or off. Long-press the tile to open the app.

### 5. Line it up with your game

Games don't always put their aim point at the exact centre of the screen. Use **Position (fine-tune center)** to match it.

1. Choose **LANDSCAPE** for games. Portrait and landscape each keep their own position, and the app switches between them automatically when you rotate.
2. Start the overlay and turn on **Center guide lines** to see the true centre while you adjust.
3. Move the crosshair with the **Horizontal** and **Vertical** sliders, or tap **+ / −** to move it exactly 1 dp at a time. Hold the button to keep moving; you feel a light vibration on each step.
4. Open the game, compare, and adjust again until it sits right on the game's own crosshair.
5. **CENTER EXACTLY** puts it back in the true centre. **FIT SIZE TO SCREEN** sets a size that suits your screen.

The **Device** card shows your phone model, screen size, the screen centre and the exact crosshair position.

### 6. Save presets

You have 3 preset slots. Use them for different weapons or modes, for example *Hipfire*, *Sniper* and *Night map*.

1. Set up the crosshair the way you want.
2. Tap **SAVE** on a slot and give it a name.
3. Tap **LOAD** on a slot to use it. Saving over a filled slot asks before replacing it.

If you change any setting after loading a preset, the label switches to **Custom**.

### 7. Control it during a match

You don't need to leave the game. Pull down the notification and use its buttons:

| Button | What it does |
|---|---|
| **Next preset** | Switches to your next saved preset |
| **Hide / Show** | Hides the crosshair for a moment, for example in menus or cutscenes |
| **Stop** | Turns the overlay off |

Taps always pass through the crosshair to the game on Android 12 and newer. It never blocks your controls.

### 8. Backup and restore

Open the **⋮** menu.

- **Backup** saves all settings and presets to `Download/Crosshair/crosshair-backup.txt` on your phone.
- **Auto-backup when I save a preset** is on by default, so the file stays up to date.
- **Restore → CHOOSE BACKUP FILE** brings everything back, for example on a new phone or after reinstalling.
- **Reset** returns to the default settings. Your presets are kept.

---

## Tips for FPS games

These work for most shooters, such as COD Mobile, PUBG Mobile and Free Fire. They are suggestions to start from; change them to suit you.

- Use **Gap plus** or **Dot** with **Outline** on. They stay visible on bright and dark maps.
- Keep opacity at around 70–80%. You still see the target behind the crosshair.
- Line it up in **landscape** in the training ground, then save it as a preset.
- Make a second preset with a bigger **Circle** or **Ring + dot** for close-range hipfire, and switch with **Next preset**.

## Troubleshooting

**The crosshair disappears in the middle of a match.**
Set battery use to **Unrestricted** (tap the battery banner in the app). On Xiaomi, Realme, Oppo or Vivo phones, also turn on **Autostart** for Crosshair in App info. If Android does close it, the crosshair comes back by itself.

**My taps don't reach the game.**
On Android 12 and newer the app keeps the overlay below the system's touch limit, so taps pass through.

**The opacity slider doesn't go fully solid.**
That's on purpose. Android blocks taps through overlays above about 80% opacity, so the app caps it there to keep your controls working.

**The crosshair is slightly off from the game's aim point.**
Use the **+ / −** buttons in Position to move it 1 dp at a time while the game is open, then save a preset.
