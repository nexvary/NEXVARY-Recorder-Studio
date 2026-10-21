"""Read-only account status check. Never creates resources, sends mail or edits DNS."""
import argparse
import json
import boto3
from botocore.exceptions import BotoCoreError, ClientError
p=argparse.ArgumentParser();p.add_argument('--region',required=True);args=p.parse_args()
try:
    client=boto3.client('sesv2',region_name=args.region)
    account=client.get_account()
    identity=client.get_email_identity(EmailIdentity='nexvary.com')
    print(json.dumps({'region':args.region,'ProductionAccessEnabled':account.get('ProductionAccessEnabled'),'SendingEnabled':account.get('SendingEnabled'),'SendQuota':account.get('SendQuota'),'VerifiedForSendingStatus':identity.get('VerifiedForSendingStatus'),'DkimStatus':identity.get('DkimAttributes',{}).get('Status')},indent=2))
except (BotoCoreError,ClientError):
    print(json.dumps({'status':'NOT_VERIFIED','reason':'No usable AWS session or permission; run in the authorized account.'}));raise SystemExit(2)
