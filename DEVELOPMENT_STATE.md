# Guardian0.12.4 — individual recipient cards checkpoint, 2026-10-25

Branch: fix/guardian-recipient-picker; PR #17; base feat/camera-multi-recipient-mail
at98ee78f974cd513f98ffb4c4d10afdfcca46b09c (PR #16, accepted0.12.3).
Last tested code commit:29e336004609c396ad32d451e2eaff9fd5230f23.
The final documentation/evidence checkpoint changes no app/test/build bytes;
its current commit is available with git log -1 / git rev-parse HEAD.

## Implemented

- Replaced delimiter-based recipient entry with one mailbox field and Add button
  or keyboard Done action. Each address becomes a removable card; count/max10.
- Invalid and duplicate addresses are rejected before insertion. Close icons
  have localized accessible labels,48dp minimum chip targets and full-address
  descriptions/tooltips when text is truncated. Full addresses remain in consent.
- Reused the same component for camera and outbox retry. Saved recipients load
  as cards, including older canonical lists. Rotations preserve cards/draft;
  user removal is not overwritten by asynchronous default loading. A valid
  unfinished draft is included in review; invalid drafts block sending.
- Redundant helper paragraph removed; all10 locales now describe individual
  addition/removal. Outbox display uses one address per line.
- Reused existing SMTP transport, encrypted profile, queue, PIN/consent and
  local original retention. No contacts permission, extra service, new transport
  engine or MR change. Version0.12.4/code25.

## Validation

Android Release Gate40172598115: source/UI/review/provenance, Gradle builds,
unit/lint and backend jobs PASS.49 unit tests,0 failures/skips. Lint0 errors,
165 warnings; the extra warning is unused legacy camera_email_note after removing
its redundant paragraph. APIs33/34/35:89 cases EACH,0 failures/errors/skips.
Camera Mail Release Acceptance40172598116 PASS on API35:9 cases,0 failures,
errors or skips, DEBUG=false asserted. Tests cover actual Add/IME/touch removal,
rotation/draft, invalid/duplicate rejection, capacity10/replacement, same-editor
outbox cancellation, existing TLS/privacy/approval/PIN, timed Camera2 MP4 and
scheduled Worker. Only loopback SMTP and synthetic addresses used.

Owner APK: NEXVARY-Guardian-Studio-0.12.4-release-signed.apk,16800314 bytes,
com.nexvary.recorder,0.12.4/code25, non-debuggable.
SHA256:33aa49e36be135009049531ef54c8f023592abf1d5a18f305b9ea1c862e9eba1.
Original release certificate SHA256:
ca4640c7afcd5522210b2b60aefe121be2bc4ece87a0b9ca4ee14425891be7f6.
Built from this code's unsigned AAB; official SDK signature/package/version
checks and original-key comparison against0.12.3 passed. Signed AAB verified.
Temporary decrypted signing files and downloaded unlock copy removed; no secrets
in git. Public-fixture-key CI APKs are not owner updates.

Actual Arabic Release screenshots: Guardian-0.12.4-recipient-cards-AR.png and
Guardian-0.12.4-recipient-review-AR.png. Downloaded archive hashes checked;
images inspected. These show actual emulator windows with synthetic addresses,
not generated images or an OPPO runtime. The Release instrumentation app uses
the public fixture key; the delivered APK uses the original owner key.

## Remaining / next action

Owner installs the signed APK as an
update without uninstall/data clear. Open Camera, enable email, enter one
address and tap Add for each recipient; × removes one. Existing SMTP settings
are preserved. Review the final list before capture. Physical OPPO/ColorOS,
Android16, real owner SMTP and inbox receipt remain Pending. No main merge,
public/store release or server activation. Earlier accepted checkpoints:
docs/TEST_RESULTS_0.12.3.md (email) and docs/TEST_RESULTS_0.12.2.md (compact home).
