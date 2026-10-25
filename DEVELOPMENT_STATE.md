# Guardian 0.12.3 camera mail — implementation checkpoint, 2026-10-25

Current branch: feat/camera-multi-recipient-mail. Parent source:
9bccfa1060696142023fd3164e58f84bfd7e05b5; use git log -1 for current commit.
Restored camera email in Release; 1–10 validated private recipients; explicit
pre-recording approval and existing optional guardian PIN; encrypted recipient
defaults; editable outbox retry; old pending jobs require fresh review. Reused
existing SMTP/queue/Worker, retained local originals. Family/SOS and MR unchanged.
Local source gates PASS. Gradle, unit/emulator and dedicated Release runtime
acceptance PENDING. Next: run both CI gates, correct failures, sign the accepted
owner release with the original key and record actual evidence. See
docs/CAMERA_MAIL_INTEGRATION.md and docs/TEST_RESULTS_0.12.3.md.

## Previous accepted checkpoint

Branch: fix/guardian-compact-dashboard; PR #15; base fix/guardian-signed-delivery.
Last tested source: b18b0aac76ba0217d767f0e4fcfea593e2354c64.
Final checkpoint adds evidence/docs and restores the existing emulator script's
executable bit. No app/layout/test bytes changed after accepted source. Inspect
`git log -1` for the current checkpoint commit.

Owner supplied a 0.12.1 home screenshot with an oversized setup banner and a
partly clipped last row. Implemented home-only 48dp setup entry/32dp icon and
readable theme text,44dp brand logo,52dp grid icons and128dp card minimums,
tighter header/padding, and26dp bottom icons in a64dp bar. Rows share spare
space when content fits; large text grows and scrolls. Other feature entries
retain their normal sizes. Package/permissions/storage/release key unchanged.

VERIFIED: Android Release Gate40143812943 SUCCESS on the source above.
- Source UI/review/Maven provenance, Gradle lint/build/test APK/AAB,43 unit
  tests (0 failures/skips), and all backend jobs passed.
- Lint:0 errors,162 warnings, including3 NestedWeights warnings for the finite
  three dashboard rows. No broad suppression; raw reports retained in CI.
- APIs33/34/35:82 instrumentation cases EACH,0 failures/errors/skips.
  Downloaded hash-verified XML confirms both DashboardLayoutTest cases passed.
- 720x1600px/320dpi (360x800dp) geometry in AR/EN on all three APIs: all 9 cards
  completely visible above bottom navigation at initial scroll; setup height
  exactly 48dp.1.5-font case keeps last row/labels reachable and unclipped.
- Actual API35 AR/EN default-profile and AR 1.5-font screenshots inspected.
  AR default image shows the complete final row and readable setup text.
- Release APK/AAB signed using original Guardian key, official signatures
  verified. APK: com.nexvary.recorder,0.12.2/code 23, non-debuggable,16771642 bytes.
  Current and previous0.12.1 release certificate:
  ca4640c7afcd5522210b2b60aefe121be2bc4ece87a0b9ca4ee14425891be7f6.
  APK SHA256:92c1a7ca104a62f88ddab7afc7c9f765de1e19b1dd4ec558ebb4155fc3182672.
  Temporary decrypted signing material removed; no signing secrets in git/logs.

Deliverables: NEXVARY-Guardian-Studio-0.12.2-release-signed.apk and actual
Guardian-0.12.2-compact-home-AR.png. Install as an update without uninstall/data
clear. See docs/TEST_RESULTS_0.12.2.md and docs/release-evidence/0.12.2.

PENDING: owner's physical OPPO installation/visual acceptance of0.12.2 and any
manufacturer-specific behavior. The supplied0.12.1 screenshot is the issue
input, not a0.12.2 device pass. Instrumentation used Debug from the same source;
matching-profile emulator evidence is not a signed-Release phone runtime pass.
No main merge, server activation, store/release publication or unrelated product
changes. Next: owner installs the signed update and confirms the home layout.
Do not repeat accepted gates unless a new change/failure justifies it.

---

Historical0.12.1 checkpoint (superseded by the current checkpoint above):

# Guardian 0.12.1 signed delivery — accepted checkpoint, 2026-10-25

Branch: fix/guardian-signed-delivery; PR #14 (base feature/npse-integration).
Last tested implementation: 4492685768a29145607a1862b1ca9575892a8331.
This final evidence checkpoint is documentation only; inspect git log -1 for its
commit. No production code changed after the accepted source.

Implemented: versionCode 22/versionName 0.12.1, retaining com.nexvary.recorder;
optional Device diagnostics More card in all ten locales; explicit independent
Guardian delivery instructions; scripts/verify_owner_apk.py refuses wrong keys,
debuggable APKs, source-version mismatches and non-increasing updates.
Guardian does not require the MR Android helper or a Shizuku installation.

VERIFIED: Android Release Gate 40138155110 SUCCESS on the tested source.
- UI/review/dependency gates, Gradle lint, 43 unit tests (zero failures/skips),
  APK/test APK/unsigned AAB and all three backend jobs passed.
- Lint: zero errors, 159 warnings retained in the actual quality artifact.
- APIs33/34/35: 80 UI tests EACH; zero failures/errors/skips. Downloaded XMLs
  confirm both NPSE host cases including Standard mode with no manager.
- Actual API35 Arabic More screenshot visually inspected: optional diagnostics
  copy is readable, RTL correct, inspected cards have no clipped text.
- Universal Release APK generated from this run's hash-verified AAB; APK and
  AAB signed externally using the original recovered Guardian key. APK v3 and
  AAB JAR verification passed; non-debuggable, correct package and higher code.
  The original release APK and current APK both have certificate SHA-256
  ca4640c7afcd5522210b2b60aefe121be2bc4ece87a0b9ca4ee14425891be7f6.
- Current APK SHA-256: 93d5e76ce4157dd0377005730795b0a91d835a41c653f9a77b26b415890513ee.
  Saved deliverable: NEXVARY-Guardian-Studio-0.12.1-release-signed.apk (16767546 bytes).
  Temporary decrypted key/unlock copy removed. No secrets added to git/logs.

The previous NPSE Debug delivery used a different certificate (6b0f84e5…81cbf76).
The verifier correctly rejects it as an owner release. The phone's installed
certificate/error code has not been read; no definitive phone diagnosis claimed.

PENDING: physical OPPO/ColorOS installation, retained recordings/settings,
actual Guardian manager use, pairing, permission revocation, reboot and battery
behavior. UI instrumentation used Debug from the same source; this is not a
physical signed-Release runtime pass. No connected device is available.

Next: owner installs ONLY the release-signed Guardian APK as an update, without
uninstall/data clear. If rejected, inspect the actual PackageInstaller reason
and installed certificate before changing signing or package identity. Do not
repeat accepted source tests without a new change/failure. No main merge,
production/store publication, cloud activation or MR product changes performed.

See docs/SIGNED_DELIVERY.md, docs/TEST_RESULTS_0.12.1.md and
 docs/release-evidence/0.12.1/VALIDATION.json.

---

Historical checkpoints below are superseded by the accepted checkpoint above.

# Guardian NPSE accepted alpha checkpoint — 2026-10-24

Branch: feature/npse-integration; PR #13. Last tested implementation commit:
`7f324204e2f60ab1aa42136668856676157e45b6`; base guardian-studio bde1110.
The documentation checkpoint containing this file is later; inspect git log -1.

Implemented: shared reviewed NPSE Maven library; internal More control-center
entry respecting configured PIN; shared shell-only call preflight; disabled
automatic Sui initialization. No engine copy, capture bypass or package/signing
identity change. Read docs/NPSE_INTEGRATION.md and third_party/npse/README.md.

VERIFIED run 40126176694: dependency/UI/review gates, Gradle lint/unit/APK/test-APK/
AAB build, pinned debug signature, AWS/SQLite/MySQL backend checks; API33/34/35
each 80 passing UI cases with no failures/skips. Both new NpseIntegrationTest
cases passed on each API. Real Arabic More-card screenshots inspected.
Shared engine official/Next consent/Binder-death tests passed on API35 in MR
run 40126853544; these are not host-specific physical Guardian tests.

PENDING: owner's OPPO/ColorOS, actual Guardian manager use, pairing, revocation,
reboot and battery behavior; no device is connected. APK remains a debug preview.
No main merge, production release or Google Play publication. Continue physical
acceptance when a device is available; do not repeat this completed adoption.

---

# Guardian Studio development checkpoint

Current candidate: 0.12.0 (versionCode 21), package `com.nexvary.recorder`.
Integration branch: `feature/guardian-studio`.

Implemented: built-in local video/audio playback; five-step local family setup;
encrypted profiles and resumable progress; ten-language UI resources. See
`docs/release-notes-0.12.0.md` and `PlayerAndSetupTest.kt` for behavior and checks.
Existing vault playback remains private and unchanged. Settings screenshot
support from 0.11.2 is retained.

Validation commands: `python3 scripts/ui_release_gate.py`,
`python3 scripts/app_review_gate.py`, Gradle lintDebug/testDebugUnitTest/
assembleDebug/assembleDebugAndroidTest/bundleRelease and connectedDebugAndroidTest
on API 33/34/35. Consult Actions for the actual result of the current HEAD;
a checkpoint is not itself evidence of a successful build.

Release continuation: download the quality-gated release AAB, sign using the
existing guardian-release-2026 certificate (never replace it), verify package,
version and certificate, and deliver the signed APK/AAB and real UI screenshots.
Do not publish to Google Play or activate cloud services as part of this task.

Pending external items: Namecheap has been emailed by the owner and its reply
is awaited. No provider approval, DNS change or live cloud deployment is claimed.
Physical Android 15 OPPO/ColorOS checks remain required.
