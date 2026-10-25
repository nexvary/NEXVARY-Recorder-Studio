# Guardian 0.12.2 compact dashboard acceptance

Tested source:b18b0aac76ba0217d767f0e4fcfea593e2354c64.
CI:https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/40143812943
Conclusion:SUCCESS. Publishing remains disabled.

| Gate | Observed result |
| --- | --- |
| Source UI/review/Maven provenance | PASS |
| Gradle APK/test APK/AAB build and lint | PASS |
| JVM unit tests |43 passed,0 failed,0 skipped |
| Android Lint |0 errors,162 warnings |
| API33 instrumentation |82 passed,0 failed/errors/skipped |
| API34 instrumentation |82 passed,0 failed/errors/skipped |
| API35 instrumentation |82 passed,0 failed/errors/skipped |
| AR/EN 360x800dp initial dashboard |All 9 cards fully visible above navigation on all three APIs |
| Setup entry |48dp tall; contrast and touch-size checks passed |
| AR 1.5 font |Last row reachable; tested labels unclipped |
| Actual API35 screenshots |AR/EN default and AR enlarged text inspected |
| Backend reference/SQLite/MySQL jobs |All SUCCESS |
| Original-key APK/AAB signatures/update identity |PASS; code 22 to 23, non-debuggable |
| Physical OPPO0.12.2 installation and visual acceptance |PENDING |

New cases: phoneSizedDashboardKeepsAllNineCardsAboveNavigation and
largeTextKeepsTheLastRowReachableWithoutClippingLabels. XMLs and actual geometry
JSON were downloaded from digest-verified CI artifacts and inspected. Display,
font and locale overrides apply only on emulators and restore afterward.
Instrumentation uses Debug from the same source. Release APK is generated from
that run's accepted unsigned AAB and signed separately using the existing key.

Three new NestedWeights lint warnings describe the bounded3x3 dashboard. Row
weights distribute only spare viewport space; content retains its natural
scroll height at larger fonts. Measured bounds and enlarged-text checks guard
against truncation. Warnings are retained, not globally suppressed.

An attempted shared push/PR concurrency group delayed cancellation; independent
PR validation was restored before the accepted source/run. Superseded attempts
are not test passes. Final evidence checkpoint also preserves the emulator
script's original executable bit; application/layout/test bytes are unchanged.

Detailed non-secret evidence:[VALIDATION.json](release-evidence/0.12.2/VALIDATION.json).
No user phone screenshot or private signing data is published in the repository.
