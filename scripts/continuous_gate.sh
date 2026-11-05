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
adb pull /sdcard/Download/NEXVARYContinuousEvidence/ continuous-evidence/clips-metadata/ || true
if [ "$status" -eq 0 ]; then
  python3 - <<'EVIDENCE'
from pathlib import Path
import json
metrics = list(Path("continuous-evidence/clips-metadata").rglob("*.metrics.json"))
assert metrics, "Passing tests must publish actual timing metrics"
for file in metrics:
    value = json.loads(file.read_text())
    assert value["elapsedMs"] == 900000 and value["targetSeconds"] == 60 and value["clips"] >= 15
    print(json.dumps(value, indent=2))
EVIDENCE
fi
exit "$status"
