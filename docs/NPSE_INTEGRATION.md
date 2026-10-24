# Guardian NPSE integration

Base: feature/guardian-studio bde1110629f0295b026848b8cd2651c69fa0f5e8.
NPSE 0.1.0-alpha01 comes from MR commit 0908a3363401b2ccba4292fa82a27062ab6c908f,
Actions run 40113664208: build/unit/lint and absent-manager API33/34/35/36
AR/EN/RTL emulator acceptance passed. Guardian consumes its exact AAR/JAR through
an exclusively scoped file Maven repository, with reviewed provenance/checksums
and notices under third_party/npse. No engine source is forked or copied.

More now has a real entry to the non-exported control center. Entry honors the
existing configured Guardian PIN gate. The center collects standard aggregate
reports and has an optional, consented shell-only diagnostic path. Its onStop
cancels work and clears privileged observations. This is a diagnostic page, not
a recording control or a replacement for the Android permissions screen.

CallCapturePreflight delegates permission/backend checks to NPSE. A root backend
can no longer count as authorized readiness. Companion presence/readiness still
does not prove call audio capture. Camera/microphone indicators, runtime grants,
MediaProjection consent and foreground-service constraints remain mandatory.
Automatic Sui initialization is disabled by the host Application before provider
initialization. No INTERNET-backed NPSE transfer is configured by this host.

Validation: scripts/verify_npse_dependency.py, existing UI/review gates, full
Gradle lint/unit/APK/test-APK build and NpseIntegrationTest. Actual results are
recorded in DEVELOPMENT_STATE.md/Actions; adding a test is not a pass. Physical
OPPO/ColorOS and official/Next Binder/reboot acceptance remain pending.

## Acceptance evidence

Source 7f324204e2f60ab1aa42136668856676157e45b6, run 40126176694:
build/lint/unit/signature/backend checks passed; API33/34/35 each 80 UI tests,
0 failures/errors/skips. XML reports confirm both new host cases (internal activity
+ Standard report without manager; configured PIN at More entry). Actual Arabic
More screenshots show the NPSE card. Host real-manager/OEM acceptance is pending;
the same shared engine passed official/Next UID-2000/Binder-death tests in the MR
host, source 514afe8 / run 40126853544. No root, release promotion or privacy bypass.
