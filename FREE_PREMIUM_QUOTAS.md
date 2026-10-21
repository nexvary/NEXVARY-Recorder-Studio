# Remote quotas and operating profiles (0.11.0)

Local SOS and local recording are never limited by cloud counters. Sensitive quotas are enforced using the server UTC clock, not phone preferences.

## Portable managed/institution deployment

- Free: three **remote alert events** per UTC month, at most three/day. A text alert includes permitted metadata/location; local evidence remains available.
- An `ALERT_ONLY` reservation sends no video, exposes `uploadEvidence=false`, and has no cloud evidence link. Video-upload remaining count is zero while storage is disabled/unverified. This is explicitly different from a full remote video package.
- A `FULL_EVIDENCE` reservation requires verified private external object storage. Segments/retries share one reservation and count; monthly allowance remains shared with remote alerts already sent that month. Merely pressing local SOS consumes no remote quota unless consented remote processing actually reserves an event.
- Cooldown: ten minutes. Repeated remote reservations merge into the active event. Clients cannot bypass cooldown by sending an `override` flag. Local call/SMS/share and recording remain available.
- Premium/active institution contract: bounded fair use, initially 100 remote events/month and ten/day; not technically unlimited. Personal paid state requires real server-side Play verification. Institution entitlement is a trusted server-operator contract configuration; no phone-selected Premium mode.
- Max segment: 12 MiB; max event: 120 MiB; up to 24 segments. Server fixes package type and free/premium retention tier at reservation.
- Default cloud retention: two days Free, seven days Premium. Local files are not deleted by server cleanup. Viewer links last at most 15 minutes; S3 links expire separately and may remain valid until expiration or actual object removal after a deletion request.

Global service budgets cover **all** email recipient attempts (verification, password reset, contact consent and SOS), not just events. Default 100/hour, 1000/day, 50 new accounts/day and 3 GiB/month reserved upload bytes are operator budgets within verified hosting headroom, not promises about the provider. Reserve upload budget before signing; deletion/retry does not refund it for abuse protection.

A first bounded attempt runs after commit, with five-minute-or-longer CLI cron fallback on shared hosting. Email delay is possible. Definitive rejection can retry; acceptance ambiguity becomes UNCERTAIN and is not silently resent or failed over. Local safety actions remain available.

Account deletion revokes sessions/views immediately and erases private account/contact/payload data. Private object removal is queued for bounded maintenance; retention lifecycle is an independent backstop. Operators must monitor cleanup backlog. Auth/mail metadata expires after its short purpose plus seven days; purged event tombstones after 60 days. Minimal account/purchase-ownership security tombstones are retained without the user's original email.

## Existing AWS SAM deployment

The original AWS implementation retains three full remote SOS/video packages/month Free, atomic DynamoDB counters and FIFO queue, with the same local-safety guarantee. It uses Cognito rather than the portable identity database. It is still source-tested, not deployed; SES production state and actual storage/Play remain unverified. A move between these identity/database systems needs an explicit migration, whereas SMTP/SES/S3 provider changes inside the portable service keep the same endpoint and accounts.
