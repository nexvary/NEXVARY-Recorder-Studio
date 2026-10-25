# Guardian Studio 0.11.0 — portable managed/institution service

The existing 0.10.x recorder and AWS source remain. The new portable Django WSGI/MySQL service can run on suitable cPanel hosting or an institution-operated server without Cognito/SQS/DynamoDB. It uses server-owned SMTP, durable SQL outbox, server-clock quotas, rotating sessions, operator capacity budgets and optional private external S3/Wasabi storage. Android adds personal/institution service discovery while preserving legacy AWS connections under Advanced settings.

**Operating distinction:** with SMTP only, remote alerts contain text/location and video stays local (`ALERT_ONLY`). Full cloud evidence requires a verified private external store (`FULL_EVIDENCE`); an unverified bucket does not enable uploads. There is no local video-storage backend on shared hosting. Free has three shared remote events/month; local SOS/recording remain available. Institution enrollment and contractual entitlement are server-configured; fleet administration/license issuance dashboards are not claimed implemented.

Prepared deployment source bundle, tests, research and Android build are delivered by CI. A personal managed-service origin is a public build variable and remains empty until deployment verification. No Namecheap deployment, SMTP credentials, DNS record, storage activation or Play activation is performed automatically. Existing nexvary.com site files are not overwritten. Real provider capacity, mailbox delivery, institution operations and physical OPPO/Samsung/Pixel behavior require staged verification. See `NAMECHEAP_DEPLOYMENT.md`, `INSTITUTION_DEPLOYMENT.md`, `HOSTING_RESEARCH_2026-10-21.md`, `FREE_PREMIUM_QUOTAS.md` and `docs/REVIEW-0.10.1.md`.

The older audit below records the AWS source foundation and its original verification boundary; it is not a live cloud-deployment claim.

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
| SMTP | Camera recording: encrypted opt-in SMTP, Release Worker and private 1–10-recipient envelope in0.12.3; existing optional parent PIN and explicit recipient approval. Family/SOS legacy SMTP workers remain production-disabled; cloud delivery is separate. Local TLS/emulator evidence is recorded in docs/TEST_RESULTS_0.12.3.md; external inbox delivery Pending. |
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
