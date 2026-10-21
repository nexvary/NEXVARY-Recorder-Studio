# Plans and quota semantics

| Feature | Free | Premium |
|---|---|---|
| Local recording, local SOS/evidence | Available; device space permits | Available |
| Trusted local contacts | 1 | Multiple |
| Basic safety check and profile | Available | Available |
| Remote SOS package | 3 per UTC calendar month | 100 fair-use reservations/month |
| Video uploads | Same 3 packages; segments do not consume extra packages | Within event fair-use limits |
| Advanced journey, motion, evidence automation | Premium feature gates | Available after verification |
| Cloud retention | API access up to 48 h; physical S3 expiration typically 48–72 h | 7 days default |

A remote package covers location, progressive evidence and email/cloud link under one event UUID. Both usage meters refer to the **same** remaining packages. They are not independent pools. Local-only SOS does not decrement the backend counter. Server UTC month, daily counter, quota reservation and cooldown are authoritative; handset clock changes do not reset them.

One reservation is consumed when the server accepts the event; a failed upload/email retains that same reservation for safe retry. Active SOS taps add markers to the current session. Server merges different event IDs during a 10-minute cooldown; an authenticated parent override may bypass the cooldown but never monthly/daily/file limits. No quota reset endpoint exists in Android. An override UI is not enabled for child taps.

Daily caps: Free 3, Premium 10 events. Segment cap 12 MiB; event cap 120 MiB; 24 segments; network retries use the original event and index. The 720p H.264 emergency target is 1 Mbps video + 48 kbps AAC: `(1,000,000 + 48,000) × 60 / 8 = 7,860,000 bytes/min`, plus container overhead (about 7.93 MB/min with the app allowance). Actual codec/device output must be measured. Emergency MP4 segments use a 30-second native duration limit or 12 MiB safety cap. Camera restart gaps and actual durations require physical measurement.

An encrypted snapshot shows remaining quota and reset date before the next send; it is explicitly cached. Offline/local evidence and dial/SMS/manual location sharing remain available when remote quota is exhausted. No paywall blocks the emergency screen.

Subscription target prices: $2.99/month and $24.99/year, configured in Play Console. Android displays actual localized ProductDetails prices. No AWS account/Play catalog is provisioned by this commit. Both debug and production use verified entitlement. Synthetic instrumentation fixtures seed test-only entitlements. No local Premium toggle exists.
