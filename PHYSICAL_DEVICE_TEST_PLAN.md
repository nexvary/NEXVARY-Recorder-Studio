# Device verification plan and evidence ledger

No physical Android phone is connected to this workspace. AOSP emulator results must never be recorded as OPPO/ColorOS, Samsung or physical Pixel results. User-reported SMTP connectivity on their phone is not a complete physical regression test.

| Platform | CI scope | Physical status |
|---|---|---|
| Android 13 / API 33 | Configured AOSP CI matrix; use actual Actions results as authority | OPPO/Samsung/Pixel not run |
| Android 14 / API 34 | Configured AOSP CI matrix; use actual Actions results as authority | OPPO/Samsung/Pixel not run |
| Android 15 / API 35 | Existing AOSP stable navigation/screen-off/SMTP gate plus new family instrumentation | OPPO/Samsung/Pixel not run |

## Required scenarios on each supported OEM

1. Install/upgrade with the pinned preview key; retain ordinary recordings, vault PIN, SMTP and preferences. Repeat permission denial/revocation for camera, microphone, approximate/precise location and notification channels.
2. Record camera video with/without audio, screen off, Home and other-app transitions; native timer/manual notification stop must close a playable file. Measure one and five moving-scene minutes for Compact/Emergency/Balanced/High, actual codec/size/duration and MIME budget.
3. Check-in reminder, expiry, grace, Safe, Extend and SOS; repeat Activity recreation, process kill, reboot, wall-clock change, Doze, exact access denied/revoked and blocked notification channel. Force-stop limitations must remain disclosed.
4. Explicit active-session location; approximate permission, no GPS, stale fix, destination distance and two-fix arrival. Confirm no location or sensors remain after resolving and no background capture after boot.
5. Silent versus audible channel, DND, lock screen, Quick Tile, widget and in-app volume pattern. All automatic impact triggers must allow cancellation; no claims of global Power handling.
6. Consent revoked/contact edited/deleted while queued; SMTP missing, invalid auth/TLS, server rejection, offline retry and ambiguous DATA termination. No automatic duplicate retry after UNCERTAIN. SMS composer never marks a message SENT.
7. Ten-second evidence and longer capture; verify manifest after close, re-hash, mutation/missing-file failure and explicit export. Ordinary recordings must not be stopped by a stale evidence session.
8. Simulate battery 15/10/5 and recovery; record actual location sampling and motion shutdown. Evaluate whether a future safe video-to-audio handoff is needed rather than claiming it exists.
9. Parent PIN: wrong attempts/lockout, background lock, settings/contact/SMTP protection; SOS always reachable. Large font, RTL phone/email/coordinates, TalkBack, haptic and minimum touch target review.
10. Real backend chunk upload/resume, guardian push and Play licensed test purchases are blocked until real services/products are configured. Test subscription pending, acknowledgement, restore, grace, hold, cancellation, expiry and revoked token with server evidence then.

## Performance measurements

Collect `dumpsys meminfo`, `dumpsys batterystats`, `dumpsys thermalservice`, CPU samples, free storage and recording size over 30/60-minute sessions with screen off. Record OS build, device, battery optimization state, temperature and network. Compare camera-only, audio-only, location and motion enabled. CI captures logcat, last ANR, device properties, memory and instrumentation reports; these are smoke diagnostics, not battery/thermal endurance certification.

## Screenshot acceptance

Use only installed-current-APK captures after instrumentation succeeds. No Pixel Launcher ANR, system crash dialog, starting window or permission overlay may cover them. Protected settings intentionally use FLAG_SECURE; do not disable it merely for a screenshot. Tag all images with run/commit provenance. Any emulator ANR or FATAL EXCEPTION invalidates screenshots and blocks preview publishing.
