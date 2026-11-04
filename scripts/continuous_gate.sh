#!/usr/bin/env bash
set -euo pipefail
mkdir -p continuous-evidence
adb logcat -c
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put global stay_on_while_plugged_in 3
adb shell settings put system screen_off_timeout 2147483647
set +e
gradle --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nexvary.recorder.ContinuousRecordingTest \
  -Pandroid.testInstrumentationRunnerArguments.continuousLong=true \
  > continuous-evidence/instrumentation.txt 2>&1
status=$?
set -e
cat continuous-evidence/instrumentation.txt
adb logcat -d -v threadtime > continuous-evidence/logcat.txt
adb pull /sdcard/Android/data/com.nexvary.recorder/files/Download/ continuous-evidence/clips-metadata/ || true
exit "$status"
