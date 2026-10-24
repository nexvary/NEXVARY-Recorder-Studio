# Guardian installable delivery

Guardian is the family/child safety and recording product. NEXVARY-MR is a
separate repair product. The NPSE dependency is an embedded optional library;
neither MR desktop nor its Android helper is required to install or use Guardian.
Standard device diagnostics work without a Shizuku manager. Additional shell
diagnostics require an explicitly started manager and user permission.

## Installation conflict correction — 2026-10-25

The previously delivered `NEXVARY-Guardian-NPSE-0.12.0-debug.apk` is signed with
the public automated-test certificate
`6b0f84e5767eb07b99b8e4e5b277cca9ac093fe96e132df3008566fe581cbf76`.
The owner's earlier signed Guardian 0.12.0 APK has certificate
`ca4640c7afcd5522210b2b60aefe121be2bc4ece87a0b9ca4ee14425891be7f6`.
Both fingerprints were extracted from actual APKs. These APKs are not compatible
updates. The phone installer screenshot alone does not prove its installed
certificate or the precise PackageInstaller failure; the phone was not connected.
The separately reported MR helper conflict is unresolved and is not a Guardian
installation prerequisite.

0.12.1 keeps `com.nexvary.recorder`, raises versionCode 21 to 22, and changes
the optional More entry to Device diagnostics in all ten locales. Its release
delivery must use the existing Guardian release key, kept outside git and CI
logs. Never replace that key, rename the package to sidestep an upgrade, or ask
the owner to uninstall/clear data as a routine installation step.

## Reproduce

1. Accept Android Release Gate for the exact source: lint, unit tests, build,
   backend gates and emulator matrix. Download its unsigned release AAB.
2. Restore the existing signing backup in a private location. Verify the key's
   public certificate against the pinned release fingerprint above. Passwords
   must be passed by protected files/environment, never command literals/logs.
3. Build a universal APK with official bundletool 1.18.3, SHA-256
   `a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`,
   using the existing release key. Sign the AAB with jarsigner. Do not sign the
   debug APK and present it as a release build.
4. Verify the APK with official apksigner and inspect its compiled manifest:
   correct package/version, non-debuggable, Guardian-scoped providers. Verify
   AAB jarsigner signature and exact same public certificate. Compare the
   release certificate with the previous delivered APK.
5. Deliver only the Guardian release APK as the owner's installable update.
   An AAB and technical evidence may be supplied separately for developers.
   Do not bundle an MR APK or the public-debug APK as required applications.

CI artifacts containing `debug` are explicitly development/test artifacts;
unsigned AABs require external release signing before owner delivery. CI does
not publish a release or store listing automatically. Physical OPPO install and
retained recordings/settings are pending until observed on that device.

See DEVELOPMENT_STATE.md for the current source and accepted results.
