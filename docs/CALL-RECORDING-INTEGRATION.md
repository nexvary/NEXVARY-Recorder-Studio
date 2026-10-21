# Call recording integration — ShizuCallRecorder evaluation

Upstream: https://github.com/kitsumed/ShizuCallRecorder

## Architecture decision
Add a separate, explicitly enabled Call Recording module. Do not silently mix call audio with screen or camera recordings.

Upstream components to review:
- integrations/shizuku/ShizukuConnectionManager.kt
- integrations/scrcpy/ScrcpyClient.kt, ScrcpyAudioSource.kt, ScrcpyAudioMuxer.kt
- services/shell/ShellAudioPipeline.kt, ShellService.kt
- services/callDetection/CallDetectionOrchestrator.kt
- services/recording/AudioRecordingEngine.kt, RecordingForegroundService.kt
- services/RecordingDecisionEngine.kt

## Delivery gates
1. Review upstream GPL-3.0 Section 7 additional terms and third-party scrcpy license/attribution before importing code. No upstream source has been copied yet.
2. Separate user opt-in, permissions, Shizuku pairing, and recording state with clear recording indication.
3. Provide manual and automatic modes only after user activation; exclude contacts and emergency calls as appropriate.
4. Persist audio using MediaStore/SAF, with selectable AAC/Opus, clear retention/deletion controls.
5. Handle Shizuku unavailable/revoked, OS upgrades, call waiting, dual SIM, Bluetooth, interrupted sessions, and locked screen.
6. Test on real Android 15 device. Never claim call audio capture based solely on emulator tests.
7. Verify local recording consent requirements before enabling automatic capture.

## Important limitations
Upstream targets carrier calls; third-party VoIP is out of scope. Hidden Android APIs and OEM customizations make functionality device-dependent. Shizuku is a user-managed dependency, not a root bypass guarantee.
