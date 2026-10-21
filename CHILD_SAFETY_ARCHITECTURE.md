# Child & Family Safety Expansion — 0.9.0 preview

This branch adds local-first assistance tools, not guaranteed protection or a remote surveillance platform. Existing recorder, video vault, manual Quick Rescue and SMTP behavior are retained. Basic SOS does not require a parent PIN or subscription.

## Source inventory

| Module | Source-backed behavior | Verification boundary |
|---|---|---|
| Screen recorder | MediaProjection consent, foreground notification, user stop, overlay/tile | OS capture consent and OEM screen-off behavior require device tests |
| Voice studio | PCM recording/enhancement and MediaStore WAV save; stops when leaving the studio | It has no background microphone service; do not market it as one |
| Background camera | Camera2 + MediaRecorder H.264/AAC, visible foreground service, native duration limit, local save | Must start visibly with permission. Physical Android 15/OEM testing remains required |
| Media tools/library | Existing file workflows, playback, delete and encrypted PIN vault | Existing tests retained; vault is not a hidden app or OS indicator bypass |
| Live broadcast | Existing RootEncoder RTMP(S) **file** streaming | No camera live uplink. External RTMP endpoint not tested by CI |
| Call recording | External ShizuCallRecorder/Shizuku companion preflight/open links | No integrated call engine or claim of capturing both sides |
| Quick Rescue | Existing editable SMS/photo/location cards and review/composer | User taps Send in the messaging app; no direct SMS permission |
| SMTP | Mandatory TLS, connection/auth test, classified failures, encrypted credentials, retry queue | Loopback runtime test is separate from owner server/device verification |
| Family safety | Persisted check-in, cancellable countdown, trusted circle, journey distance, queue, history, profile, privacy and parent controls | See limits below and physical test plan |
| Evidence | Visible audio/video recording or photo capture, closed-file manifests, SHA-256 and explicit export | Hash verifies bytes relative to a saved manifest, not legal provenance |
| Billing | Real Play client/product query/restore/pending token capture, central entitlement policy | No configured verification backend or production products. Purchases disabled |

## Components and persistence

`family/SafetyCheckManager` owns a durable session state. `SafetyPolicy` contains clock, timer, battery, arrival and motion rules. `TrustedContactsRepository`, `EmergencyQueue` and `EvidenceManager` use `FamilyStore`.

`FamilyStore` stores one AES-GCM AtomicFile under `noBackupFilesDir`, using an Android Keystore key. A global transaction lock prevents state/queue gaps, and atomic replacement protects interrupted writes. The existing app uses encrypted files; this preview extends that approach instead of adding a second database stack. State, contacts, emergency profile, guardian PIN verifier, preferences, evidence metadata, alerts, history and purchase tokens are encrypted together. Keystore/decryption failures are reported; they never silently reset consent. Video/audio bytes remain app-specific local files, not encrypted by this metadata store. Device uninstall deletes local data; export important files deliberately.

A session records UUID, phase, wall deadline, elapsed deadline and boot counter. Same-boot calculations use elapsed time; reboot uses wall time and a fresh grace countdown. AlarmManager exact access is optional. When allowed, a visible alarm-clock alarm avoids short-interval Doze quotas; otherwise inexact idle alarms plus durable WorkManager are a disclosed delayed fallback. A reminder is scheduled one minute before expiry. A live Activity ticker only renders state; it is not the timer engine.

Location is requested only by an explicit visible start/resume during an active safety session. The location foreground service requests GPS/network fixes at 60 seconds/25m, stores at most 720 trail points, observes network and battery, and optionally samples accelerometer signals. It is NOT_STICKY: after process death/reboot, the timer and queue recover, but location/sensors require returning to the visible UI. There is no default 24/7 tracking, background-location permission, route API or geofence.

Safe Journey uses a parent-configured coordinate and two fresh accurate fixes, at least ten seconds apart, inside an accuracy-adjusted 100m radius. It does not infer a road route, route deviation, or safe arrival from stale/inaccurate fixes. No maps/routing backend is invented.

## Consent and protection

Automatic sharing, active-session location, immediate SOS, audible alerts, device/medical sharing, motion, video microphone and automatic visible evidence are separate opt-ins, off by default. Test Alert asks for explicit confirmation and labels the JSON and email subject `TEST ALERT`. No test calls emergency services. SMS jobs wait for a user-reviewed composer.

Parent controls use a salted 250,000-iteration PBKDF2 verifier inside encrypted storage, a five-attempt/60-second lockout, five-minute session and background lock. Contact editing, emergency settings, SMTP, medical profile and manifests are guarded; basic SOS remains available. Protected screens hide their content before checking the gate and use FLAG_SECURE. This is accidental-change protection, not device-administrator control or remote identity verification.

The debug preview makes advanced screens testable through a separate debug entitlement bypass. It never represents a purchase. Release builds fail closed for premium features without a fresh backend-verified entitlement. Subscription products are `recorder_premium_monthly` and `recorder_premium_yearly`. Target prices belong in Play Console ($2.99 / $24.99), not screen strings. Display prices come from ProductDetails. Existing recorder/SMTP capabilities are not retroactively removed.

## Explicit remaining integration work

- No production push, cloud upload, guardian pairing, routing or Play verification server is supplied.
- Progressive upload currently provides a hash/offset-validated resumable transport engine and test-only mock. It does not upload an actively recording file, segment live capture, schedule production chunk workers, or survive physical phone destruction. UI states that upload is unconfigured.
- Evidence automatic start is supported only from a resumed visible safety screen with existing permissions and explicit prior consent. Background triggers record USER_ACTION_REQUIRED and post a notification; no camera/microphone is started from a boot/alarm receiver.
- At critical battery, the service reduces location frequency and stops motion sampling. It does not yet transcode an active video or switch a running camera capture to audio.
- Motion Safety is an experimental acceleration heuristic with cooldown and cancellation. Gyroscope/activity recognition, validated fall/crash classification and physical sensitivity calibration are not implemented.
- Full Play subscription lifecycle enforcement requires authenticated backend verification and RTDN. Restore queries real Play purchases but cannot grant ownership from an unverified token.
- Physical Android 13/14/15 tests, OEM battery behavior, real network loss/reboot, thermal and endurance measurements remain separate gates.

Do not publish a child/family safety marketing claim until the device and policy gates in the companion documents have passed.
