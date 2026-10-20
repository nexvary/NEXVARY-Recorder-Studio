NEXVARY Recorder Studio 0.7.1 — reported-issue fixes

- SOS editor: explicit neon-green input text, readable hints, selection, checkboxes and action buttons, including OEM light-widget defaults.
- Library: Play, confirmed permanent Delete and Hide actions. PIN (6–12 digits, confirmed at creation) opens the private vault; lock, restore and delete are available inside.
- Hidden video data and names use chunked AES-GCM, with PIN-derived keys bound to Android Keystore. Five failures trigger a persisted 30-second delay. The encrypted copy is completely authenticated before deleting its source. Restores publish a completed MediaStore copy before removing the encrypted one. Transfer failures retain the source.
- Vault locks when leaving; private playback stays inside the app, is protected against screenshots and cleans its private temporary file on exit. Private data resides under noBackupFilesDir, outside MediaStore and FileProvider paths. No new broad storage permissions.
- There is no PIN recovery. Uninstalling, clearing app data or losing this installation's Keystore makes the vault inaccessible. Restore important videos first. This is explained before PIN creation.
- Home theme now changes actual glossy backgrounds, card interiors, text and accents, and refreshes when returning from Settings. Metallic edges and original raster icons are retained.

Installation/signing: from 0.7.1, CI uses a committed PUBLIC DEVELOPMENT signing identity (.github/preview-debug-key.b64, standard android password). This is deliberately public test material, never a production signing key. It allows subsequent preview updates to retain app-private data. Earlier previews used ephemeral runner debug identities and cannot be updated with this key; export important app-private recordings before any uninstall. No automated uninstall or data migration is performed.

Known scope retained: file-based RTMP streaming; call recording uses Shizuku companion; voice capture saves when leaving its screen. Device-specific background camera reliability still needs a physical-device check. Preview release only, no main merge.
