# Hosting and operating-model research — 21 October 2026

## Decision

Use existing shared hosting for a small WSGI account/metadata API, SQL outbox and bounded SMTP. Start in clearly declared **text alert + location** mode, retaining video locally. Enable private external object storage only after an operator chooses and verifies it; cloud video cannot be promised on the shared-host-only profile. Keep the existing AWS serverless implementation for later scaling. Provide separate institution-owned deployments/mailboxes with controlled enrollment.

Prior project context identifies nexvary.com on Stellar Plus. The active account/plan and its current available headroom were not inspected in this session. No purchase, DNS change, account login or production deployment was performed.

## Official sources and findings

1. Namecheap Python App: shared hosting supports WSGI through cPanel Setup Python App; ASGI requires VPS/Dedicated. A WSGI `application` entry point and private environment configuration are documented. We implement `passenger_wsgi.py`, with no standalone app server.
   https://www.namecheap.com/support/knowledgebase/article.aspx/10048/2182/how-to-work-with-python-app/
2. Namecheap email restrictions: the published per-domain hourly limits distinguish Stellar (50), Stellar Plus (200), Stellar Business (10000), Private Email and other products. These figures are not remaining capacity for this application; other senders on the same domain count. We use a configurable conservative service budget, default 100/hour, and a shared budget across every email type. Verify the actual plan and mailbox before activation.
   https://www.namecheap.com/support/knowledgebase/article.aspx/133/22/do-you-have-any-restrictions-on-sending-out-emails/
3. Namecheap AUP: shared hosting prohibits standalone daemons, cron intervals below five minutes, and file-exchange/storage uses; multimedia allowance does not authorize an evidence-storage service. Therefore no local video backend, continuous worker or public file-sharing endpoint is added. A private external store is necessary for cloud video. Hosting suitability remains subject to Namecheap accepting this application’s actual use and resource load.
   https://www.namecheap.com/legal/hosting/aup/
   https://www.namecheap.com/support/knowledgebase/article.aspx/9792/22/unmetered-hosting-policy-explained/
4. Namecheap resource limits also cover memory/processes/CPU; exceeding them can yield 500/503 responses. Queue age and sending rate are useful app signals but do not replace cPanel LVE monitoring.
   https://www.namecheap.com/support/knowledgebase/article.aspx/1127/103/a-handy-guide-to-resource-limits-or-what-is-lve/
5. Wasabi PAYG publishes a 1 TB minimum monthly storage charge and 90-day minimum billable retention with charges for early deletion. It should not be assumed cheaper for a small service retaining evidence for two/seven days. Wasabi remains an optional institution/VPS adapter, not an automatic early paid dependency. No price or production capacity is guaranteed by this document.
   https://wasabi.com/pricing/faq
6. Django’s deployment guidance supports WSGI and requires secret production settings, HTTPS and an actual production server. We use Django’s password validation/hashers, MySQL transactions, strict host validation, private configuration and bearer-only API authorization.
   https://docs.djangoproject.com/en/5.2/howto/deployment/wsgi/
   https://docs.djangoproject.com/en/5.2/howto/deployment/checklist/

## Expansion triggers

Monitor messages/recipient attempts, account verification traffic, queue age, SQL/LVE load, upload reservations and retention/deletion backlog. Review providers before 70% of the configured service budget, or if requests/delivery start queuing. Preserve a fixed service HTTPS origin and identity database during migration; change provider configuration server-side. Back up and rehearse the MySQL migration, maintain signing keys securely and avoid two active senders claiming the same queue.

Email provider changes and external storage activation are operator actions with explicit credentials/budgets. Subscription revenue can finance that decision; reaching a subscriber count does not itself authorize a purchase or guarantee deliverability.
