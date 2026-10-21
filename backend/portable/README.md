# Guardian portable service (0.11.0)

Django WSGI + MySQL + an operator-owned SMTP mailbox. No Cognito, DynamoDB, SQS, AWS keys or permanent background worker is required for text alerts. The existing SAM backend remains supported. See `HOSTING_RESEARCH_2026-10-21.md`, `NAMECHEAP_DEPLOYMENT.md` and `INSTITUTION_DEPLOYMENT.md` in the repository root.

**Not deployed.** Configuration, a private MySQL database, valid sender/domain authentication and a working HTTPS origin must be supplied by an authorized operator. There is no default production password or public test endpoint.

- `MANAGED`: Free three remote alert events/month; paid entitlements require actual Play verification; limits remain bounded.
- `INSTITUTION`: a separate deployment/database/secret/mailbox, controlled enrollment and a contractual license expiry configured by the institution operator. A phone cannot select its own paid quota or institutional entitlement. Operator configuration is not a piracy-proof licensing system.
- `ALERT_ONLY`: available with SMTP and no object store. Email contains event metadata/location; video remains local. No cloud-evidence link or upload is claimed.
- `FULL_EVIDENCE`: explicitly enabled private external S3 storage; existing signed segment protocol and checksum verification. Never stores videos on shared web hosting. An event keeps its reserved package mode and retention tier across subsequent configuration or entitlement changes.

## API compatibility

Public `GET /.well-known/nexvary-guardian` returns schema/version, edition and service capabilities (no credentials). Android trusts only the HTTPS origin explicitly confirmed by the guardian. Redirects and addresses with credentials, paths or query strings are rejected. Personal service URL is a public build configuration value; an empty value means not activated. Provider migration keeps this origin, database and token-signing key; it does not require new user settings. Moving to a new authentication system or rotating identity keys requires an explicit session migration/re-login.

Account endpoints: `POST /auth/signup`, `/auth/confirm`, `/auth/login`, `/auth/refresh`, `/auth/forgot`, `/auth/reset`, `/auth/logout`. Passwords use Django hashing/validation; six-digit challenges expire after 10 minutes and five failures; access JWTs last 15 minutes, refresh credentials rotate and are hashed in SQL. Logout, reset and deletion revoke server sessions. APIs use bearer tokens only; browser cookies grant no authority.

Protected event/upload/contact/usage/billing/deletion paths match the AWS backend. Email recipients are verified account addresses or code-confirmed trusted addresses, never arbitrary event-body recipients. Separate institutional deployments avoid cross-institution data sharing.

## Queue and capacity

One bounded initial mail attempt runs during the authenticated request after SQL commits the job. A **single CLI cron at intervals of five minutes or longer** retries, expires challenges and deletes expired/revoked private objects. No public cron URL, daemon, Celery, Redis, ASGI or video relay is required. This is best-effort email; it is not a guaranteed real-time emergency channel. Android retains local SOS/call/SMS/share actions.

Conditional SQL counters enforce per-user, IP, enrollment, daily/monthly, domain-service email and monthly upload-byte budgets. All outbound auth, contact and SOS mail use the same global email budget. Default 100/hour and 1000/day are operator safety budgets, not a claim about available Namecheap quota. Other applications/mailboxes sharing the domain also consume its provider allowance. Retrying uses the same event/package reservation; definitive failures retry with backoff; acceptance ambiguity or a dead sending worker becomes `UNCERTAIN` for review. Neither automatic provider failover nor SDK resend is used after ambiguous email acceptance.

`guardian_capacity` reports aggregate counters, queue age and uncertain deliveries without user data. At 70% of the configured email or upload budget, or queue age above 60 seconds, it reports `REVIEW_EXTERNAL_PROVIDER`. This prompts an operator decision; it never buys/activates a paid service automatically. Actual LVE/CPU/memory/database usage and provider quotas must also be monitored in cPanel. Counts of subscribers alone are not a capacity model.

## Tests

```bash
python -m pip install -r backend/portable/requirements-dev.txt
GUARDIAN_TESTING=1 python -m pytest -q -c backend/portable/pytest.ini backend/portable/tests
GUARDIAN_TESTING=1 python backend/portable/manage.py check
```

SQLite is test-only; the CI MySQL 8 job exercises production migrations and concurrent reservation/refresh locks. MySQL tests use only synthetic CI credentials. The SMTP certificate test runs on loopback with a disposable self-signed certificate and proves no credentials are sent before verified TLS. AWS object-store tests are mocked. Real Namecheap SMTP, live storage, actual Play transactions and institution operations require staged verification before advertising activation.

A guardian-initiated retry may requeue definitively FAILED SOS recipients at most three times/day without a new package reservation. Already accepted or UNCERTAIN recipients are never reset by that action.

SOS jobs have priority over account/contact mail. Non-emergency messages use at most 80% of the configured hourly service budget (minimum one for very small test/operator budgets), leaving normal hourly headroom for emergency alerts; all messages still share the hard provider-service caps.
