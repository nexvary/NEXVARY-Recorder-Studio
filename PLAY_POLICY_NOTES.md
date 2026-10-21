# Android / Google Play review notes

Source review date: 2026-10-21. These are implementation notes, not approval from Google Play. Distribution eligibility, target audience, Data safety declarations, privacy policy, foreground-service declarations and reviewer evidence must be completed before a public store release. The preview AAB is unsigned, not production-ready.

| Sensitive capability | Why / when | User-facing disclosure and alternative |
|---|---|---|
| CAMERA + camera FGS | User starts local camera/evidence capture visibly | Runtime permission, persistent stop notification and OS indicator. Denial leaves other SOS actions available |
| RECORD_AUDIO + microphone FGS | Optional video microphone or visible audio evidence | Separate opt-in/permission; audio-only evidence can be stopped. Ordinary voice studio stops on leaving |
| MediaProjection FGS | User-approved screen recording | System projection consent each capture session; no boot-start screen recording |
| COARSE/FINE + location FGS | Opt-in active Safety Check/Journey/SOS only | Disclosure before arming, persistent notification, stop on resolution; last-known timestamp disclosed. No ACCESS_BACKGROUND_LOCATION requested |
| POST_NOTIFICATIONS | Check-in question, countdown and recording controls | Request at start on API 33+. Check-in warns/blocks if notifications disabled; basic manual SOS remains accessible |
| SCHEDULE_EXACT_ALARM | User-started deadline and grace prompt | Optional special access; visible alarm-clock scheduling. Inexact alarm + WorkManager fallback disclosed. No USE_EXACT_ALARM permission |
| RECEIVE_BOOT_COMPLETED | Reconcile persisted deadline and queued alerts | Does not start camera/microphone/location after boot; user must reopen to resume active tracking |
| INTERNET / ACCESS_NETWORK_STATE | Explicit SMTP/broadcast and durable queue constraints | SMTP requires configured server and sharing consent; no default cloud uploads |
| SYSTEM_ALERT_WINDOW / WRITE_SETTINGS | Existing optional screen-recorder overlay/settings behavior | Special access only when that existing feature is requested; family SOS does not require overlay permission |
| Contacts / phone | Manually entered trusted contacts | No READ_CONTACTS or call-log permission. Local encrypted records; no address-book upload |
| SMS | Emergency message review/composer | No SEND_SMS/READ_SMS requested. Technical direct-SMS capability and Play eligibility are separate; physical-safety exception still requires Play review |
| Accessibility / hardware triggers | Not used by family module | No Accessibility service, Power interception or hidden/global key hooks; optional volume pattern only in focused app |
| Billing | Genuine Google Play subscription product/restore APIs | Products/prices from Play; no purchase flow until real backend verification is configured |

## Official references

- Foreground service types and while-in-use restrictions: https://developer.android.com/develop/background-work/services/fgs/service-types
- Background foreground-service starts: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Exact alarm scheduling/access: https://developer.android.com/develop/background-work/services/alarms/schedule
- Android 14 exact-alarm access changes: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Google Play SMS/call-log policy and emergency exceptions: https://support.google.com/googleplay/android-developer/answer/10208820
- Billing integration: https://developer.android.com/google/play/billing/integrate
- Subscription lifecycle / backend verification: https://developer.android.com/google/play/billing/lifecycle/subscriptions
- Families policy: https://support.google.com/googleplay/android-developer/answer/9893335
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469

## Release blockers and claims

Child/family audience selection is a product/store review decision, not implied certification. No ads SDK is added by this expansion. Do not claim guaranteed child protection, prevention of kidnapping, medically accurate fall detection, always-on capture, guaranteed mail delivery, or remote guardian connectivity without a backend. Describe safety checks, family assistance and evidence tools accurately.

Complete account-specific Play foreground-service/location declarations and Families/Data safety/privacy review. Verify any third-party Shizuku/companion distribution separately; this application does not embed a call recorder. Production billing needs authenticated purchase verification and RTDN processing for grace, hold, cancellation, expiry and revocation. Never embed service account keys. The development signing key is public and must never sign a production store build.
