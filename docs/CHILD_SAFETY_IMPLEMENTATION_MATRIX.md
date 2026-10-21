# Implementation matrix — 0.9.0 preview

Status describes source behavior; test results belong to the exact Actions commit. An implemented feature is not a physical-device certification.

| Request sections | Implemented source | Remaining boundary |
|---|---|---|
| 1 | Existing app/services/storage/SMTP/CI inventory in CHILD_SAFETY_ARCHITECTURE.md | Physical/OEM source-to-device comparison |
| 2,16,37,45 | Encrypted durable check-in state, monotonic deadline, pre-expiry reminder, grace, Safe/Extend/SOS, alarm + WorkManager and UUID race protection | Force-stop/OEM timing cannot be guaranteed; action states are parallel to core timer state |
| 3 | Silent notification default, optional audible channel, app/Quick Tile/widget/in-app volume triggers | No global Power interception, Accessibility trigger or floating SOS overlay |
| 4 | Encrypted trusted circle CRUD, roles, phone/email/relation/channel, explicit legacy rescue import | No backend guardian pairing or push network |
| 5 | Versioned JSON payload/schema, location age, battery/network/time, opt-in medical/device metadata | Cloud recording links absent; no fabricated data |
| 6,25 | Real audio/video/photo capture, closed-file SHA-256 manifests, verification/export and ten-second local video test | Background automatic capture requires visible user action; not a legal chain of custody |
| 7 | Resumable transport interface and chunk engine with offset/hash checks and disconnect test | No live segmentation, production upload worker or configured cloud backend |
| 8 | Actual bitrate-based Compact/Emergency/Balanced/High profiles, MB/min/storage estimates, AAC economy | HEVC selection/transcoding and moving-scene OEM size measurements pending |
| 9 | Optional encrypted local emergency profile, separate display/share opt-ins | No default upload |
| 10 | Deduplicated thresholds, optional critical saver slows location/stops motion | Monitoring currently uses active location service; active video-to-audio/profile handoff not implemented |
| 11,12 | Durable offline alert jobs, network-constraint retry, active-session trail and current/last location distinction | No 24/7 tracking; monitoring resumes visibly after process death/reboot |
| 13 | Destination coordinate/ETA/trusted contact, distance display, two-fix accurate arrival, overdue prompt | No routing/map source; no automatic cancellation of an active SOS on arrival |
| 14 | Routing absence explicitly disclosed | Route deviation deferred until reliable routing source exists |
| 15 | Separate acceleration heuristic with sensitivity/cooldown and cancellable impact countdown | No gyroscope/activity-recognition fusion or validated medical/crash detection |
| 17 | Existing rescue retained; family entry, richer queue/contact/evidence flow | Manual legacy composer and new family dispatcher remain separate workflows |
| 18 | Existing secure SMTP test/save; persisted classified test diagnostics, email queue failures/retry | Server acceptance is not delivery acknowledgement; no external mailbox was contacted by CI |
| 19 | Reviewed SMS composer, no restricted SMS permission; policy alternatives documented | Direct SMS/backend push not implemented |
| 20,41 | Visible foreground starts, permission checks, retained screen-off emulator tests; 13/14/15 CI matrix | No physical OPPO/Samsung/Pixel or Android 15 reliability claim |
| 21 | Privacy Center permission reasons/status, notification/alarm/battery settings and upload state | No permission requested for absent integrations |
| 22,33–35 | Large child actions, existing glossy black/neon/silver assets, Arabic RTL resources, scrolling/haptic/live status | Physical TalkBack/large-font/user study remains a release gate |
| 23 | Independent parent PIN, lockout/session/background lock; guarded settings/contacts/SMTP/profile | Device-admin identity or remote parental control not claimed; SOS unblocked |
| 24 | Explicit TEST ALERT confirmation, labeled JSON and mail subject | Real guardian delivery only after user opt-in, never an emergency-service test |
| 26,27 | Safety status, remaining time, primary guardian, location/battery/network, evidence action, encrypted history | No remote guardian dashboard; UI history shows latest 100 from bounded local history |
| 28–30 | Real Billing 8.3 client, ProductDetails, pending tokens/restore, central entitlement policy and verifier interface | No production products/backend verification/RTDN; purchase flow disabled, debug feature access explicitly separate |
| 31,32 | Permission/foreground/SMS/Families/Billing review notes and restrained assistance claims | Account-specific Play declarations/Data safety/privacy approval pending |
| 36,38,39 | Separate family/billing components, Keystore/AES-GCM, encrypted migration, no sensitive application logs | Existing media bytes are app-private, not encrypted by the metadata store |
| 40 | Policy/hash/entitlement/chunk unit tests and family UI/state/queue/TLS/real-capture instrumentation | Real network/reboot/Doze, sensor calibration and Play licensed tests pending |
| 42 | Emulator diagnostics plus instrumented process CPU/PSS/thermal/storage smoke sample | No battery-drain/endurance/thermal certification |
| 43 | Available space and recording-time estimate, low-storage warning/start guard | Actual encoder size and OEM storage failures still require device tests |
| 44 | Independent queued recipients/actions; cancellation/consent re-check; UNCERTAIN stops duplicate retry | In-flight or accepted mail cannot be recalled |
| 46 | No fake server, location, billing grant, push or upload success in production | Test mocks are confined to tests |
| 47–49 | Build/unit/Lint/instrumentation workflow, version 0.9.0, debug APK/unsigned AAB artifacts, required documents | Publish only after exact-head checks and screenshot review; no merge to main |
| 50 | Implementation proceeds with persisted checkpoints on development branch | Full production backend and physical review remain outside this preview |
