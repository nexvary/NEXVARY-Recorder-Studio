# Namecheap/cPanel deployment — portable Guardian 0.11.0

Prepared, **not deployed**. Use a dedicated HTTPS subdomain/app and database. Do not overwrite the existing nexvary.com website or Laravel application. A proposed `guardian.nexvary.com` address is not a claim that a DNS record/service already exists. DNS changes need the owner's authorization.

1. Verify current shared plan, residual domain email quota and whether Namecheap permits the actual application use. This package is a modest account/metadata API, not an evidence-file host. Start with SMTP text alerts and local-only video.
2. Extract the backend bundle into a private home folder, e.g. `/home/CPANEL_USER/guardian/`. In cPanel **Setup Python App**, select an available supported Python (3.10+), Application root `guardian/backend/portable`, startup file `passenger_wsgi.py`, entry `application`, and the dedicated HTTPS application origin. Choose `requirements-mysql.txt` and install dependencies (Django plus the production MySQL driver). It requires the host's available MySQL client libraries; verify installation in this cPanel account. Do not start a standalone daemon or use Django runserver publicly.
3. Create a separate MySQL database and least-privilege user through cPanel; use migrations with a controlled schema-capable user, then reduce privileges as appropriate. Test the production MySQL engine rather than copying a SQLite file. No production database password is included.
4. Activate the virtual environment command supplied by cPanel. Create private settings **outside public_html**:

```bash
python init_config.py /home/CPANEL_USER/guardian-private/settings.json --service-url https://guardian.YOUR_DOMAIN
```

The command creates a fresh 0600 file and random signing secret without printing it; it refuses to overwrite an existing file. Complete the JSON privately: `GUARDIAN_DB_NAME`, `GUARDIAN_DB_USER`, `GUARDIAN_DB_PASSWORD`; SMTP host, username, password and authenticated sender. Set `GUARDIAN_EMAIL_PROVIDER=SMTP` only when valid. Port 587 uses mandatory STARTTLS, port 465 uses direct TLS. Certificate and hostname verification cannot be disabled.

5. Add one cPanel environment variable `GUARDIAN_CONFIG_FILE` with the absolute private JSON path. Add `GUARDIAN_DOCUMENT_ROOT` if the document root has a nonstandard name. The same loader works from cron. Never upload the actual JSON/SMTP secret/Play service key through Git or public File Manager paths. Never set `GUARDIAN_TESTING=1`; Passenger refuses it.
6. Run with that configuration selected:

```bash
GUARDIAN_CONFIG_FILE=/home/CPANEL_USER/guardian-private/settings.json python manage.py migrate
GUARDIAN_CONFIG_FILE=/home/CPANEL_USER/guardian-private/settings.json python manage.py check
GUARDIAN_CONFIG_FILE=/home/CPANEL_USER/guardian-private/settings.json python manage.py guardian_capacity
```

7. Set **one CLI cron** at `*/5 * * * *` or a longer interval. Use absolute paths to the cPanel virtualenv Python, `manage.py` and private config; e.g. `GUARDIAN_CONFIG_FILE=/home/CPANEL_USER/guardian-private/settings.json /home/CPANEL_USER/virtualenv/guardian/backend/portable/VERSION/bin/python /home/CPANEL_USER/guardian/backend/portable/manage.py guardian_maintenance`. Verify the actual generated venv path. Keep output private; no public cron trigger exists. This retries/cleans a bounded batch, so delayed SMTP is possible and must not be advertised as a guaranteed emergency channel.
8. Verify `GET /.well-known/nexvary-guardian`, email confirmation/login/rotation/logout, a controlled TEST ALERT, quota/cooldown, provider limits, cancellation and deletion. Authenticate only controlled addresses during staging. Domain email deliverability requires the mailbox provider's actual SPF/DKIM/DMARC setup; do not invent DNS values or bypass TLS. Existing `AWS_SES_SETUP.md` applies only if SES is chosen later.
9. After the service is deployed and verified, configure the public Android build variable `GUARDIAN_PUBLIC_API_URL` (or Gradle property `guardianServiceUrl`) with the stable HTTPS origin. Until then it stays empty and the app clearly reports the personal service as not activated. No guessed production endpoint is embedded.

## Optional cloud video

Leave `GUARDIAN_STORAGE_PROVIDER=DISABLED` initially: alerts may send metadata/location; video stays on the phone. This consumes the same bounded monthly remote-alert quota, not a video-upload claim. For full cloud packages configure a private external S3 bucket, authenticated TLS, encryption/lifecycle and scoped server-only credentials. Android uploads directly with signed URLs; shared hosting verifies S3's stored checksum via HEAD and never relays video bytes. No local-video upload endpoint exists.

`GUARDIAN_STORAGE_VERIFIED` defaults to false even if a bucket name is provided. Run `python manage.py guardian_storage_check` for read-only AWS S3 public-access/encryption checks. Set `GUARDIAN_STORAGE_VERIFIED=1` only after all staged storage tests pass; the command does not activate storage or change configuration.

Required operator verification: bucket Block Public Access, private bucket policy/ACL, encryption, lifecycle, signed PUT with enforced checksum/size, HEAD checksum support, expired link, account/event deletion, and cost alarms. S3-compatible systems that do not return a validated checksum must fail completion. The separate AWS SAM template provides a reference private-bucket setup; enabling it provisions resources and is a distinct deployment decision. Wasabi is disabled in the shared-host profile and must be tested on an appropriate institution/VPS deployment.

## Monitoring and transition

Default application budgets: 100 email recipient attempts/hour, 1000/day, 50 new accounts/day, 3 GiB reserved cloud-upload bytes/month when storage is enabled. Adjust to **remaining** plan capacity, including other domain users. `guardian_capacity` raises a review signal at 70% or old queue entries; it does not activate a billable provider. Monitor cPanel LVE/SQL/error rates separately.

Move the same MySQL database, private configuration/signing key and HTTPS origin to a suitable service when actual load warrants it. Test backups/restores and have only one active mail queue during cutover. A provider switch does not change phone settings; an identity-server change may require re-login. Do not promise a transparent live AWS/Cognito identity migration; a migration runbook/data transformation is required for that separate change.
