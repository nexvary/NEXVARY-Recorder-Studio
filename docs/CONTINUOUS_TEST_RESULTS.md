# Continuous recording — verified engineering candidate 0.13.0

Verified implementation: `0b4c90c31bddc5ae7c38fadf63aca0cda82f6c91` (code 26), PR #18, stacked on the recipient-card branch/PR #17. Reviewed on 5 November 2026 (Cairo). The follow-up test-only source `7397d06da85996dcb5a9e90f5c0742101a2b4d32` adds dedicated network/process acceptance without changing production Kotlin behavior. Subsequent documentation-only commits do not change either source. This is an emulator-verified engineering candidate; the physical/production acceptance items below remain open.

## Accepted CI and independently read reports

| Check | Actual result | Evidence |
| --- | --- | --- |
| Android build, APK/test APK/AAB, signature fixture, static gates | PASS | [Android Release Gate 41932647838](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/41932647838) |
| JVM | 54 tests, 0 failures, 0 skipped | Hash-verified `android-quality-reports` artifact |
| Android Lint | 0 errors; 173 warnings remain | `lint-results-debug.xml` in the quality artifact |
| API33 / Android13 | 95 tests, 0 failures/errors/skips | [API33-TESTS.xml](release-evidence/continuous/API33-TESTS.xml) |
| API34 / Android14 | 95 tests, 0 failures/errors/skips | [API34-TESTS.xml](release-evidence/continuous/API34-TESTS.xml) |
| API35 / Android15 | 95 tests, 0 failures/errors/skips | [API35-TESTS.xml](release-evidence/continuous/API35-TESTS.xml) |
| Production BuildConfig mail regression tests | 9 tests, 0 failures/errors/skips | [Release run 41932647723](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/41932647723), [raw XML](release-evidence/continuous/RELEASE-TESTS.xml) |
| Actual 15-minute emulator camera session and supporting cases | 5 tests, 0 failures/errors/skips; suite 915.818 seconds | [Long run 41932647699](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/41932647699), [raw XML](release-evidence/continuous/LONG-TESTS.xml) |
| Reference backend | 20 passed | Backend job in accepted Android Release Gate |
| Portable MySQL | 39 passed | MySQL job, Django checks/migration drift/package checks passed |
| Portable SQLite | 37 passed, 2 MySQL-specific concurrency cases skipped | SQLite job in accepted gate; these skips are not Android skips |

All six downloaded evidence ZIPs were SHA-256 checked against GitHub artifact digests before parsing. IDs, archive digests, report counts and extracted-file hashes are in [VALIDATION.json](release-evidence/continuous/VALIDATION.json). The debug APK and unsigned release AAB were built; no original-key owner update was signed or published. The preview-publish job remains intentionally disabled.

## Actual timing and file evidence

The API35 long test requested a 900000 ms window with 60-second clip targets. It produced **15 independently readable MP4 clips**, all starting with sync frames, with strictly increasing per-track video sample timestamps. A real H.264 frame was independently decoded from every clip. The long camera case itself took 904.956 seconds, including setup, stopping, validation and evidence export.

| Measurement | Actual value |
| --- | --- |
| Clip indexes | 1 through 15, sequential |
| Global first-to-last video presentation-time span | 900217222 µs = 900.217222 s |
| Boundary deltas measured | 14 |
| Maximum last-frame → next-first-frame PTS delta | 33551 µs = 33.551 ms |
| Maximum adjacent-frame PTS delta within clips | 84289 µs = 84.289 ms |
| Combined finalized MP4 bytes in this synthetic scene | 12719122 bytes |

A boundary delta includes the ordinary interval between frames; it is not automatically a period of missing video. These measurements do not establish zero sensor-frame loss, physical-phone continuity, or audio/video synchronization. `elapsedMs` in the raw test metric denotes the requested test window; the measured video span above is independently derived from the segment PTS journals.

Raw [timing metrics](release-evidence/continuous/LONG-CAMERA-METRICS.json) and [all 15 segment journals](release-evidence/continuous/LONG-SEGMENTS.json) preserve clip indexes, timestamps, sizes, SHA-256 values and stable queue job IDs. A journal's QUEUED stage records enqueue completion; final submission state is owned by the separate mail outbox. No external recipient or real SMTP provider was contacted.

The short actual-camera cases also passed on APIs33/34/35: each 65-second run produced 3 clips with 30-second targets, despite an intentionally conflicting one-second ordinary timer. Their maximum boundary deltas were respectively 33.448 / 33.446 / 34.196 ms. The respective maximum within-clip deltas were 234.533 / 85.945 / 88.800 ms, illustrating why emulator timing must not be presented as a physical no-loss guarantee.

## What the tests actually establish

- Completed first clip automatically enters the persistent queue while the second records. A synthetic acknowledging transport submits that clip while capture remains RECORDING, and an accepted job cannot be dispatched twice by the same queue.
- Ten safe simulated connection failures remain retryable after queue reconstruction; disabling continuous retries produces a reviewable failure; another clip can succeed independently. Uncertain DATA acknowledgment remains UNCERTAIN, with the private send copy and original retained.
- Background activity/screen-off emulator capture continues. Stopping a short microphone/video capture saves its final tail, with both tracks present.
- Invalid crash-tail bytes remain available for inspection and are not counted as valid saved clips; reconstruction does not create duplicate queue identities. The separate follow-up below now adds an actual SIGKILL/restart test on API35.
- The Arabic UI test uses real touches to enable continuous mode, choose 30 seconds, verify the normal timer is disabled, recreate the activity with the selection retained, start the actual continuous camera through the UI, and stop it after three seconds. This passed on all three APIs.
- The exported Arabic screenshot shows the recording page/header and quality section. It is available in the API35 diagnostics artifact of the accepted run; its viewport does not show the new continuous card, so it is not claimed as visual acceptance of that card.

## Acceptance matrix and remaining boundary

| Requested scenario | Verified boundary | Remaining |
| --- | --- | --- |
| 15-minute session / one-minute clips | PASS: actual API35 camera, 15 clips and PTS journals | Physical phone |
| First clip sent while second records | PASS: actual capture/automatic queue plus synthetic SMTP acceptance | Real provider/inbox acknowledgment |
| Internet absent five minutes, then restored | PASS: actual emulator Wi-Fi/data disabled 300478 ms; production MailWorker resumes TLS submission | Physical phone / external provider |
| One clip fails, others succeed | PASS: independent persistent jobs and synthetic transports | External provider behavior |
| Full storage | Reserve policy passed; runtime has a graceful low-space stop | Real ENOSPC/low-space device test NOT RUN |
| Screen lock | PASS: emulator screen-off | Secure PIN lock/OPPO/OEM behavior |
| App backgrounded | PASS: emulator activity CREATED while FGS records | Physical/OEM behavior |
| Low battery | Warning and normal platform restrictions retained | Device test NOT RUN |
| Unexpected process restart | PASS: host SIGKILL during actual capture, fresh PID, unchanged saved clip, stable queue and retained tail | Physical phone / OEM restrictions |
| User stops unfinished clip | PASS: five-second audio/video and three-second UI-started sessions | Physical device |
| Duplicate prevention | PASS: stable queue IDs, accepted and uncertain outcomes | SMTP does not provide exactly-once inbox delivery |
| Integrity and order | PASS: sync starts, monotonic samples, SHA-256, independent frame decode | Physical/A-V drift/thermal behavior |

## Follow-up: actual network outage and process kill

[Resilience run 41969347525](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/41969347525) passed on API35 at source `7397d06da85996dcb5a9e90f5c0742101a2b4d32`. These are two completed instrumentation cases plus one intentionally killed preparation, not three passed JUnit cases. The original 95-case matrix is unchanged because this harness is compiled only with `-PguardianResilienceTest=true`.

| Observed check | Actual result |
| --- | --- |
| Wi-Fi and mobile data disabled during actual camera capture | 300478 ms; 300 disconnected-state checks |
| Clips queued before network recovery | 4, with zero send attempts and both original/private attachment retained |
| Automatic submission after Wi-Fi returns | Production MailWorker, authenticated TLS loopback; capture still RECORDING |
| Completed session including final tail | 6 clips; 6 SMTP DATA commands; 6 unique Message-IDs |
| Attachment integrity | Every MIME attachment byte-identical to its original; originals retained after SMTP acceptance |
| Host SIGKILL during second clip | PID 11732 killed with signal 9 using the debug application's own UID |
| Fresh-process recovery | PID 13925; session INTERRUPTED; camera not automatically restarted |
| Saved first clip | SHA-256 `dffef6ba3455de7cea90f45dc6e4a86fed937dbc57e829f133a3e322c8a7e805` unchanged |
| In-flight tail and queue | Tail retained or salvaged; identical job identities after three additional recoveries |

The killed preparation's raw instrumentation correctly reports `Process crashed`; this is the intended host-induced interruption. The host script requires both completed cases, the changed PID and exported metrics before passing. This does not prove that every unfinished MP4 is playable or that frames recorded before SIGKILL can always be recovered. It also does not prove inbox receipt or exactly-once delivery across arbitrary SMTP-provider failures.

The downloaded artifact ZIP was SHA-256 verified against GitHub's digest. Raw [acceptance metrics](release-evidence/continuous/resilience/ACCEPTANCE.json), [network log](release-evidence/continuous/resilience/network.log), [recovery log](release-evidence/continuous/resilience/recovery.log), host signal/seed and [file-hash manifest](release-evidence/continuous/resilience/VALIDATION.json) are preserved. The fixture's test-only trust configuration never enters production source and no external mailbox was contacted.

## Follow-up regression gates at the resilience source

The new harness source also rebuilt the normal APK/AAB and reran the existing gates; its additional source directory is disabled for the ordinary matrix. [Release-mail run 41969347510](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/41969347510) passed all 9 cases. [Android gate 41969347438](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/41969347438) passed 54 JVM cases, zero lint errors (173 warnings), reference backend 20 cases, MySQL 39 cases and SQLite 37 cases (2 MySQL-specific skips).

[Long-camera rerun 41969347436](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/41969347436) passed all 5 cases in 913.689 seconds and again produced 15 independently decoded MP4 clips. The measured global video PTS span was 900224783 µs (900.224783 seconds), maximum boundary delta 33454 µs (33.454 ms), maximum within-clip delta 70066 µs (70.066 ms), and total finalized size 12725893 bytes. The same interpretation/physical limitations stated above apply.

The complete [PR matrix run 41969352103](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/41969352103) succeeded on all three APIs. Independently downloaded reports confirm 95 cases on each API33/34/35, zero failures/errors/skips. API33/34 raw reports in the follow-up manifest come from push run 41969347438; API35 comes from PR run 41969352103. Both runs identify source `7397d06da85996dcb5a9e90f5c0742101a2b4d32`.

Fresh raw reports, long metrics/journals and archive/file digests are preserved alongside [the resilience validation manifest](release-evidence/continuous/resilience/VALIDATION.json). The original baseline evidence is retained rather than overwritten.

## Failures resolved during verification

An initial lint attempt failed for 17 new strings missing the other supported locales; all 10 locales now contain those strings and matching format arguments. An earlier API33 attempt stopped before tests on Maven HTTP403 downloads; the accepted run executed all 95 cases successfully. An earlier API34 regression exposed the recovery-start gate/stale saved-status race: the Start action is now available during background recovery, and validation messages retain priority until a new capture starts. The original assertions remain in the accepted suite.

The first long run passed but UTP uninstalled the application before private external evidence could be pulled. Test-only export now uses MediaStore public Downloads; the accepted long script requires actual timing metrics before it can pass. Production recordings continue to use app-specific storage; this public export exists only in emulator test code.

Physical execution steps and evidence requirements are in [CONTINUOUS_PHYSICAL_ACCEPTANCE.md](CONTINUOUS_PHYSICAL_ACCEPTANCE.md); every item there remains pending until actually run.

## Local checks and production blockers

Locally executed and passed: UI release gate (9 layouts/45 actions/10 locales), NPSE dependency verification (4 checksums/POMs/notices/R8), page/help gate (36 activities/12 topics/10 locales), all continuous XML resources and numbered arguments, whitespace checks and shell syntax. Local Android SDK/Gradle/ADB were unavailable; Android execution above used actual GitHub-hosted emulators.

Physical Android10–16 coverage, especially OPPO CPH2577/Android15/ColorOS, external provider delivery, real storage/battery/thermal/OEM process death, original-key signed upgrade, and the actual cPanel account/policy/capacity assessment remain unverified. The connected desktop was checked again and remained offline. No hosting/DNS/database/paid infrastructure was changed, no production backend was activated, no main merge or store/public release occurred. Preserve installed app data and the original signing certificate; fixture-key APKs are not compatible owner updates.
