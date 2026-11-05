# Continuous emergency recording — engineering candidate 0.13.0

## Scope and release boundary

Implemented on `feature/continuous-emergency-recording`, stacked on PR #17 (`e5e0c150bb2818e9c7215c6d70614ea4f86cb023`) rather than the older PR #10 snapshot. PR #10 and later camera/mail/backend changes were inspected before implementation. The package remains `com.nexvary.recorder`; code 26 / version 0.13.0. This document describes implementation, not physical-device acceptance or a deployed service.

The recording page offers an independent continuous switch, 30/60/120/300-second targets (60 by default), automatic email using the existing approved recipient editor, safe retry preference, live clip number and saved/server-accepted/pending/review counts. The ordinary auto-stop timer is ignored only in this mode. Existing timed recording and family-evidence paths remain separate.

## Capture and finalization

`SegmentedEncoder` maintains one Camera2 output surface, AVC encoder, optional microphone/AAC encoder, and foreground service for the entire session. Only MediaMuxer instances rotate. MediaRecorder's documented next-output mechanism is file-size based and its max-duration callback stops recording, so it is not used for time-targeted continuous clips. This avoids an intentional camera close/reopen at every boundary.

Encoded samples are buffered in presentation-time order, with an audio/video watermark. A new file starts on an AVC sync frame at/after the requested interval; duration is a target, not an exact wall-clock guarantee. Sync requests and a one-second I-frame target bound ordinary rollover latency, subject to encoder behavior. Per-track timestamps are normalized from each encoder's first output. Initial audio/video start skew and long-run drift need measurement on physical hardware; synchronized track origins are not claimed.

The reorder buffer is capped at 16 MiB and five seconds; a stalled producer or muxer failure ends the session with an error. A three-second EOS deadline bounds stop cleanup. Final MP4 publication is `.pending` → muxer stop/release → file sync → same-directory rename to `.mp4`. Completed files receive media validation and SHA-256 off the camera looper. Validation/SMTP copy work is independent of subsequent encoding.

Each session has a UUID; clips have monotonic one-based indexes and creation timestamps. Per-segment journals preserve first/last global video PTS for boundary measurements. Publication is atomic on the app filesystem; absolute power-loss durability of directory metadata is not asserted.

## Recovery and storage

Backup-excluded, app-private AtomicFile journals persist session status, approved recipient snapshot, target interval, segment metadata and queue job identity. Finalized files survive process death. Recovery validates orphan published/pending files, queues valid clips, and marks unfinished MP4 tails INCOMPLETE without deleting them. An interrupted MP4 lacking its final index cannot always be repaired; the app does not claim recovery of every in-flight frame. Camera capture never restarts automatically after process death.

The queue has deterministic UUID keys derived from session/index, so recovery cannot create a second job for the same clip. The sender still rechecks its private copy's checksum. WorkManager restores scheduled jobs, uses connected-network constraints and exponential backoff. Safe pre-DATA connection failures can retry until the user intervenes; authentication/TLS/size/permanent recipient errors require review. The user can disable automatic safe retries for continuous jobs. Unknown SMTP DATA acceptance is UNCERTAIN and never silently retried.

Each second, storage monitoring checks both movie and internal outbox storage, reserving at least 64 MiB or two estimated segment budgets (whichever is larger). When low, capture ends and attempts to finalize its tail. This is a graceful stop, not automatic deletion of unaccepted evidence. Originals remain until user deletion; accepted SMTP private copies follow the existing cleanup behavior. Long offline sessions cannot record forever on finite storage. No automatic age-based deletion of unsent clips is implemented.

## Transport and privacy

No Nexvary server is required for this mode. Existing authenticated, certificate-validated TLS SMTP and encrypted Android Keystore profile remain in use; there is no company mailbox password in the APK. Recipient review/PIN controls and private SMTP envelope are retained. Queue/session metadata is app-private and excluded from Android backup; video uses app-specific Movies storage protected by Android sandbox/device file encryption. These are not separately application-encrypted MP4 files. A compromised/unlocked privileged device remains outside those protections. TLS protects SMTP hops to the submission provider; it is not end-to-end encrypted email.

The UI's SMTP accepted state means provider submission succeeded, not inbox delivery, reading or recipient acknowledgment. Stable Message-ID is helpful for correlation but does not give exactly-once SMTP delivery. An HTTPS object-storage workflow would require verified upload plus separate recipient notification/access acknowledgment; it is not substituted into this mode or marked delivered.

Continuous sessions deliberately do not route through the existing Guardian event upload API. That API has bounded event/segment/size quotas (including a 24-segment event limit) and is not an unlimited recording backend. Reusing it for arbitrary-duration sessions without lifecycle/quota changes would be incorrect.

## Android lifecycle

Start is explicitly user-initiated from the visible recording activity. Non-exported camera service, declared camera/microphone foreground-service types, runtime permissions, clear persistent stop notification and normal privacy indicators are retained. `START_NOT_STICKY` prevents unattended camera restart. Screen lock/background operation is subject to platform/OEM restrictions; callback or encoder failures end recording. Process killing may bypass onDestroy, which is why the journal marks a previous RECORDING session interrupted on next recovery. There is no stealth path or privacy-indicator suppression.

Battery, data, storage and thermal conditions can end capture or delay WorkManager. There is no claimed exemption from Doze, ColorOS restrictions, force-stop or Android while-in-use permission rules. Low-battery and thermal acceptance remain physical-test work.

## Verification

See `CONTINUOUS_TEST_RESULTS.md` for actual run status. JVM tests cover duration/keyframe policy, numbering, stable keys and storage floor. Instrumentation creates actual camera MP4s, dispatches one completed clip through a synthetic acknowledging transport while the next clip records, checks file tracks/timestamps, final-tail save and idempotent reconstruction, and retains an uncertain-send private copy. A dedicated API35 workflow runs a real 15-minute emulator camera session with 60-second targets and exports boundary PTS journals. None of these establishes physical OPPO, real provider delivery or zero gaps.

## Dedicated network/process acceptance harness

`Continuous Network and Process Recovery Acceptance` builds an isolated test APK using
`gradle -PguardianResilienceTest=true :app:assembleDebug :app:assembleDebugAndroidTest`,
then runs `bash scripts/resilience_gate.sh` on a disposable API35 emulator. Both the
host script and instrumentation reject a non-emulator target. Do not install this
harness over an owner installation: it changes network state, temporarily configures
fixture SMTP, and deliberately kills its own debug application process.

The network case disables Wi-Fi/mobile data for at least five minutes during actual
capture, checks queued files remain intact with zero offline attempts, then restores
Wi-Fi and waits for the production MailWorker to submit all clips over authenticated
TLS to a loopback fixture. Only the test process temporarily trusts the public fixture
certificate; production certificate/hostname verification code is unchanged. Each MIME
attachment is compared byte-for-byte with its retained original. This verifies server
submission, not external inbox delivery.

The host then starts another actual capture, waits for one finalized clip and a live
second file, records its PID/hash/job identity, sends SIGKILL using that debug app's own
UID, and starts fresh instrumentation without clearing/uninstalling app data. Recovery
checks the changed PID, INTERRUPTED session, unchanged completed clip, retained or
salvaged tail, stable queue identities across repeated recovery, and no automatic camera
restart. The intentionally killed preparation is not counted as a passed JUnit test.
The workflow exports raw instrumentation logs, host signal/seed records, logcat and
JSON checks under `continuous-network-process-evidence`; the script requires both
completed acceptance cases before writing `ACCEPTANCE.json`.

## Rollback

Do not merge this draft into main before acceptance. To disable the feature in an installed candidate, stop the active session then turn off Continuous; ordinary recording remains available. Do not clear application data or uninstall to roll back: this removes private queue/journal state. To revert code while preserving queued work, retain the stable-key MailQueue schema/reader and recovery code until all jobs are resolved; the previous APK cannot fully interpret new continuous sessions. Production APK updates must use the original release signing certificate and a higher versionCode. Public debug-key artifacts are CI fixtures, not an owner update.
