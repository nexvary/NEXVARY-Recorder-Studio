# Launcher appearance 0.13.1 — verified engineering candidate

Users can choose Guardian, Clock or Calendar from Settings → Appearance → App name and icon. All three shortcuts open the existing Guardian/child-mode entry point. Confirmation and the configured parent PIN protect changes; the original shortcut is restorable.

This changes launcher presentation only. Android application identity, app-settings visibility, recording notifications, stop controls and normal camera/microphone indicators remain. No uninstall blocking, all-shortcuts-hidden mode, dial-pad receiver, keyboard/accessibility interception or OS restriction bypass was added.

## Accepted checks

Application/signing source: `be3658d1970660d896e4772545fed5e5b78e42df`. Final test-readiness source: `df37350d23fa2bc9901cb043fd4c83d003747862`.

- [Final push matrix 42008437177](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/42008437177) and [parallel PR matrix 42008445344](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/42008445344) both succeeded: 100 cases on EACH API33/34/35, zero failures/errors/skips. Preserved final API33 XML comes from the parallel PR run; API34/35 XML comes from the push run.
- [Focused API29/API35 run 42003973334](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/42003973334): five cases each; [follow-up 42008437259](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/42008437259) also succeeded. Cases cover real alias labels/resolution/launch, reconstructed selection, concurrent controllers, cancel/apply/rotation, parent-PIN protection, and changing shortcuts during actual camera capture with unchanged PID/session plus a readable finalized MP4.
- Quality archive: 54 JVM cases, zero failures/skips; lint zero errors, 173 retained warnings. Reference backend 20, MySQL39, SQLite37 passed (two MySQL-only SQLite skips).
- [DEBUG=false mail acceptance 42003984011](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/42003984011): nine cases, zero failures/errors/skips; follow-up 42008445157 also succeeded.

## Initial failures and retained assertions

The first focused run at 2655539 had one failure per API: Espresso searched for an underlying settings control in the focused parent-PIN dialog root. The corrected test inspects the actual activity content is INVISIBLE and the gate remains locked, then cancels and confirms the alias is unchanged. Other functional assertions were retained.

At be3658d, the complete push matrix passed all 100 cases per API, but parallel PR API34 timed out in the existing FamilySafetyTest confirmation case after activity recreation. The exact original cause was not established by logs. Test-only df37350 waits for the rendered ARMED state and an enabled action before the actual Safe-button tap; original resolution timeout, saved-field/session-ID and stale-cancel assertions remain. Both final full matrices succeeded. No production safety-state logic changed. Initial failure XMLs remain alongside accepted reports.

## Signed owner APK

`NEXVARY-Guardian-Studio-0.13.1-release-signed.apk`: 16862003 bytes; SHA-256 `9c1e8d5f404e1659b14fcfb5e7684033b5e7419a89eb50d5427b5ec3f66131f0`.

Official bundletool 1.18.3 generated the universal release APK from the accepted unsigned release AAB. Local official apksigner v3 verification and verify_owner_apk.py against the actual 0.13.0 APK passed: same original certificate/package, non-debuggable, code26 → code27. Certificate SHA-256: `ca4640c7afcd5522210b2b60aefe121be2bc4ece87a0b9ca4ee14425891be7f6`. Passwords were checked absent from APK contents and temporary decrypted material removed. No private signing key/password was uploaded to GitHub/CI.

The be3658d and df37350 bundles were compared entry by entry. Only `base/root/META-INF/version-control-info.textproto` differs; all application code, manifest, resources, libraries and other uncompressed entries are identical. The signed APK records the be3658d application build; final tests use df37350. An old hardcoded CI artifact label said v0.13.0 even though its compiled manifest is verified as 0.13.1/code27; artifact labels are corrected separately.

## Remaining physical boundary

OPPO/ColorOS launcher caching/pinned shortcuts, actual owner upgrade/data preservation and hardware behavior remain unverified. The connected desktop was offline. Keep PR #19 draft; no main merge, store/public release or hosting change occurred.

OPPO documents native hidden-app access through a dial-pad access number: https://support.oppo.com/ru/answer/?aid=neu13836 . This is an owner-configured OS feature, not implemented/enabled by this app. Availability and effects on recording/notification stop controls require testing on the actual CPH2577. Uninstalling/clearing data may remove unsent app-private recordings/queues; received off-device copies are separate. SMTP submission acknowledgment is still not inbox receipt.

## Evidence recovery

The local workspace became unavailable during the final documentation write after APK signing, upload and test-report verification. A documentation-only GitHub job re-downloads these pinned GitHub artifacts, checks their server metadata/digests and suite counts, and preserves exact raw report bytes here. It contains no signing secrets and changes no application/test code. The signed APK was already retained for owner download before that interruption.

## Owner signing fallback and OPPO checklist

The unsigned universal APK was regenerated with bundletool 1.18.3 from
[the successful release-bundle artifact](https://github.com/nexvary/NEXVARY-Recorder-Studio/actions/runs/42008437177/artifacts/13405363886).
The archive matched its recorded SHA-256. Android build tools confirmed the
package `com.nexvary.recorder`, versionName `0.13.1`, versionCode `27`,
absence of the debuggable flag, and 16 KiB ZIP alignment. The unsigned APK
SHA-256 is `f3d0ea20e85355ecd8ccd888ba2b35efa3fb1af7c39431b8d586d8d1530b28a9`.
The previously verified signed APK and original key were unavailable to this
session. ADB listed no device. The unsigned file is for signing in the
owner environment only.

1. On the OPPO, record the ColorOS build and installed APK version. Pull the
   installed base APK privately and use it as `--previous` in
   `scripts/verify_owner_apk.py`. For a strict upgrade from code 27 or higher,
   build a release with a versionCode greater than the installed value before
   signing. Verify the candidate with `apksigner verify --verbose --print-certs`
   and the repository verifier after signing with the original key.
2. Record the current settings, recording count and outbox item states. Keep
   an unsent test item offline while comparing states. With no recording active,
   install the verified APK with `adb install -r` and compare the same screens
   before reconnecting. Open an existing video and capture and play a new one.
3. In Settings → Appearance → App name and icon, try Guardian, Clock, Calendar
   and restore Guardian. Confirm that wrong PIN and cancellation leave the old
   choice active, then apply with the correct PIN. Launch each choice and
   reboot with an alternate choice active. Compare the drawer and a home-screen
   shortcut. If the drawer and Android's resolved launcher alias agree but a
   pinned icon is stale, remove only that shortcut and pin the current drawer
   entry again. If Android resolves an unexpected alias, investigate app state
   before attributing the behavior to ColorOS.
4. Repeat the switch during a user-started test recording. Check Android's
   capture indicators and the recording notification, stop using the
   notification button, and play the resulting video.
5. If the phone exposes Hide Apps in its system settings, enable it for the
   test and open the hidden-apps folder with the configured access code in the
   phone dial pad. Restore visibility by turning off Hide Apps for Guardian.
   Check the recording notification and Stop button while hidden. If the option
   is absent or Stop becomes inaccessible, use the in-app appearance choices.
   Keep access codes and personal recording/outbox details out of the PR.

The emulator capture test stops through the service action and reads the MP4
with MediaExtractor. The PIN test covers the locked screen and cancellation.
Wrong-PIN entry, full phone reboot, pinned icons, notification-button interaction
and data preservation across an owner-signed install remain physical checks.
