# Guardian Studio 0.12.0 — internal playback and guided setup

Recording Library now plays normal video and audio in an app-private Media3
player with seek controls, speed selection, orientation switching and saved
position. Playback pauses when leaving and releases the decoder on stop. Only
local content URIs are accepted. Missing files display a recoverable error.
Vault entries retain the existing secure player and capture protections.

The home page, Settings and Family Safety open a five-step setup: health profile,
guardian, child, recording/playback, then safety review. Progress and saved data
use the existing encrypted family store. These are local profiles, not online
accounts or remote device pairing. Child name and age update the emergency
profile while retaining health notes and consent choices. Saving a contact
email does not automatically enroll a recipient or send an alert.

Setup can be skipped for personal recording. Recording and SOS remain accessible.
Cloud deployment awaits the hosting decision/Namecheap reply. New UI text is
provided for all ten existing locales, with format checks in the review gate.

Tests cover actual video decoding and paused-position restoration, internal
library navigation, missing files, health/consent preservation, progress across
recreation, and access to recording during incomplete setup. Physical OPPO /
ColorOS playback and rotation validation remain required.
