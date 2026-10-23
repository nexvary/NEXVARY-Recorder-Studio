# Abuse and cost protection

- API Gateway Cognito JWT auth; verified email. Client recipient arrays are ignored.
- Atomic event UUID, monthly/daily quota and 10-minute cooldown transaction.
- Per-account 120 API requests/minute; hashed IP 600/minute; API Gateway throttling and limited Lambda concurrency.
- Cognito-scoped identity across devices prevents an ordinary second device resetting account quotas. A spoof-resistant per-device attestation mechanism (Play Integrity) is not integrated yet; do not describe the account limit as full device attestation.
- Free one cloud guardian (verified account email). Premium additional guardians require expiring code confirmation; max 5 total; invitation cap 5/day. Child UI has no invitation/account/cloud/Premium configuration.
- 12 MiB segments, 120 MiB event, 24 segments. Presigned PUT signs length/type/checksum; backend re-hashes uploaded content.
- 3/day Free, 10/day Premium; Premium 100 monthly fair-use reservations. Local SOS is independent.
- FIFO queue, per-recipient acceptance receipts, no automatic resend after ambiguous SES acceptance.
- Private bucket, TLS deny policy, lifecycle, queue-age/DLQ alarms, budget notifications, log retention. Budgets are alerts, not a spend ceiling.

Production follow-up: verify SES bounce/complaint processing and suppression, wire account health monitoring, test queue alarms, add device attestation if needed, apply Cognito auth abuse protections, review service quotas/region pricing. A failed provider never deletes the local emergency event or prevents local capture.
