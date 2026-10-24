# Guardian Studio 0.12.1

- Retains Guardian identity `com.nexvary.recorder`; versionCode 22.
- More identifies the embedded NPSE page as optional Device diagnostics in all
  ten locales. Guardian runs independently of the MR helper and Shizuku.
- Owner APK delivery uses the original Guardian release certificate, preventing
  the known public-Debug versus Release signature mismatch for prior releases
  signed by that certificate. Exact failure on the owner's phone remains
  unverified until the resulting update is installed.
- No recording, permission, family data, account, evidence or server behavior
  changes. No uninstall/data clear, package rename, privacy bypass or publishing.

Validation and physical-device limitations: DEVELOPMENT_STATE.md and
docs/SIGNED_DELIVERY.md. Do not infer a completed test from this release note.
