# Emergency flow and failure isolation

## Core state

`IDLE → ARMED → COUNTDOWN → SOS_ACTIVE → RESOLVED/CANCELLED`

Manual SOS normally enters a 20-second cancellable countdown; prior explicit Immediate Silent SOS consent may skip it. Check-in enters a configurable 20–600-second grace period after expiry. Overdue recovery starts a fresh prompt rather than silently sending on boot. Safe, Extend and Start SOS are available in the app and notification. Repeated countdown triggers do not restart its deadline. A stale notification UUID cannot resolve a newer session.

The core state is separate from concurrent action states: evidence `USER_ACTION_REQUIRED / STARTING / RECORDING / SAVED / FAILED`, email `QUEUED / SENDING / SENT / RETRY / AUTH / TLS / CONFIG / LIMIT / FAILED / UNCERTAIN / CANCELLED`, and SMS `WAITING_USER / OPENED`. This avoids pretending that evidence, email and future uploads occur in one linear sequence. `UPLOADING` is not reported when no uploader exists.

## Durable dispatch

1. Persist the state transition and event.
2. Construct versioned alert JSON with current/last location distinction, timestamp, timezone, battery, network and user note. Medical/device data require their own opt-ins.
3. In the same encrypted transaction, enqueue one job per opted-in trusted contact and event. No contacts/consent means no automatic sends.
4. WorkManager schedules each email independently with a network constraint and bounded exponential retries. Missing SMTP, authentication or TLS failures are visible without stopping local evidence or another recipient.
5. At claim time re-check consent, contact existence, channel and recipient address. Changes cancel stale queued destinations. Revocation cannot recall an email already in flight or accepted by a server.
6. Server acceptance is `SENT`, not proof of inbox delivery or guardian receipt. Interrupted SMTP DATA is `UNCERTAIN`; automatic duplicate sending is stopped and explicit retry warns of duplication. Stable Message-ID helps diagnostics but does not guarantee exactly-once mail delivery.
7. SMS opens only a reviewed composer. `OPENED` means the composer opened, never that a text was sent.

Silent is the default notification channel (no sound/vibration). Opt-in audible countdown/active alerts use a separate channel, still subject to system notification/channel/DND settings. App button, Quick Settings tile, home widget and optional double Volume Down while this Activity has focus use the same flow. No Power interception or Accessibility/global volume listener exists.

## Location, power and connectivity

Current location requires a fix no older than two minutes; the payload also labels last-known location and age. Same-boot age uses the monotonic fix clock. After reboot the stored wall timestamp remains explicitly last-known. Approximate permission can limit arrival accuracy. A network transition is recorded while the safety service is active; queued emails persist without connectivity and resume through WorkManager. Without location opt-in, the durable alert queue still uses WorkManager network constraints, but no continuous network/battery monitor is started.

Battery thresholds 15/10/5 are deduplicated within a session. Critical saver slows location to five minutes and unregisters motion. Motion signals always use a cancellable countdown even if immediate manual SOS is enabled.

## Android boundaries

Exact alarm permission may be denied/revoked. Inexact work may be delayed in Doze or by OEM restrictions. Force-stop prevents scheduled execution until the app is reopened. Boot restores timer scheduling and queued jobs, not camera/microphone/location capture. Background evidence needs a visible user action to respect while-in-use foreground service rules. Basic SOS is an assistance tool, not a guaranteed emergency communications service.
