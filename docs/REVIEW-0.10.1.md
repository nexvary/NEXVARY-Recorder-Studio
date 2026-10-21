# Guardian Studio 0.10.1 — page review and operating guide

Package remains `com.nexvary.recorder`; version code 17. This update preserves the glossy black, metallic silver and colored raster icon system. Only emergency cards use red.

## Requested changes

- More is a full scrollable page with 12 real destination cards, explanations, bundled realistic colored icons, RTL support, safe Back navigation and one column at large font sizes. Theme and language actions respect the Guardian PIN.
- Offline operating guide: 12 expandable questions, search, result count, rotation state and quick links. Available from More, Settings and Child Mode. All guide and card copy ships in Arabic, English, German, Spanish, Persian, French, Italian, Russian, Turkish and Urdu. Links open pages without starting recording, sending mail or creating an SOS.
- Help explains recording permissions, background/screen-off behavior, local SOS, quotas, guardian controls, cloud setup, progressive evidence limitations, library/export, media tools, troubleshooting, companion call recording and distinct test alerts.

## Review scope and corrected defects

Reviewed manifest, navigation, all 34 internal activity classes, recorder/media services, family state/consent, private evidence, billing/entitlements, cloud outbox, backend reservation/upload/dispatch paths, resources and CI. Automated page rendering is a smoke check of each registered activity, not a guarantee that every hardware or external-service path has been exercised.

| Finding | Correction and evidence |
| --- | --- |
| More used a plain text modal; theme action skipped the guardian gate | Real card page; card navigation and PIN instrumentation tests |
| Exported media used a folder omitted from Library queries | Unified new exports; keep legacy folder readable; actual video fixture export and Library test; public Share/Export action |
| Voice enhancement loaded an entire long recording into arrays | Two streaming PCM passes; WAV/header/silence tests and a 64 MiB input enhanced in a child JVM capped at 24 MiB heap |
| Audio read/write failures could escape the capture thread | Controlled capture error, attempt partial save, release effects, verify output publication, remove failed pending rows |
| Logged-out billing could throw or reuse another account's entitlement | Guard purchase launch; bind verified entitlement/purchase response to account |
| Account change/logout could leave old outbound jobs/cache eligible | Revoke session nonce, cancel old transport jobs, clear account-bound caches; preserve local SOS/evidence; account lifecycle instrumentation |
| Last video segment/retry could revive a cancelled upload | Preserve cancellation and check session/cancellation between network stages; cancellation regression test |
| Expired signed upload URL became a terminal failure | Obtain a new signed URL on retry without a new event reservation |
| Subscription expiration could change an active event's storage lifecycle prefix | Fix storage tier at reservation; backend expiration regression test; reject boolean segment index |
| Two legacy pages contained hardcoded bilingual instructions | Localized call companion and evidence pages; link to internal help |
| Screen recording claimed saved after a failed MP4 finalization | Check playable metadata and successful MediaStore publication; valid/corrupt output instrumentation |
| Stop during RTMP preparation could allow a late stream start; failed connections retained the service | Serialized session generation, stale-callback rejection and resource cleanup; three session lifecycle unit tests (live server remains unverified) |
| Some document providers reject persistable URI permission | Accept the available transient grant without crashing the picker |

## Verification

CI performs Android build, release AAB, lint, JVM tests, backend tests with mocked AWS, Ruff, CloudFormation lint, page/help declaration gate and instrumented emulator tests on Android 13/14/15 (API 33/34/35). Real Arabic screenshots are captured by the app's instrumentation, including More, Guide and a filtered FAQ result. Final run and counts are recorded in the delivery report after successful completion.

The page gate checks all app Activity declarations, private internal pages and complete help copy in all ten languages. Tests cover actual WAV capture/playability and video export/Library visibility, along with the existing media, safety, camera/evidence, PIN, privacy and navigation cases.

## Physical Device Verification and external dependencies

Still requires physical OPPO/ColorOS, Samsung and Pixel testing: prolonged recording, screen lock, process kill, power saving, thermal/low battery, actual full-storage behavior, hardware codecs, phone-call companion/Shizuku and foreground-service restrictions. Emulator tests cannot establish these OEM outcomes.

Live RTMP server behavior, live Cognito/SQS/SES/S3 deployment, SES Sandbox/production state, Wasabi credentials/end-to-end, and real Play purchases/renewal/hold are not verified here. No AWS/DNS/Play resources are created or changed by this update. Cloud quota enforcement and cloud storage require deploying the documented backend; cloud messaging must not be described as active before that deployment. Existing architecture/setup/privacy/policy documentation remains applicable.

Known operational boundaries: audio studio saves when leaving its page; Android 15 may stop screen capture on lock; progressive segments can contain short restart gaps and an unfinalized last segment can be lost on process kill; cryptographic hashes do not certify legal admissibility. PIN loss has no hidden recovery bypass. Local recording and local SOS remain usable without cloud quota or a subscription.
