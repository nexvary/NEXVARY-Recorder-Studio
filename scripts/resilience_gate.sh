#!/usr/bin/env bash
set -euo pipefail
mkdir -p resilience-evidence
serial=$(adb get-serialno | tr -d '\r')
[[ "$serial" == emulator-* ]] || { echo 'Dedicated emulator required'; exit 1; }
[[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]] || exit 1
adb logcat -c
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put global stay_on_while_plugged_in 3
adb shell settings put system screen_off_timeout 2147483647
adb shell svc wifi enable
adb shell svc data enable
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
runner=com.nexvary.recorder.test/androidx.test.runner.AndroidJUnitRunner
fixture=com.nexvary.recorder.ResilienceAcceptanceTest
capture() {
  adb logcat -d -v threadtime > resilience-evidence/logcat.txt || true
  adb pull /sdcard/Download/NEXVARYContinuousEvidence/ resilience-evidence/public/ || true
}
trap capture EXIT
run_case() {
  adb shell am instrument -w -r -e class "$fixture#$1" "$runner" > "resilience-evidence/$2.log" 2>&1
  python3 - "$2" <<'CHECK'
from pathlib import Path
import re,sys
text=Path('resilience-evidence/'+sys.argv[1]+'.log').read_text()
assert re.search(r'OK\s+\(1 test\)',text),text
assert 'FAILURES!!!' not in text and 'INSTRUMENTATION_FAILED' not in text,text
CHECK
}
run_case fiveMinuteNetworkOutageResumesRealMailWorkerOverPinnedLocalTls network
adb shell am instrument -w -r -e class "$fixture#prepareLiveSessionForHostSigkill" "$runner" > resilience-evidence/kill-prepare.log 2>&1 &
instrument_pid=$!
ready=false
for attempt in $(seq 1 150); do
  if adb shell test -f /sdcard/Download/NEXVARYContinuousEvidence/resilience-kill-ready.json; then ready=true; break; fi
  if ! kill -0 "$instrument_pid" 2>/dev/null; then break; fi
  sleep 1
done
if [[ "$ready" != true ]]; then cat resilience-evidence/kill-prepare.log; exit 1; fi
adb pull /sdcard/Download/NEXVARYContinuousEvidence/resilience-kill-ready.json resilience-evidence/kill-seed.json
victim_pid=$(adb shell pidof -s com.nexvary.recorder | tr -d '\r')
[[ "$victim_pid" =~ ^[0-9]+$ ]] && (( victim_pid > 1 )) || exit 1
python3 - "$victim_pid" <<'CHECK'
from pathlib import Path
import json,sys
seed=json.loads(Path('resilience-evidence/kill-seed.json').read_text())
assert seed['pid']==int(sys.argv[1]),seed
CHECK
# This is the emulator application's own UID, not root or an OS/security-policy bypass.
adb shell run-as com.nexvary.recorder kill -9 "$victim_pid"
wait "$instrument_pid" || true
new_pid=$(adb shell pidof -s com.nexvary.recorder | tr -d '\r' || true)
[[ "$new_pid" != "$victim_pid" ]] || exit 1
python3 - "$victim_pid" <<'CHECK'
from pathlib import Path
import json,sys
Path('resilience-evidence/host-sigkill.json').write_text(json.dumps({'signal':9,'victimPid':int(sys.argv[1]),'target':'dedicated emulator application UID','intentionalInstrumentationTermination':True},indent=2))
CHECK
run_case verifyRecoveryAfterHostSigkill recovery
capture
python3 - <<'CHECK'
from pathlib import Path
import json
root=Path('resilience-evidence/public')
network=json.loads(next(root.rglob('resilience-network.json')).read_text())
recovery=json.loads(next(root.rglob('resilience-process-recovery.json')).read_text())
assert network['offlineMs']>=300000 and network['productionMailWorker']
assert network['clips']==network['smtpDataCommands']==network['uniqueMessageIds']
assert recovery['oldPid']!=recovery['newPid'] and recovery['state']=='INTERRUPTED'
assert recovery['tailRetainedOrSalvaged'] and recovery['queueIdentityStable'] and not recovery['cameraAutomaticallyRestarted']
Path('resilience-evidence/ACCEPTANCE.json').write_text(json.dumps({'network':network,'processRecovery':recovery,'physicalDeviceVerified':False,'externalInboxVerified':False},indent=2))
CHECK
