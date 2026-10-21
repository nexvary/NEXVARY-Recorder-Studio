Recorder Studio 0.7.0 Android UI preview (min Android 10, target Android 15).

- Original independent full-color 3D raster assets and adaptive N/camera launcher icon.
- Nine connected home cards, glossy black surfaces, silver metallic borders, Arabic RTL and scroll-safe navigation.
- SOS contact CRUD, optional per-contact image/recent location, explicit review and external message composer. No automatic SMS sending.
- Recordings library, settings entry and back controls for camera/call/evidence screens.
- Capture lifecycle fixes: cancel countdown on leaving before screen recording starts; save foreground-only voice recording on leaving.

Published only after static checks, Android Lint, JVM unit tests and Android 15 Espresso navigation tests pass. Attached PNGs are actual Arabic emulator screenshots, not design mockups.

Limitations: this is a debug-signed test APK. Real-device background camera reliability still needs hardware validation. Android 15 QPR1+ stops MediaProjection screen recording when the screen locks (Android platform behavior). Live broadcasting currently streams a selected video file. Call recording requires the separate ShizuCallRecorder companion and Shizuku. The voice studio saves when leaving its screen; it does not provide a microphone foreground service. SOS image sharing requires choosing the recipient in the selected sharing app. New labels outside Arabic use English fallback.
