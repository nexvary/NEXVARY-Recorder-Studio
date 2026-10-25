# Guardian 0.12.3 — camera email acceptance

Tested head: `8ded2a73cacde2cf0c91750f02208c8f45a7b9f9`, branch
`feat/camera-multi-recipient-mail`, PR [#16](https://github.com/nexvary/NEXVARY-Recorder-Studio/pull/16).
Parent: `9bccfa1060696142023fd3164e58f84bfd7e05b5` (accepted compact dashboard).
Final documentation checkpoint changes no application/test/build bytes; inspect
git log -1 for its commit. No merge to main or public release was performed.

## Verified build and transport

[Android Release Gate40168288269](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/40168288269):
PASS, including all86 instrumentation cases on EACH of AOSP APIs33/34/35,
0 failures/errors/skips. Downloaded hash-verified raw XML confirms the new mail
cases, existing safety/PIN regression cases and normal recording without email.
The source, build, static and backend gates also passed.
UI/source review and pinned NPSE Maven provenance passed. Gradle lintDebug,
testDebugUnitTest, assembleDebug, assembleDebugAndroidTest and bundleRelease
passed;49 unit cases,0 failures/skips. Android Lint:0 errors,164 warnings.
The two warnings above0.12.2 are now-unused legacy single-recipient UI strings;
other existing dependency/style/accessibility warnings remain visible, with no
broad suppression. All backend and portable SQLite/MySQL jobs passed.

[Camera Mail Release Acceptance40168288246](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/40168288246)
PASS:6 cases,0 failures/errors/skips on AOSP API35. Release DEBUG=false was
asserted at runtime. Public fixture signing is used only for instrumentation;
that APK is not the owner update. Real Arabic RTL screenshots were inspected,
including the enabled email switch and deduplicated two-recipient review.

The six Release cases verify encrypted recipient defaults, refusal to claim
legacy unapproved queue jobs, explicit retry approval, one successful handoff
without resending, TLS and STARTTLS two-recipient envelopes, recipient privacy
in transmitted MIME headers, cancellation before capture, optional PIN gates,
an actual scheduled Release Worker, and actual timed Camera2 MP4 save → one
private send copy → two SMTP recipients. The decoded attachment matches the
local recording byte-for-byte. The original is retained; the accepted private
copy is removed. Unit tests additionally verify malformed/header-injection
input, maximum10 distinct addresses, duplicate removal and SMTP RCPT rejection
aborting before DATA for every recipient.

All SMTP servers in tests bind127.0.0.1 with public test-only credentials and
trust material injected by test code. Production retains Android trust/hostname
verification. No external recipient or owner SMTP account was contacted.
Server acceptance is explicitly distinguished from actual inbox receipt.

## Regression failure investigated

Initial complete run40167163403 failed one legacy FamilySafety test on API33
and two on APIs34/35. New mail UI tests grant POST_NOTIFICATIONS earlier in the
suite; Logcat records OS heads-up banners over the test's safety-button taps.
The ensuing state timeout/missing dialog did not involve SMTP transport. The
emulator harness now disables OS heads-up overlays before UI automation.
Application channels, status notifications and camera privacy indicators are
unchanged. No tests were skipped and no production code was changed to satisfy
this fixture issue. The fresh complete acceptance status is recorded above;
the initial failed run and its diagnostics remain available for review.

## Signed owner update

File: `NEXVARY-Guardian-Studio-0.12.3-release-signed.apk`,16792122 bytes.
Package: `com.nexvary.recorder`; version0.12.3/code24; non-debuggable.
APK SHA256: `af8d4e279af4c6d51e992c5f8ae7ae7c6a0ed398354ce6b389db76c957f03dc3`.
Original release certificate SHA256:
`ca4640c7afcd5522210b2b60aefe121be2bc4ece87a0b9ca4ee14425891be7f6`.

Owner bundle source: `918ed35ce8dc36ae3a75c47231fef65e937254b1`, built by
run40167163403 and signed using the existing original Guardian key. The later
head changes only emulator scripts. Byte comparison of its AAB confirms all
1355 application payload entries match the owner bundle; the sole differing
entry is the embedded Git commit marker. See BUNDLE-PAYLOAD-COMPARISON.json. Official apksigner verification, package/version/non-debuggable
checks and comparison with the prior0.12.2 original-key release passed. Signed
AAB jarsigner verification also passed. Install as an update; do not uninstall
or clear app data. Signing-key compatibility verification does not constitute
a physical installation pass. No private signing material is committed.

Small machine-readable provenance/signature/test XML records are in
[release-evidence/0.12.3](release-evidence/0.12.3). Full raw quality and emulator
reports are actual workflow artifacts in the linked runs. Screenshots are
actual emulator windows from the Release acceptance app with synthetic
`.invalid` addresses, not generated imagery or a physical OPPO run.

## Pending owner/device checks

Physical OPPO/ColorOS installation, battery/background scheduling under its
actual settings, external owner SMTP authentication/message acceptance, real
inbox delivery and Android16 runtime remain Pending. SMTP constraints, message
size and recipient policies vary by operator. Legacy queued jobs require fresh
review after upgrade; uncertain DATA outcomes never auto-resend. Normal camera
recording remains available without configuring email or Shizuku.
