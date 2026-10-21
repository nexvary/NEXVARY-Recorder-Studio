# Guardian Studio 0.10.0 — source audit and verification boundary

## Source audit

Default main at audit: `d51e6339b7e30af6eec954a44dc6745cc5ea47e1` (0.6.0). Existing product work was in `feature/background-camera-recording` at `4eeb96c9170174cb726058b3f7f357d2f50da1ba`. Guardian branch deliberately builds on that current product work; it does not discard it or change `com.nexvary.recorder`.

| Component | Existing source / change |
|---|---|
| Voice | Real local capture/enhancement; preserved under Media Center |
| Camera | Camera2 + MediaRecorder FGS, timed capture; emergency 720p profile and timed progressive files added |
| Screen | Existing MediaProjection FGS and overlay; preserved |
| Media | Existing conversion/remux, audio replacement and trim; new extraction, metadata/share and Media3 video compression |
| Rescue | Existing local SMS composer/contact cards; new fast emergency surface, dialer, silent mode and recording |
| Family | Existing encrypted timer/check-in, profile, consent, contacts, integrity, history, guardian PIN and journey foundation; new Home destination and Child Mode |
| SMTP | Existing encrypted opt-in development/fixture capability; production workers disabled; cloud uses authenticated backend SES |
| RTMP | Existing file-to-RTMP/RTMPS foreground service; preserved |
| Library/vault | Existing local recording library and protected vault; preserved |
| Billing | Existing ProductDetails/restore client; Guardian product IDs, verified account binding, backend verification and Free gates added |
| Backend | New SAM API Gateway, Cognito, Lambda, SQS, DynamoDB quota transactions, SES, private S3/optional Wasabi, deletion and cost alarms |

## Status boundaries

The Android app and AWS deployment source are implementation candidates. No live AWS deployment, DNS update, verified SES identity, Sandbox exit, Play product activation or real Wasabi validation occurred. Short-lived access tokens are renewed with an encrypted Cognito refresh token; passwords and provider keys are not stored in Android. Parent cloud setup must be completed before remote sending works.

Free remains useful offline: local SOS and evidence, one trusted contact, check-in and profile. Three remote packages/month are server-reserved. Same-session taps append markers. Different sessions in cooldown merge to the existing remote alert, preserve new evidence locally and do not upload another full package. Resolving an unsent offline event cancels its pending cloud notification; accepted or currently sending messages cannot be retracted.

Emergency video uses native 30-second file duration limits, validates and hashes closed files, then reopens the camera inside the active service. A short restart gap may occur; this is not gap-free forensic continuous capture. Automatic uploads require network, authentication, consent and quota. OEM process termination can lose an open segment, never guarantee recording after force-stop, and the app cannot guarantee inbox delivery.

## Physical Device Verification (required before a production claim)

Android 15 OPPO/ColorOS first, then Samsung and Pixel: screen off for 10 minutes, over another app, thermal/battery restrictions, camera/mic privacy controls, lock/unlock, process reclaim, force-stop, reboot, storage full, 5% battery, airplane mode → reconnect, flaky upload, fresh signed URL after expiry, playback/integrity of every segment, measured camera restart gaps, file size/minute, stop/save notification. Screen projection behavior on lock must be reported separately from camera behavior.

Cloud stage: verify actual SES account/identity/sandbox status, only controlled test recipients, complete signed upload → checksum → FIFO → SES → viewer → expiry → delete, confirmed contact invitation, auth expiry/refresh, concurrent quota reservations and real subscription purchase/restore/refund/hold. Publish privacy/deletion webpages and complete Play target-audience/Data Safety/FGS declarations. Preview AAB is unsigned; public debug signer is not a production key.

Do not mark an unrun physical/cloud test passed. CI/emulator results and artifact SHA-256 should accompany delivery.
