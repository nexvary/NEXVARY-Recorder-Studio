# Physical/production acceptance — pending, not a passed test report

Target first: OPPO CPH2577, Android15/ColorOS. The API33/34/35 emulator reports and
network/SIGKILL evidence do not substitute for this device run. Keep PR #18 draft
until these results and owner signing are reviewed.

## Safe preparation

1. Connect the authorized test phone with USB debugging and unlock it normally.
   Record `adb devices -l`, `adb shell getprop ro.product.model`,
   `adb shell getprop ro.build.version.release` and `adb shell getprop ro.build.version.sdk`.
2. Preserve existing recordings/outbox. Do not uninstall, clear app data or replace
   the installed owner build with a debug-key APK. Follow `scripts/verify_owner_apk.py`
   and `docs/SIGNED_DELIVERY.md`; verify the original certificate and a
   higher versionCode before an update. An unsigned AAB is not installable as an update.
3. Use a test scene and explicitly approved test recipient. Configure the provider
   through the app; do not copy mailbox credentials into source, CI logs or screenshots.
4. Record free storage (`adb shell df -k /data /sdcard`), battery
   (`adb shell dumpsys battery`) and thermal status
   (`adb shell dumpsys thermalservice`) before/after each long run. Log actual OEM
   battery settings. Do not disable OS privacy indicators, foreground-service rules
   or secure-lock protections. Device identifiers/recipient details should be redacted
   before publishing a report.

## Required executions and evidence

| Scenario | Procedure and acceptance evidence |
| --- | --- |
| Long capture | Start once from the visible Arabic recording page, default 60-second targets; run at least 15 minutes. Record wall-clock start/stop, clip indexes, sizes, durations and hashes. Independently decode a frame from each file. Measure boundary timestamps without claiming zero frame loss. |
| Concurrent delivery | Confirm clip1 reaches provider acceptance while clip2 still records; retain recipient-side headers/Message-ID and receipt time separately from sender status. Actual inbox delivery is a separate observation. |
| Five-minute outage | Disable phone Wi-Fi/mobile data through normal controls for five minutes while capture continues. Verify pending files remain; restore connectivity and observe automatic submission without restarting the session. Compare attachment hashes and check duplicate Message-IDs at the controlled inbox. |
| Independent failure | Use an approved test configuration to provoke a safe pre-DATA failure for one queued job; verify other jobs can progress and originals persist. Do not generate spam or deliberately ambiguous delivery to real recipients. |
| Lock/background | Start visibly, press Home, then lock with the normal secure lock for several clip boundaries. Verify notification/stop control, privacy indicators, actual output and any recorded stop reason after unlock. |
| User stop | Stop halfway through a segment; verify the final valid tail is saved and queued once. |
| Process death | Use a dedicated test installation/device with recoverable data. Abrupt termination must occur while a second segment is live; after a normal user reopen verify INTERRUPTED, retained first-clip hash, pending tail and stable queue identities. `am force-stop` is a separate scenario: Android suppresses background work until user launch. Do not substitute it for SIGKILL or promise unattended restart. |
| Low storage | Use a disposable test device/image, not the owner's populated phone. Approach the reserve threshold, observe graceful stop/finalization and no unsent deletion. Test an actual write/ENOSPC failure separately; a reserve-policy fixture is not proof of that failure path. |
| Low battery/thermal | Observe real low-battery, battery-saver and warm sustained use without unsafe heating or forced battery discharge. Record measured battery/thermal state, output continuity or explicit interruption. A simulated battery percentage alone does not verify physical power behavior. |

For every case record: build commit/version/certificate, device/OS, conditions,
start/end timestamps, actual vs expected result, file hashes, sender/provider/inbox
states and evidence location. Mark an interrupted recording honestly; never infer
continued capture from a persistent notification alone. Test microphone/video start
skew and long-run A/V drift on hardware separately.

## Access needed to close production items

- The connected workstation and authorized USB test phone must be online; the latest
  available workstation check was offline. The emulator-only resilience script must
  never be used as a physical-device test runner.
- Original release signing must occur in the owner's controlled signing environment.
- Actual Namecheap cPanel plan, resource limits, storage/backup and runtime/background
  capabilities require account inspection. Keep work isolated from `nexvary.com`
  and its database. The chosen direct-SMTP mode does not require a Nexvary server.
- Real provider/inbox acceptance uses the owner's approved test addresses and normal
  consent flow. No external test message has been sent by these emulator tests.

No physical or production item in this document has been claimed as passed.
