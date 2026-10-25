# Guardian0.12.4 recipient editor — checkpoint

Source branch: fix/guardian-recipient-picker, parent98ee78f974cd513f98ffb4c4d10afdfcca46b09c.
Source UI, page/help, dependency provenance and whitespace gates PASS. Gradle,
unit tests, APIs33/34/35 and dedicated Release acceptance PENDING.

New tests exercise actual Add and IME input, invalid/duplicate rejection,
close-icon touch removal, rotation/draft retention, capacity10 and re-adding
after removal, and editing/cancelling an existing outbox job without changing
its recipients or local original. Existing SMTP TLS/privacy/approval/camera
acceptance remains; Release runs include the recipient editor tests.

Original-key update and actual Arabic Release screenshots PENDING. Physical
OPPO/ColorOS, Android16 and real provider/inbox delivery remain Pending.
