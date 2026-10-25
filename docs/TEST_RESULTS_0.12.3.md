# Guardian 0.12.3 — validation checkpoint

Branch: feat/camera-multi-recipient-mail, parent9bccfa1060696142023fd3164e58f84bfd7e05b5.
Local source gates passed: UI release gate, page/help review, NPSE dependency
provenance and git diff whitespace check. Gradle/Android runtime gates Pending
until accepted GitHub Actions results are recorded here. No owner update APK
has yet been signed for this source.

New coverage: bounded recipient parsing/injection/duplicates, actual TLS and
STARTTLS two-recipient envelopes, no recipient disclosure in MIME headers,
all-or-none rejection before DATA, encrypted defaults, legacy queue approval,
PIN/cancel behavior and independent DEBUG=false Worker/camera acceptance.

Physical OPPO, Android16, external SMTP and inbox receipt remain Pending.
