#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots diagnostics
adb logcat -c
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put global stay_on_while_plugged_in 3
adb shell settings put system screen_off_timeout 2147483647
adb shell getprop > diagnostics/device-properties.txt
adb shell dumpsys meminfo > diagnostics/memory-before.txt
# No HOME key or Pixel-specific profile: avoid launching the unstable launcher during evidence collection.
set +e
gradle --no-daemon :app:connectedDebugAndroidTest > diagnostics/instrumentation.txt 2>&1
status=$?
set -e
cat diagnostics/instrumentation.txt
adb logcat -d -v threadtime > diagnostics/logcat.txt
adb shell dumpsys activity lastanr > diagnostics/last-anr.txt
adb shell dumpsys activity activities > diagnostics/activities.txt
adb shell dumpsys window windows > diagnostics/windows.txt
adb pull /sdcard/Pictures/NEXVARYVisualEvidence/. screenshots/ || true
# A screenshot is evidence only when the instrumentation gate succeeds.
if [ "$status" -ne 0 ]; then
  cp -r screenshots diagnostics/unapproved-screenshots
  rm -f screenshots/*.png
  exit "$status"
fi
for screen in dashboard-ar dashboard-purple-ar sos-ar sos-editor-ar sos-contact-ar camera-ar settings-ar library-ar; do
  test -s "screenshots/$screen.png"
done
if grep -E 'ANR in com.nexvary.recorder|ANR in com.google.android.apps.nexuslauncher|ANR in com.android.launcher3|FATAL EXCEPTION:' diagnostics/logcat.txt; then
  rm -f screenshots/*.png
  exit 1
fi
