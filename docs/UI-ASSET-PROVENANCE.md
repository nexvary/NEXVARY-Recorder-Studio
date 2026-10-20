# Recorder Studio 0.7 visual assets

The ten independent PNG icons in `app/src/main/res/drawable-nodpi/` are original AI-generated assets created for this application with the built-in image generation tool on 2026-10-20. No third-party icon pack or runtime URL is used. Each home destination consumes its own image. Raster output was inspected and resized with alpha preserved for Android packaging.

Prompt specification: single isolated glossy realistic 3D object, frontal view, polished metal/glass, controlled silver reflections, square transparent canvas, clear silhouette at 56dp, 15% padding, no scene, watermark, unrelated text or sprite sheet.

Subjects: blue compact camera (screen); silver camera lens with blue optics (camera); neon green studio microphone (audio); golden media folder (tools); cyan broadcast tower/waves (live); red SOS button; purple media document/play badge (library); silver metal gear; green information badge; silver/blue letter N with integrated blue lens on piano-black rounded square (launcher).

`launcher_foreground.png` places the launcher artwork within the adaptive icon safe region. Android's monochrome themed-icon layer intentionally remains a system-compatible silhouette. Notification/status-bar icons also remain monochrome as required by Android; these are not substitutes for the full-color in-app assets.

## Behavior and verification

Home: nine real routes, responsive scroll content, fixed bottom navigation and independent touch targets. RTMP tile explicitly describes file broadcasting. Library reads app-owned MediaStore recordings and private camera files; FileProvider exposes only the Movies subdirectory.

SOS: local contact CRUD, phone validation including Arabic digits and short emergency numbers, per-contact optional image/location, recent monotonic location checks, explicit review and user-controlled SMS/share composer. No SEND_SMS permission or automatic sending. Test fixtures are cleared; screenshots show the actual empty state.

The emulator gate runs API 35 with 2 CPU cores/2GB RAM, captures actual Arabic screens through instrumentation, retains Logcat/last ANR/memory and test reports, rejects app/launcher ANRs, and uploads screenshots only after tests pass. Physical-device background recording remains a separate requirement.
