# Google Play release review — 21 October 2026

This document is a release checklist, not a statement of Google approval. targetSdk 35 / minSdk 29 / package `com.nexvary.recorder` preserved. Review the current target API submission deadline before release.

Camera/microphone/location foreground services start from a visible user action and show persistent notifications. Do not promise silent/hidden system recording: Android privacy indicators remain. Android 14+ enforces while-in-use permission and service-type restrictions; Android 15 adds background-start restrictions. Screen projection needs system consent and can stop on screen lock. Camera background/screen-off, RTMP, ColorOS power management, process kill and full storage need real phone testing.

Use runtime permissions only for selected functions. No SEND_SMS/CALL_PHONE: open the SMS composer/dialer and let the user confirm. Review Foreground Service declarations and video evidence for Play Console. Foreground location used during a journey may still be reviewed as background-equivalent collection; check the actual behavior and disclosures.

Data Safety must match account email, trusted contacts, precise/approximate location where enabled, video/audio/photos, medical profile where consented, usage/event metadata, tokens and billing processing. Document cloud recipient/provider access, encryption, retention/deletion, user choice and why emergency processing occurs. Medical profile remains local unless separately consented; current cloud payload intentionally minimizes fields.

Account creation requires both an in-app deletion flow (implemented) and a functional externally hosted deletion request page (not deployed). Account deletion does not cancel Play subscriptions; the UI says so. Do not release until that webpage, privacy URL, refund/subscription messaging, store product catalog, production signer and Play checks pass. The AAB is an unsigned release candidate, not a Play-ready signed release.

References:
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/changes
- https://support.google.com/googleplay/android-developer/answer/9799150
- https://support.google.com/googleplay/android-developer/answer/13327111
