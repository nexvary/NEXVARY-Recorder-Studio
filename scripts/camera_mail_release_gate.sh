#!/usr/bin/env bash
set -euo pipefail
mkdir -p mail-screenshots mail-diagnostics
adb logcat -c
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put global stay_on_while_plugged_in 3
adb shell settings put system screen_off_timeout 2147483647
adb shell settings put global heads_up_notifications_enabled 0
set +e
gradle --no-daemon -PguardianMailReleaseTest=true -Pandroid.testInstrumentationRunnerArguments.class=com.nexvary.recorder.CameraMailAcceptanceTest,com.nexvary.recorder.ReleaseMailWorkerTest :app:connectedReleaseAndroidTest > mail-diagnostics/instrumentation.txt 2>&1
status=$?
set -e
cat mail-diagnostics/instrumentation.txt
adb logcat -d -v threadtime > mail-diagnostics/logcat.txt
adb pull /sdcard/Pictures/NEXVARYVisualEvidence/. mail-screenshots/ || true
if [ "$status" -ne 0 ]; then
  rm -f mail-screenshots/*.png
  exit "$status"
fi
test -s mail-screenshots/camera-mail-release-ar.png
test -s mail-screenshots/camera-mail-review-release-ar.png
if grep -E 'ANR in com.nexvary.recorder|FATAL EXCEPTION:' mail-diagnostics/logcat.txt; then
  rm -f mail-screenshots/*.png
  exit 1
fi
