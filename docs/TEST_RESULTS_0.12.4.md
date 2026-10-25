# Guardian0.12.4 — individual recipient cards

Source:29e336004609c396ad32d451e2eaff9fd5230f23; branch
fix/guardian-recipient-picker; PR [#17](https://github.com/nexvary/NEXVARY-Recorder-Studio/pull/17).
Parent:98ee78f974cd513f98ffb4c4d10afdfcca46b09c (accepted0.12.3 email delivery).
The final evidence/documentation checkpoint does not change app/test/build bytes.
Inspect git log -1 for its current commit.

## Build and runtime results

[Android Release Gate40172598115](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/40172598115):
PASS:source/static/build/backend gates and all89 instrumentation cases on EACH
of AOSP APIs33/34/35,0 failures/errors/skips. Downloaded hash-verified raw XML
confirms all three new recipient-editor cases on each API, together with all
existing mail, recording, safety, navigation, layout and NPSE cases.
Gradle lintDebug, testDebugUnitTest, assembleDebug, assembleDebugAndroidTest and
bundleRelease passed.49 unit tests,0 failures/skips. Android Lint0 errors,
165 retained warnings (one additional unused legacy camera-email helper text
resource compared with0.12.3); no broad suppression. Existing source/UI review
and pinned NPSE dependency provenance passed. All backend jobs passed.

[Camera Mail Release Acceptance40172598116](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/40172598116)
PASS on AOSP API35:9 cases,0 failures/errors/skips. BuildConfig.DEBUG=false
is asserted. This instrumentation APK uses the public fixture certificate only;
the owner APK is separately signed with the existing original release key.

New real UI interactions cover adding individual addresses with the Add button
and keyboard Done action, clearing the successful input, invalid and duplicate
rejection, touching each card's close icon, list/draft survival across rotation,
capacity10 disabling entry, removing one and adding a replacement, and using the
same editor for an outbox retry without modifying recipients on cancellation.
Stored canonical recipients are loaded as cards. A valid unfinished draft is
included before consent; invalid drafts block it. Removing the complete list is
not undone by a delayed encrypted-profile load or restoration of the saved activity state.

Existing Release checks still cover encrypted settings, legacy queue approval,
PIN/cancellation, actual TLS/STARTTLS envelopes without recipient disclosure,
actual timed Camera2 MP4 save/private-copy queue/scheduled Worker and attachment
byte equality. Tests use only loopback SMTP and synthetic .invalid addresses.
No external recipient or real SMTP provider was contacted.

Actual Arabic Release screenshots were downloaded from the accepted workflow
artifact, archive SHA256 checked, and visually inspected. They show the single
email field, Add button, count, removable cards and recipient consent dialog.
They are emulator windows, not generated images or physical OPPO evidence.

## Owner update

NEXVARY-Guardian-Studio-0.12.4-release-signed.apk:16800314 bytes,
com.nexvary.recorder,version0.12.4/code25, non-debuggable.
SHA256:33aa49e36be135009049531ef54c8f023592abf1d5a18f305b9ea1c862e9eba1.
Original release certificate SHA256:
ca4640c7afcd5522210b2b60aefe121be2bc4ece87a0b9ca4ee14425891be7f6.

Built from this source's unsigned AAB and signed using the original Guardian
key. SDK apksigner/aapt2 checks and comparison with0.12.3 prove the same
certificate and higher versionCode. Signed AAB jarsigner verification passed.
Temporary decrypted signing files and downloaded unlock copy were removed.
Install as an update without uninstall/data clear; no signing secrets in git.
Small raw XML/signature/provenance records: docs/release-evidence/0.12.4.

## Pending

Physical OPPO/ColorOS installation, background/battery/keyboard behavior,
Android16, owner SMTP and actual inbox delivery remain Pending. No main merge,
public/store release, server activation or unrelated MR change was performed.
