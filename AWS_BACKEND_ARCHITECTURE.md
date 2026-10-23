# Guardian AWS backend — implementation and deployment

For the new cPanel/institution profile, use `NAMECHEAP_DEPLOYMENT.md` and `backend/portable/README.md`. This document describes the preserved AWS/Cognito deployment; its manual endpoint/region/client fields are now under Android Advanced settings. SMTP/S3 provider migration inside the portable service preserves its own endpoint/accounts; migrating those identities to Cognito requires a separate migration.

Status: source implemented and tested with mocked AWS. **Not deployed to an AWS account in this session.** SES status, sending approval, production DNS, billing catalog and real S3/Wasabi access are unverified. Android package remains `com.nexvary.recorder`.

## Resources

`backend/template.yaml` creates an HTTP API with Cognito JWT authorizer; public-client Cognito user pool (verified email); API Lambda; FIFO SQS + dead-letter queue; SES worker Lambda; DynamoDB on-demand quota/event table; private AES256 S3 bucket; TLS-only bucket policy; 2/7-day lifecycle rules; 7-day CloudWatch logs; SNS queue alarms; AWS Budget. It creates **no EC2 instance, SES identity, domain or DNS record**.

API and worker concurrency limits and API Gateway throttling constrain spikes. Budgets alert, they do not stop charges. Cognito auth traffic and successful/failed signups can have separate costs: configure Cognito threat protection/WAF when usage warrants it. Never claim a fixed monthly cost without checking region and usage.

## Deploy

Use AWS CLI v2, AWS SAM CLI, Python 3.13, an authorized deployment role and a chosen supported AWS region. Prefer AWS SSO. Do not paste keys into code, Gradle, APKs or chat.

```bash
aws sso login --profile guardian-admin
sam build --template-file backend/template.yaml
sam deploy --guided --profile guardian-admin --capabilities CAPABILITY_IAM
aws cloudformation describe-stacks --stack-name YOUR_STACK --profile guardian-admin
```

Supply `SenderEmail=alerts@nexvary.com`, `BudgetEmail` and a budget appropriate to the account. Confirm SNS alarm subscription emails. SAM's guided deploy previews resources and requires an AWS operator to authorize account changes. No deployment or DNS change was attempted here.

Read API URL, Cognito client ID and region from stack outputs; enter them in Parent settings > Cloud & account. Create/confirm a parent account, log in and explicitly enable automatic sharing in Family Safety settings. Free cloud recipient is the **verified account email**, appropriate to a guardian-managed child device. Local trusted contacts/SMS are configured separately. Premium additional cloud recipients require email-code verification through `/contacts/invite` and `/contacts/confirm`; arbitrary recipients sent by a client are ignored. Removal uses `/contacts/{sha256(lowercase_email)}`.

## API

Authenticated: GET `/usage`; POST `/events`; POST `/uploads/sign`; POST `/uploads/complete`; GET `/events/{eventId}`; POST `/events/{eventId}/dispatch`; POST `/events/{eventId}/cancel`; DELETE `/events/{eventId}`; POST `/contacts/invite`; POST `/contacts/confirm`; GET `/contacts`; DELETE `/contacts/{id}`; POST `/billing/verify`; DELETE `/account`.

JWTs are validated by API Gateway (issuer, audience, signature, expiration) and the handler requires a verified Cognito email and ID token. Viewer GET `/view/{random-token}` is intentionally a **bearer link** that expires in 15 minutes; it reveals only that event's evidence, supports refresh as new segments arrive and sends no referrer. A signed S3 URL may remain valid until its own expiration after account/event deletion unless the underlying object has been deleted; explicit deletion removes the objects.

Uploads use signed PUT length, content type and SHA-256 checksum. Completion re-hashes the stored object before marking it accepted. There is no public bucket, client AWS credential or large email attachment. Events are immutable reservations with per-user monthly/daily counters and a cooldown updated in one DynamoDB transaction. Reservation consumes one package even if upload/email later fails; retry uses the same event and does not consume again. This prevents retry-based quota bypass.

SQS FIFO reduces duplicates but SES does not offer an idempotency key. Recipient acceptance receipts prevent re-sending accepted recipients. If SES acceptance is ambiguous (network interruption/process death), status becomes `UNCERTAIN`; it is not automatically sent twice. Operational review is required. A definitive SES rejection retries through SQS; exhausted retries enter the DLQ. No source code can guarantee inbox delivery or emergency-service response.

## Billing activation

Create Play subscriptions `guardian_premium_monthly` ($2.99 target base price) and `guardian_premium_yearly` ($24.99 target base price). Prices displayed in Android come exclusively from ProductDetails; regional prices are controlled in Play Console. Link Google Play Developer API access to a restricted service account, store its JSON in AWS Secrets Manager and pass its ARN as `PlayServiceAccountSecretArn`. It stays server-side.

The verifier calls `purchases.subscriptionsv2.get`, checks package, product, expiry, subscription state, SHA-256 account binding and exclusive token ownership. Android acknowledges only verified purchased subscriptions. Premium/Grace/Hold/Expired are mapped, and restore re-verifies. The verifier is unavailable until configured; no purchase success is simulated. RTDN refresh/revocation automation is a production follow-up; verified entitlements expire from cache after 24 hours so restore/refresh is required. No trial is advertised before a real Play offer exists.

## Verification commands

```bash
python -m pip install -r backend/requirements-dev.txt cfn-lint
python -m pytest -q backend/tests
python -m ruff check backend/src backend/tests --select E9,F63,F7,F82
cfn-lint backend/template.yaml
python backend/check_aws.py --region YOUR_REGION
```

`check_aws.py` is read-only. Run it in the actual account before claiming SES/S3 works. Use only controlled verified recipients/mailbox simulator while in Sandbox. End-to-end staged testing (auth → reserve → signed PUT → checksum → FIFO → SES → expiring viewer → deletion) and alarm/SNS delivery must pass before production release.
