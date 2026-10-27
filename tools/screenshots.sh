#!/bin/bash
# Takes README screenshots on an Android emulator (run by .github/workflows/screenshots.yml).
# Needs: a running emulator/device on adb and the debug APK built.
set -x
OUT=${1:-shots}; mkdir -p "$OUT"
PKG=com.example.crosshair
APK=CrosshairOverlay/app/build/outputs/apk/debug/app-debug.apk

shot() { sleep 2; adb exec-out screencap -p > "$OUT/$1.png"; }
# Tap the first on-screen element whose text contains $1 (uses a UI dump to find it)
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null; adb pull -q /sdcard/ui.xml /tmp/ui.xml > /dev/null
  b=$(python3 - "$1" <<'EOF'
import re, sys, html
x = open('/tmp/ui.xml', encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
    if sys.argv[1].lower() in html.unescape(m.group(1)).lower():
        a, b, c, d = map(int, m.groups()[1:]); print((a + c) // 2, (b + d) // 2); break
EOF
)
  [ -n "$b" ] && adb shell input tap $b
}
# Screen size, so swipes work on phones and tablets
read W H < <(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr x ' ')
swipe_up() { adb shell input swipe $((W/2)) $((H*80/100)) $((W/2)) $((H*35/100)) 600; sleep 1; }

adb install -r "$APK"
# Permissions the app normally asks for, so no banners or dialogs cover the screens
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell dumpsys deviceidle whitelist +$PKG
# Clean status bar (fixed time, full battery, no notifications icons)
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0

adb shell am start -W -n $PKG/.MainActivity; sleep 4
shot 01_main
swipe_up;  shot 02_style_colour
swipe_up;  shot 03_size_look
swipe_up;  shot 04_position
swipe_up;  shot 05_presets
swipe_up;  shot 06_device

fresh() { adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.MainActivity; sleep 3; }

# Menu and changelog
fresh; tap_text "⋮"; shot 07_menu
tap_text "CHANGELOG"; shot 08_changelog

# Pick a bigger, easy-to-see crosshair, start the overlay and show it over the home screen
fresh; tap_text "GAP PLUS"; sleep 1
tap_text "START OVERLAY"; sleep 4; shot 09_running
adb shell input keyevent KEYCODE_HOME; sleep 2; shot 10_overlay_home
adb shell cmd statusbar expand-notifications; sleep 2; shot 11_notification
adb shell cmd statusbar collapse; sleep 1

# Landscape, like in a game: the app with the overlay running on top
adb shell am start -W -n $PKG/.MainActivity; sleep 2
adb shell settings put system user_rotation 1; sleep 4; shot 12_landscape_running
adb shell settings put system user_rotation 0
ls -la "$OUT"
