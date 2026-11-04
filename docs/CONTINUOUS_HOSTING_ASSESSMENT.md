# Hosting decision for continuous recording — 5 November 2026 (Cairo)

## Verified boundary

Repository, PR #10, latest recipient-card branch and existing portable/AWS backends were inspected. The connected desktop was offline; the available cloud browser had no authenticated tabs. No cPanel account, active plan, Python selector, free disk/inode/LVE capacity, SMTP account quota, PHP/proxy request limit, database or DNS was inspected. A production capacity assessment is therefore **pending account access**, not passed. No server, DNS record, bucket, mailbox, database or paid resource was created. `api.nexvary.com` remains a proposal, not a deployed endpoint.

## Decision

Use local segmented recording plus the existing user-configured TLS SMTP transport initially. No hosting change is needed for this mode. The existing optional portable backend stays disabled until actual hosting capability and provider suitability are verified. Do not proxy or store continuous video on the shared website account. Keep company website, Laravel files, production database and domain DNS untouched.

For future cloud video, use authenticated direct-to-private-object-storage uploads, server-side checksum/size confirmation, per-segment idempotency, separate upload/submission/access states, bounded signed viewer links and verified retention. Existing Guardian backend implements a bounded event package, not this unlimited session model; its quotas/notification lifecycle must be redesigned before continuous integration. No new migration is required for the direct SMTP implementation.

## Official policy checks (retrieved during this work)

- Namecheap shared AUP disallows standalone daemons, cron intervals below five minutes and public file-exchange services; its multimedia disk allowance is not permission to operate an evidence-storage service. URL: https://www.namecheap.com/legal/hosting/aup/
- Namecheap's space guide says heavy media/file-storage use is inappropriate for shared servers: https://www.namecheap.com/support/knowledgebase/article.aspx/9541/2194/how-to-optimize-hosting-account-space/
- Published domain hourly mail limits include Stellar 50, Stellar Plus 200 and Stellar Business 10000. The actual plan and other domain traffic were not verified. Individual provider throttling or policy decisions can still delay or reject messages. URL: https://www.namecheap.com/support/knowledgebase/article.aspx/133/22/do-you-have-any-restrictions-on-sending-out-emails/
- Setup Python App/Passenger and the deployed MySQL driver's availability must be checked in the actual account. Existing deployment instructions: `NAMECHEAP_DEPLOYMENT.md`.

A one-minute clip implies about 60 messages/hour/session; at 30 seconds it is about 120. Continuous sessions from several users can exhaust a shared domain's quota quickly. Base64 expansion and message-size limits also matter: at Compact quality a one-minute clip is approximately 2.4–2.7 MB before MIME, while High quality can exceed a 25 MiB message budget. The UI estimates each chosen segment; actual size remains encoder-dependent. SMTP queues/provider limits prevent a delivery-time guarantee.

## Indicative alternatives (USD; no purchase authorized)

| Option | Monthly baseline | Storage / transfer | Workers and backups | Scaling / operational cost |
| --- | --- | --- | --- | --- |
| Existing cPanel | No additional hosting purchase for direct SMTP; existing plan cost not verified | Actual remaining quota unknown; unsuitable for continuous private video hosting | Passenger metadata API and bounded SQL/cron only, subject to host approval; existing backup eligibility must be checked | Lowest initial administration; limited process/resource control |
| Lightsail Linux public IPv4, 1 GB | $7 | 40 GB SSD, published 2 TB transfer allowance (region-dependent) | Persistent worker can run under supervisor; snapshots/backup add cost | Simple predictable test server, modest memory; production sizing requires load testing |
| Lightsail Linux public IPv4, 2 GB | $12 | 60 GB SSD, published 3 TB transfer allowance (region-dependent) | More worker/DB headroom; snapshot/backup separate | Preferable small VPS candidate if cloud service becomes necessary; one instance is still a single failure domain |
| EC2 Linux t3.micro example | ~$12.84 for 730h compute + one IPv4 + 20 GB gp3, before extras | EBS selected separately; 100 GB/month internet egress allowance aggregated across eligible AWS services/regions, then region pricing | Full worker/service control; EBS snapshots and managed backups priced separately | Most flexible but more components/monitoring; CPU-credit, egress, logs and backup charges must be budgeted |

EC2 example arithmetic: $0.0104 × 730 = $7.592 compute; $0.005 × 730 = $3.65 IPv4; $0.08 × 20 = $1.60 gp3; total $12.842. This uses published example rates, not a quote for every region. Excludes taxes, CPU credit surcharges, email provider, S3, monitoring, snapshots, domain and load balancer. A 1 GB machine is an estimate for evaluation, not proven production capacity.

Official pricing references:
- https://aws.amazon.com/lightsail/pricing/
- https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-bundles.html
- https://aws.amazon.com/ec2/instance-types/t3/
- https://aws.amazon.com/vpc/pricing/
- https://aws.amazon.com/ebs/pricing/
- https://aws.amazon.com/ec2/pricing/on-demand/

## Deployment readiness and rollback

Use `NAMECHEAP_DEPLOYMENT.md` for an isolated metadata-only WSGI/MySQL deployment if selected later; do not execute migrations against the website database. Verify plan/LVE usage, policy allowance, Python/driver versions, HTTPS and SMTP limits first. Use a private root outside public_html, separate least-privilege database/user, no plaintext secret in git/APK, disabled storage by default, staged mail to controlled recipients, and a tested restore.

Before activation take a private database/config backup, record app and schema version, test health/auth/quota/expired links/revocation, and establish a mail-queue owner. To roll back disable new reservations/sending, stop the isolated cron/app, retain evidence and queue state, restore only the isolated database/config as needed, and revert only the new subdomain routing. Never restore or replace the company's website/database as part of this app's rollback. Cutover to VPS must have one active sender, checksum-confirmed object retention, private storage encryption and a rehearsed backup/restore. No runbook is evidence of a completed production deployment.
