# Guardian compact dashboard candidate — 2026-10-25

Branch fix/guardian-compact-dashboard; parent 2e954cc4a4738e5e5a5eef164fab385ea31f4eb4.
Candidate 0.12.2/code23; original package/release key retained. Source HEAD: inspect git log -1.

Owner screenshot showed an oversized setup entry and dashboard icons, with the
last row only partly visible. Implemented home-only compact setup entry (32dp
icon, at least48dp touch target, themed readable text), 52dp grid icons, 128dp
minimum cards, tighter header spacing and 26dp bottom icons/64dp bar. Other
pages retain their default entry sizes. Text can grow and the page still scrolls.

Added two real emulator regression cases: all nine cards fully visible above
navigation at 360x800dp/1.0 font in Arabic and English, and reachable/unclipped
last row at 1.5 font. Actual screenshots/measurement JSON required by CI.
Display changes are emulator-only and restored in finally; these are not OPPO
hardware results. Existing source UI/review/Maven gates passed locally.

PENDING current Gradle/lint/unit/runtime/visual acceptance and original-key
release signing. Next: accept CI, inspect actual profile screenshots, sign the
accepted AAB, verify update against the 0.12.1 release, save owner APK/evidence.
Do not deliver until current results are recorded. No data clear/package rename.

---

Previous accepted checkpoint (historical, not current-candidate results):

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
