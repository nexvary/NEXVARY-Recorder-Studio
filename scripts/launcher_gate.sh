#!/usr/bin/env bash
set -euo pipefail
[[ $(adb get-serialno) == emulator-* ]] || exit 1
[[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]] || exit 1
mkdir -p launcher-evidence
adb logcat -c
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put system screen_off_timeout 2147483647
adb shell settings put global heads_up_notifications_enabled 0
capture() {
  adb logcat -d -v threadtime > launcher-evidence/logcat.txt || true
  adb pull /sdcard/Download/NEXVARYContinuousEvidence/ launcher-evidence/public/ || true
}
trap capture EXIT
gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nexvary.recorder.LauncherAppearanceTest > launcher-evidence/instrumentation.txt 2>&1
cat launcher-evidence/instrumentation.txt
