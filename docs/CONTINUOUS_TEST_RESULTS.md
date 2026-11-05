# Continuous recording — verified engineering candidate 0.13.0

Verified implementation: `0b4c90c31bddc5ae7c38fadf63aca0cda82f6c91` (code 26), PR #18, stacked on the recipient-card branch/PR #17. Reviewed on 5 November 2026 (Cairo). Documentation/evidence commits after this implementation do not change application or test source. This is an emulator-verified engineering candidate; the physical/production acceptance items below remain open.

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
- Invalid crash-tail bytes remain available for inspection and are not counted as valid saved clips; reconstruction does not create duplicate queue identities. This is not an actual OS process-kill test.
- The Arabic UI test uses real touches to enable continuous mode, choose 30 seconds, verify the normal timer is disabled, recreate the activity with the selection retained, start the actual continuous camera through the UI, and stop it after three seconds. This passed on all three APIs.
- The exported Arabic screenshot shows the recording page/header and quality section. It is available in the API35 diagnostics artifact of the accepted run; its viewport does not show the new continuous card, so it is not claimed as visual acceptance of that card.

## Acceptance matrix and remaining boundary

| Requested scenario | Verified boundary | Remaining |
| --- | --- | --- |
| 15-minute session / one-minute clips | PASS: actual API35 camera, 15 clips and PTS journals | Physical phone |
| First clip sent while second records | PASS: actual capture/automatic queue plus synthetic SMTP acceptance | Real provider/inbox acknowledgment |
| Internet absent five minutes, then restored | Safe-retry persistence fixture passed | Actual five-minute network outage/recovery NOT RUN |
| One clip fails, others succeed | PASS: independent persistent jobs and synthetic transports | External provider behavior |
| Full storage | Reserve policy passed; runtime has a graceful low-space stop | Real ENOSPC/low-space device test NOT RUN |
| Screen lock | PASS: emulator screen-off | Secure PIN lock/OPPO/OEM behavior |
| App backgrounded | PASS: emulator activity CREATED while FGS records | Physical/OEM behavior |
| Low battery | Warning and normal platform restrictions retained | Device test NOT RUN |
| Unexpected process restart | PASS: reconstructed persistence/incomplete-tail fixtures | Actual OS kill/restart NOT RUN |
| User stops unfinished clip | PASS: five-second audio/video and three-second UI-started sessions | Physical device |
| Duplicate prevention | PASS: stable queue IDs, accepted and uncertain outcomes | SMTP does not provide exactly-once inbox delivery |
| Integrity and order | PASS: sync starts, monotonic samples, SHA-256, independent frame decode | Physical/A-V drift/thermal behavior |

## Failures resolved during verification

An initial lint attempt failed for 17 new strings missing the other supported locales; all 10 locales now contain those strings and matching format arguments. An earlier API33 attempt stopped before tests on Maven HTTP403 downloads; the accepted run executed all 95 cases successfully. An earlier API34 regression exposed the recovery-start gate/stale saved-status race: the Start action is now available during background recovery, and validation messages retain priority until a new capture starts. The original assertions remain in the accepted suite.

The first long run passed but UTP uninstalled the application before private external evidence could be pulled. Test-only export now uses MediaStore public Downloads; the accepted long script requires actual timing metrics before it can pass. Production recordings continue to use app-specific storage; this public export exists only in emulator test code.

## Local checks and production blockers

Locally executed and passed: UI release gate (9 layouts/45 actions/10 locales), NPSE dependency verification (4 checksums/POMs/notices/R8), page/help gate (36 activities/12 topics/10 locales), all continuous XML resources and numbered arguments, whitespace checks and shell syntax. Local Android SDK/Gradle/ADB were unavailable; Android execution above used actual GitHub-hosted emulators.

Physical Android10–16 coverage, especially OPPO CPH2577/Android15/ColorOS, external provider delivery, real storage/battery/thermal/process death, original-key signed upgrade, and the actual cPanel account/policy/capacity assessment remain unverified. The connected desktop was checked again and remained offline. No hosting/DNS/database/paid infrastructure was changed, no production backend was activated, no main merge or store/public release occurred. Preserve installed app data and the original signing certificate; fixture-key APKs are not compatible owner updates.
