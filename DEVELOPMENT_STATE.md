# NPSE integration checkpoint — 2026-10-24

Branch: feature/npse-integration; base feature/guardian-studio at
bde1110629f0295b026848b8cd2651c69fa0f5e8. Current commit: inspect git log -1.

Implemented: reviewed shared NPSE Maven dependency, internal More entry with
existing PIN gate, Standard-first control center, shared call-preflight backend
policy and disabled automatic Sui initialization. No capture bypass. Read
`docs/NPSE_INTEGRATION.md` and `third_party/npse/README.md`.

Source dependency: MR 0908a3363401b2ccba4292fa82a27062ab6c908f; upstream build,
unit/lint and missing-manager emulators API33–36 passed in run 40113664208.
Current host verification pending CI: dependency/UI/review scripts; Gradle lint,
unit/APK/test-APK build; native NpseIntegrationTest and existing recorder suite.
Physical OPPO/ColorOS/manager pairing/restart/reboot remain pending.

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
