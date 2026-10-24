# Reviewed NPSE binary dependency

NPSE 0.1.0-alpha01 is built once in NEXVARY-MR and consumed here as a Maven
library. No provider, runner, AIDL, or capability-policy source is copied into
Guardian. This small file Maven repository is intentionally checked in so a
normal clone builds without GitHub Actions artifact authentication or expiring
URLs. Its group is resolved exclusively from this repository.

SOURCE.json records the reviewed upstream commit, successful Actions run,
artifact id/archive checksum and exact binary/POM checksums. The archive was
retrieved through the GitHub connector and its SHA-256 verified before extraction.
The AAR is unchanged. The small POMs express the source modules' explicit
transitive dependencies; no remote repository redirect is embedded in them.
This is checksum/provenance review, not a signed software attestation.

The AAR includes NPSE/API license assets and Binder R8 consumer rules. Notices
are also included beside this file. NPSE/API MIT; no Shizuku manager is bundled.

To upgrade: review upstream diff and licenses, obtain a tested source-tagged
artifact, verify its archive digest, update both Maven coordinates/POMs and
SOURCE.json together, run scripts/verify_npse_dependency.py, build/lint/unit
and host instrumentation. Do not replace these binaries from a moving branch.
