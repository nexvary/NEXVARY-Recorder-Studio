# NEXVARY SOS and Live Camera — engineering reference

## Verified upstream references
- https://github.com/dhilipmpms/SOS-alerter — SMS dispatch, location tracking, history; GPL-3.0. Study design, do not copy into differently licensed app.
- https://github.com/OksigeniaSL/oksigenia-sos — GSM/SMS multipart handling, delivery acknowledgement, offline operation. Check its exact license before reuse.
- https://github.com/dev-xero/sos-client — editable incident details, attachments and contacts; GPL-3.0.
- https://github.com/ly2xxx/sos — offline emergency contacts and GPS; MIT.
- https://github.com/pedroSG94/RootEncoder — RTMPS camera streaming and simultaneous MP4 recording. Already in app Gradle dependencies.
- https://github.com/google/material-design-icons — official Android-ready icons, Apache-2.0; use source assets, tint by semantic function.

## Release blockers
1. Replace legacy SOS dynamic dialog with stateful, lifecycle-safe editor; image selection currently returns after dialog dismissal and may not persist correctly.
2. SMS-only pathway needs explicit delivery and failure state, user-triggered send, dual-SIM selection, and no silent success claims.
3. MMS/image sending must preserve intended recipient; current generic share chooser does not guarantee recipient delivery.
4. Get a fresh location with bounded timeout; show last-known age and location-unavailable fallback.
5. Camera service should expose explicit started/failed/stopped status, persisted output, and failure-safe finalization.
6. Implement actual Camera2/RootEncoder RTMPS live pipeline and simultaneous local recording; current LiveBroadcastService streams only existing files.
7. Show visible recording indicators and foreground notification; respect Android camera/background restrictions.
8. SOS UI: RTL, large tap targets, official colored Material icons, clear error and sent/queued states.
9. Run lint, unit tests, emulator navigation/back tests, screenshot capture; inspect artifacts before requesting physical-device validation.
10. Review permissions, privacy, and upstream licenses before publishing.

## Safety and acceptance
Emergency functions must not claim a message was delivered merely because an SMS intent was opened. A successful build is not equivalent to a tested rescue workflow. Never rely on this app as the sole emergency channel.
