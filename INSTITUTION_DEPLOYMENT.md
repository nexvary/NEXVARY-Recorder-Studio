# Institution edition — school, organization or enterprise

Each institution operates a separate deployment, MySQL database, signing secret, verified sender/mailbox and (if needed) private object storage. Android uses its confirmed HTTPS service address; no SMTP/storage password is shipped in the APK. The institution owns operating costs and chooses its recipients and enrollment policy.

Use the portable package instructions in `NAMECHEAP_DEPLOYMENT.md`. For a suitable institution-owned VPS, use a production WSGI server behind HTTPS, MySQL backups and scheduled maintenance. Shared-host restrictions still apply if the institution chooses shared hosting.

- Generate its private configuration using `init_config.py ... --edition INSTITUTION`.
- Set `GUARDIAN_SERVICE_NAME`, e.g. a school safety service name.
- Set `GUARDIAN_ENROLLMENT_DOMAINS` to a comma-separated allowlist of institution domains **or individual parent email addresses**. Without an allowlist, institution signup is denied; this is not public registration.
- Set `GUARDIAN_INSTITUTION_EXPIRES_AT` to the contractual UTC Unix expiry. Only the trusted server operator configures it. The app cannot elevate itself or reset quotas. Expiry removes advanced institutional entitlement without blocking local SOS/evidence. This operator-configured contract is not an anti-piracy DRM or an issued commercial license; business contract/licensing activation remains an operational step.
- Configure institution-owned SMTP credentials in private server settings. All account-code, contact and SOS messages share its budget. Keep sender authentication and verified recipient consent; never use a school server as an open relay.
- Start with `ALERT_ONLY` if it supplies only email. Choose and verify private external storage before enabling cloud video. Changing delivery capability never retroactively turns an alert-only reservation into an uploaded-video event.
- The school/institution operates the service endpoint; a guardian explicitly confirms its address and chooses what to share. This does not provide covert camera/location access, a central surveillance console or authority to bypass guardian controls. Tenant administration dashboards/fleet provisioning are not claimed implemented by this package.

Institutional accounts do not show a personal subscription purchase path. Managed individuals use real Play products and server verification only after an operator enables/configures Play; no fake Premium entitlement or hardcoded displayed price is used. A shared-host deployment can verify a Play purchase with a restricted service-account file outside the web root, without AWS Secrets Manager. Keep actual credentials and receipt binding server-side.

Before selling an activated deployment, exercise its own HTTPS/account/mailbox/storage path with TEST ALERT and synthetic evidence, verify deletion and recipient consent, rehearse restore, and identify its support operator. This source package and automated tests do not establish live readiness of a real institution's infrastructure.

Set `GUARDIAN_PLAY_BILLING_ENABLED=1` only after real staged purchase/restore/refund verification for a managed deployment. Institution mode keeps personal Play purchase paths disabled.
