# Guardian0.12.4 — individual recipient cards, implementation checkpoint

Branch: fix/guardian-recipient-picker; parent98ee78f974cd513f98ffb4c4d10afdfcca46b09c.
Replaces delimiter-based entry with one email field, Add/IME action, removable
recipient cards and a count/limit of10. Reuses the same editor in camera and
outbox retry; migrates saved addresses, preserves rotation drafts, prevents
invalid/duplicate entries, and includes a valid unfinished draft in consent.
Existing queue, encrypted profile, consent/PIN, private SMTP envelope and local
original retention are unchanged. Version0.12.4/code25. Source UI/review/NPSE
checks PASS; Gradle, runtime and original-key signed update PENDING.
Next: run both Android gates, inspect actual Release Arabic screenshots,
correct failures and deliver an original-key update. Physical OPPO/external
SMTP/inbox and Android16 remain Pending. No MR changes or public release.

Previous accepted checkpoint follows; do not rerun or reimplement its features.

# Guardian 0.12.3 — camera multi-recipient mail checkpoint, 2026-10-25

Branch: `feat/camera-multi-recipient-mail`; PR #16, based on PR #15 branch
`fix/guardian-compact-dashboard` at9bccfa1060696142023fd3164e58f84bfd7e05b5.
Last tested source head:8ded2a73cacde2cf0c91750f02208c8f45a7b9f9.
The final documentation checkpoint changes no source/test/build bytes; its
current commit is available with `git log -1` / `git rev-parse HEAD`.

## Implemented

- Restored camera email controls and MailWorker in non-debuggable Release.
- 1–10 bounded, validated mailbox addresses; comma/semicolon/newline and Arabic
  separators; case-insensitive duplicate removal; no display-name/header syntax.
- Explicit recipient review before every email-enabled recording, with the
  existing optional guardian PIN. Regular recording defaults to email off.
- Existing encrypted SMTP profile stores last-used recipients; settings edits
  preserve them. Legacy plaintext camera preference is removed on confirmed save.
- One existing private attachment copy/job per recording. Multi-recipient SMTP
  envelope hides other addresses from MIME headers. Any RCPT rejection aborts
  before DATA for everyone. Server acceptance is not claimed as inbox receipt.
- Outbox is protected by the existing optional PIN; retry reviews/edits recipients.
  Old jobs without persisted approval require review; interrupted DATA never
  silently auto-resends. Originals remain on the phone.
- Reused the existing queue, SMTP, encryption, transport and camera engines.
  Family/SOS legacy SMTP restrictions remain. No NEXVARY-MR changes or new app.

## Verified

Android Release Gate40168288269: build/static/backend jobs PASS;49 JVM cases,
0 failures/skips; Android Lint0 errors/164 warnings. APIs33/34/35:86 cases EACH,
0 failures/errors/skips. Camera Mail Release Acceptance40168288246
PASS:6 cases,0 failures/errors/skips on API35, with DEBUG=false asserted.
Actual loopback TLS/STARTTLS, private envelopes, byte-identical attachment,
queue migration, cancellation/PIN and timed camera → queue → Worker paths passed.
No external SMTP server or recipient was contacted by tests. The first complete
run exposed OS heads-up banners intercepting safety UI taps after early mail
permission grants; CI now disables those overlays only on the emulator. No
application behavior was changed for that fixture issue. Details and raw XML:
docs/TEST_RESULTS_0.12.3.md and docs/release-evidence/0.12.3.

Owner APK: NEXVARY-Guardian-Studio-0.12.3-release-signed.apk,16792122 bytes,
com.nexvary.recorder,0.12.3/code24, non-debuggable. Original release certificate:
ca4640c7afcd5522210b2b60aefe121be2bc4ece87a0b9ca4ee14425891be7f6.
APK SHA256:af8d4e279af4c6d51e992c5f8ae7ae7c6a0ed398354ce6b389db76c957f03dc3.
Verified original-key compatibility with the previous0.12.2 release using SDK
apksigner/aapt2; signed AAB also verified. Owner bundle was built from918ed35
by40167163403. The later source changes only emulator scripts; byte comparison
confirms all1355 application payload entries match, except the separate embedded
Git checkout marker. Temporary decrypted signing files have been removed.
Do not deliver public-test-key CI APKs as owner updates.

Actual Arabic Release emulator screenshots: Guardian-0.12.3-camera-mail-AR.png
and Guardian-0.12.3-mail-review-AR.png. These contain synthetic .invalid addresses;
they are not generated images or physical OPPO evidence. Release instrumentation
uses a public fixture signing key; the owner APK uses the original release key.
The compact0.12.2 home layout is retained; previous evidence remains in
`docs/TEST_RESULTS_0.12.2.md`.

## Remaining

Owner installs the
signed APK as an update without uninstall/data clear, configures/tests their own
SMTP, enters recipients and confirms one short recording. Physical OPPO/ColorOS
installation/background/battery behavior, external SMTP, inbox receipt and
Android16 runtime remain Pending. No main merge, public/store release, server
activation or automatic real-person email was performed.
