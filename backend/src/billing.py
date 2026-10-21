"""Optional Google Play server verification, never a handset premium toggle."""
import hashlib
import json
import os
from datetime import datetime
import boto3
from policy import Rejected

PRODUCTS = {"guardian_premium_monthly", "guardian_premium_yearly"}

def verify(pk, body, get, put, now, claim=None):
    from google.oauth2.service_account import Credentials
    from google.auth.transport.requests import AuthorizedSession
    token = body.get("purchaseToken", "")
    if not isinstance(token, str) or not 10 <= len(token) <= 4096: raise Rejected("INVALID_PURCHASE")
    secret = os.environ.get("PLAY_SECRET_ARN")
    account_file = os.environ.get("PLAY_SERVICE_ACCOUNT_FILE")
    if not secret and not account_file: raise Rejected("BILLING_NOT_CONFIGURED", 503)
    digest = hashlib.sha256(token.encode()).hexdigest()
    owner = get("PURCHASE#" + digest, "OWNER")
    if owner and owner["owner"] != pk: raise Rejected("PURCHASE_OWNED_BY_ANOTHER_ACCOUNT", 403)
    if account_file:
        with open(account_file, encoding="utf-8") as stream:
            account = json.loads(stream.read(65536))
    else:
        account = json.loads(boto3.client("secretsmanager").get_secret_value(SecretId=secret)["SecretString"])
    credentials = Credentials.from_service_account_info(account, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    session = AuthorizedSession(credentials)
    from urllib.parse import quote
    result = session.get("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.nexvary.recorder/purchases/subscriptionsv2/tokens/" + quote(token, safe=""), timeout=15)
    if result.status_code != 200: raise Rejected("PURCHASE_NOT_VERIFIED", 403)
    data = result.json()
    account_id = data.get("externalAccountIdentifiers", {}).get("obfuscatedExternalAccountId")
    if account_id != hashlib.sha256(pk[5:].encode()).hexdigest(): raise Rejected("PURCHASE_ACCOUNT_MISMATCH", 403)
    lines = [x for x in data.get("lineItems", []) if x.get("productId") in PRODUCTS]
    if not lines: raise Rejected("INVALID_PRODUCT", 403)
    expiry = max(int(datetime.fromisoformat(x["expiryTime"].replace("Z", "+00:00")).timestamp()) for x in lines)
    state = {"SUBSCRIPTION_STATE_ACTIVE": "PREMIUM", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD": "GRACE_PERIOD",
        "SUBSCRIPTION_STATE_CANCELED": "PREMIUM", "SUBSCRIPTION_STATE_ON_HOLD": "ACCOUNT_HOLD"}.get(data.get("subscriptionState"), "EXPIRED")
    # A single purchase token belongs to one verified Cognito account, atomically.
    ownership = {"pk": "PURCHASE#" + digest, "sk": "OWNER", "owner": pk}
    if claim is not None:
        claim(ownership)
    else:
        try:
            boto3.resource("dynamodb").Table(os.environ["TABLE_NAME"]).put_item(
                Item={"pk": "PURCHASE#" + digest, "sk": "OWNER", "owner": pk},
                ConditionExpression="attribute_not_exists(pk) OR #o = :owner", ExpressionAttributeNames={"#o": "owner"}, ExpressionAttributeValues={":owner": pk})
        except Exception: raise Rejected("PURCHASE_OWNERSHIP_CONFLICT", 409)
    entitlement = {"pk": pk, "sk": "ENTITLEMENT", "state": state, "expiresAt": expiry, "verifiedAt": now()}
    put(entitlement)
    return {"state": state, "expiresAt": expiry * 1000, "verifiedAt": now() * 1000}
