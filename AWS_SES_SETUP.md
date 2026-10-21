# Amazon SES setup — nexvary.com

**Current status: NOT CHECKED against the AWS account.** No AWS session is connected; no domain identity, mailbox, DNS record or production access was created. `alerts@nexvary.com` is the intended sender, not a claim that the address exists. Set up a monitored mailbox/replies separately.

1. Choose one SES region matching the backend; SES identity and Sandbox approval are regional.
2. In SES > Verified identities create domain identity `nexvary.com`, enable Easy DKIM and copy the **actual three generated CNAME tokens**. Never invent tokens.
3. Set a custom MAIL FROM domain such as `bounce.nexvary.com`.
4. Review the following records with the DNS operator. **Do not change DNS automatically.**

| Type | Name | Value |
|---|---|---|
| CNAME ×3 | `ACTUAL_TOKEN._domainkey.nexvary.com` | `ACTUAL_TOKEN.dkim.amazonses.com` (copy exact regional SES outputs) |
| MX | `bounce.nexvary.com` | Priority `10`, `feedback-smtp.REGION.amazonses.com` |
| TXT | `bounce.nexvary.com` | `v=spf1 include:amazonses.com ~all` |
| TXT | `_dmarc.nexvary.com` | Start `v=DMARC1; p=none; rua=mailto:dmarc@nexvary.com; adkim=r; aspf=r` |

Create/monitor the DMARC report mailbox first. Review existing root SPF/DMARC records: **one SPF TXT policy per name**, merge authorized senders if editing an existing record. Root SPF may belong to the existing mailbox provider and should not be overwritten. DKIM alignment can satisfy DMARC; custom MAIL FROM enables SPF alignment as well. After reviewing reports, move DMARC gradually to quarantine/reject with the domain owner. DNS propagation and SES verification must be observed, not assumed.

## Sandbox and production access

Run `aws sesv2 get-account --region REGION` and `aws sesv2 get-email-identity --email-identity nexvary.com --region REGION`. Check `ProductionAccessEnabled`, `SendingEnabled`, `SendQuota` and DKIM status. Sandbox permits only verified recipients/domains or SES mailbox simulator; domain verification alone does not lift it.

Request production access in SES > Account dashboard > Request production access. Explain transactional, opt-in family safety alerts; verified guardians; 3 Free packages/month; daily limits/cooldown; no purchased lists; bounce/complaint handling and deletion/retention. Include nexvary.com privacy and service pages. Approval is AWS's decision. Enable/monitor suppression, reputation, bounce/complaint notifications and account sending limits. The current code must not be treated as proof of production access.

Messages: emergency subject `NEXVARY GUARDIAN — EMERGENCY ALERT`; tests `TEST ALERT — NO EMERGENCY`; no large attachments. SES worker uses IAM `ses:SendEmail` limited to `nexvary.com`. SES SMTP usernames/passwords are unnecessary. Production Android workers do not send via SMTP.

Official references (checked 21 October 2026):
- https://docs.aws.amazon.com/ses/latest/dg/creating-identities.html
- https://docs.aws.amazon.com/ses/latest/dg/mail-from.html
- https://docs.aws.amazon.com/ses/latest/dg/send-email-authentication-dmarc.html
- https://docs.aws.amazon.com/ses/latest/dg/request-production-access.html
