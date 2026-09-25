# NEXVARY Recorder Studio

Android application prototype focused on four workflows:

1. **Screen Recorder** — MediaProjection + H.264 MP4, optional microphone audio.
2. **Professional Voice Studio** — 48 kHz PCM voice capture. When the device supports them, Android NoiseSuppressor, AutomaticGainControl and AcousticEchoCanceler are enabled. A local speech-oriented post-processing chain then applies DC/high-pass cleanup, a gentle noise gate, soft compression and peak normalization. Output is WAV.
3. **Replace Video Audio** — select a video and a replacement WAV/M4A/AAC file. WAV is encoded locally to AAC with MediaCodec; the original video track and new audio track are muxed to MP4 with MediaMuxer. No cloud upload is used.
4. **Prerecorded-to-Live RTMPS** — select a local video file and stream it as a real RTMP/RTMPS live session. The current implementation uses RootEncoder 2.8.1 and its FromFile pipeline. A persistent foreground service keeps the live broadcast running while the app is backgrounded.

## Facebook Live workflow

Open Facebook Live Producer for the Page/profile/account that is eligible to use streaming software, obtain the **Server URL** and **Stream Key**, then enter both in the Live section. The application joins them and opens an RTMPS session. For maximum interoperability, use MP4 containing H.264 video and AAC audio.

A prerecorded file is the source of the live transport. Facebook can therefore present the session as Live even though the source media was recorded earlier. For broadcasts where provenance matters, disclose that the source is prerecorded.

## Privacy

The recording, audio enhancement, WAV-to-AAC conversion and audio replacement operations are local to the Android device. The only network operation in the current source is the RTMP/RTMPS broadcast selected by the user.

## Android target

- minSdk: 29 (Android 10)
- targetSdk: 35 (Android 15)
- Kotlin/JVM 17
- RootEncoder 2.8.1

## Build

The project includes a GitHub Actions workflow. After pushing to GitHub, run **Android Build** and download the `NEXVARY-Recorder-Studio-debug` artifact.

Local build with a suitable Android SDK and Gradle 9.7.1:

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Current scope / known limitation

The screen recorder in this first package records the microphone with the screen. Capturing Android **internal playback audio** and mixing it simultaneously with the microphone requires the AudioPlaybackCapture + MediaCodec screen pipeline rather than MediaRecorder and is intentionally not presented as complete in this package. The dedicated voice studio and audio-replacement workflow are implemented.

## Third-party component

RootEncoder by Pedro Sánchez is used for RTMP/RTMPS streaming from a file. RootEncoder is licensed under Apache-2.0. See `THIRD_PARTY_NOTICES.md`.
