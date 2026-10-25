# Camera recording email — 0.12.3

Scope: Guardian camera recording only. NEXVARY-MR is independent. Existing
camera recording, SMTP settings, private attachment queue and Worker are reused;
no parallel delivery engine or cloud service was added. Family/SOS legacy SMTP
production restrictions remain unchanged.

## Owner flow

Open Camera recording, enable automatic email, configure and test your own SMTP
account, then enter 1–10 mailbox addresses separated by commas, semicolons or
newlines. Arabic separators are accepted. Duplicates are removed. Review the
entire recipient list and explicitly confirm before recording starts. If a
guardian PIN is configured its existing gate protects approval/settings/outbox.
Normal recording needs no SMTP account and email defaults to off.

After save, one immutable private copy is queued for all recipients. The local
original remains in the recording library. The network-constrained Worker may
wait for Android background execution/network availability. Outbox explains
configuration, authentication, TLS, size and uncertain-result failures and lets
the user correct recipients before manual retry. Acceptance by an SMTP server
does not prove inbox delivery. Large files remain local when message limits
would be exceeded; files are never silently split or sent to an upload service.

## Security and migration

MailRecipients permits bounded plain mailbox addresses only (4096 input
characters, maximum10 distinct recipients). Header/display-name syntax and
control-character injection are rejected. One recipient uses the existing To
header; multiple recipients use an explicit SMTP envelope, Bcc and an
undisclosed-recipients To header. Angus omits Bcc from transmitted headers.
The configured SMTP operator necessarily sees the envelope addresses.

`mail.smtp.sendpartial=false` aborts before DATA if any RCPT is rejected.
`mail.smtp.reportsuccess=false` avoids success-report exceptions being mistaken
for a failure. Mandatory TLS1.2/1.3, trust and hostname validation remain on;
there is no plaintext fallback, protocol tracing or general-purpose send IPC.
Activities/services remain unexported. No new permissions were added.

Last-used addresses share the existing Android Keystore AES-GCM SMTP profile;
the old plaintext camera recipient preference is removed after a successful
confirmed save. Private queue records retain addresses locally for review.
No credentials, addresses or attachment bytes enter WorkManager input/logs.
Old jobs lacking the new persisted approval flag cannot be claimed. Pending
ones become Review; interrupted sends become Uncertain. Neither auto-sends on
upgrade. Manual retry presents editable recipients and explicitly approves the
job. Uncertain DATA results still require manual acknowledgement, since SMTP
cannot guarantee exactly-once delivery.

## Release regression gate

The previous production build hid email UI and returned failure immediately in
MailWorker despite compiling these features in Debug. Both guards are removed.
The normal release AAB is unsigned for original-owner-key signing.

An independent workflow builds Release with DEBUG=false and a public fixture
certificate solely to permit instrumentation. It runs real TLS/STARTTLS on
127.0.0.1, privacy/migration/PIN/cancel cases, an actual scheduled Worker and a
timed Camera2 MP4 through queue/local TLS. Its APK is never an owner update.
Fixture trust material is injected only by tests; production trusts Android.
Real Arabic screenshots are collected only after successful Release tests.

Primary SMTP semantics reference:
https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html

Physical OPPO/ColorOS, external owner SMTP and actual inbox delivery are Pending.
Tests do not send to external recipients. See TEST_RESULTS_0.12.3.md for actual
accepted runs; this design document alone is not a pass claim.
