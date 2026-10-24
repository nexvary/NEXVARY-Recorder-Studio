# Guardian signed delivery correction — 2026-10-25

Branch: fix/guardian-signed-delivery. Parent: 7ba1ef1d2fccbc5a9af3319d3f9b521644d74f06
(PR #13); inspect git log -1 for the current checkpoint commit.

Candidate 0.12.1/versionCode 22; package com.nexvary.recorder retained.
The reported installation conflict led to checking the actual artifacts:
previous owner release ca4640c7…91be7f6; NPSE debug delivery 6b0f84e5…81cbf76.
These signatures differ. The installed phone certificate was not read, so this
is evidence of an incompatible delivery pair, not a claimed phone error code.

Implemented: raised version; optional Device diagnostics More entry in all ten
locales; separated Guardian installation instructions from MR helper testing;
explicit signed-owner-delivery process. Original release key recovered privately
from the existing encrypted backup, never added to git. See docs/SIGNED_DELIVERY.md.

Local UI/review/dependency gates and git diff --check: PASS.
CURRENT BUILD/LINT/UNIT/EMULATOR/SIGNED DELIVERY: PENDING. Do not distribute this
candidate until its current CI and signature checks have been accepted.
Next: accept current CI; sign its unsigned AAB using the original key; verify
APK/AAB certificate/package/non-debuggable; deliver Guardian only.
Physical OPPO update preservation and precise installer diagnosis remain pending.

Previous accepted baseline follows; its results are not current-candidate tests.

---

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
