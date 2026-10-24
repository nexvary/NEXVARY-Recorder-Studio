# Guardian 0.12.1 test evidence

Tested source: 4492685768a29145607a1862b1ca9575892a8331.
CI: https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/40138155110
Conclusion: SUCCESS; publishing job intentionally skipped.

| Gate | Observed result |
| --- | --- |
| Source UI/review/Maven provenance | PASS |
| Gradle build/lint/test APK/AAB | PASS |
| JVM unit tests | 43 passed, 0 failed, 0 skipped |
| Android Lint | 0 errors, 159 warnings |
| Android 13/API33 instrumentation | 80 passed, 0 failed/errors/skipped |
| Android 14/API34 instrumentation | 80 passed, 0 failed/errors/skipped |
| Android 15/API35 instrumentation | 80 passed, 0 failed/errors/skipped |
| Backend, portable SQLite and portable MySQL jobs | All SUCCESS |
| Original release key matches old/current APK | PASS |
| Owner-delivery verifier rejects old Debug certificate | PASS |
| Owner-delivery verifier rejects obsolete source version | PASS |
| APK v3/AAB JAR signatures, package/code/non-debuggable | PASS |
| API35 Arabic More visual review | PASS; actual CI screenshot inspected |
| Physical OPPO signed APK install and data preservation | PENDING; no connected device |

XMLs were downloaded from hash-verified CI artifacts and parsed: both NPSE
host cases are present and passed on each API. UI instrumentation is Debug
from the same source, not a signed-Release runtime/device test. Original and
corrected APK certificates were read from real APKs. The exact failure on the
owner's phone was not obtained; different delivered certificate identities
are proven, while the installed phone certificate is unknown.

The local report-inspection helper initially looked only for direct testcase
children in a testsuites XML. It was corrected to traverse nested suites;
the test counts above come from the corrected parser and actual case nodes.
The local jarsigner launcher was absent; its official installed JDK module
was used for AAB signing and verification. Neither issue was an app test failure.

Detailed non-secret hashes, signature output and rejection evidence:
[release evidence](release-evidence/0.12.1/VALIDATION.json).
Private keys/passwords remain outside the repository and were not distributed.
