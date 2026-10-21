"""Authenticated Guardian API + DynamoDB transaction quotas + SQS SES worker.
No AWS provisioning or real email is performed by tests.
"""
import hashlib
import json
import os
import time
from decimal import Decimal
import boto3
from boto3.dynamodb.types import TypeSerializer
from botocore.exceptions import ClientError
from policy import *
from providers import S3StorageProvider, WasabiStorageProvider, AmazonSesProvider

SER = TypeSerializer()

def table(): return boto3.resource("dynamodb").Table(os.environ["TABLE_NAME"])
def wire(value): return {key: SER.serialize(item) for key, item in value.items()}
def get(pk, sk): return table().get_item(Key={"pk": pk, "sk": sk}, ConsistentRead=True).get("Item", {})
def put(item): table().put_item(Item=item)
def storage():
    if os.environ.get("STORAGE_PROVIDER", "S3") == "WASABI":
        arn = os.environ.get("WASABI_SECRET_ARN")
        if not arn: raise Rejected("STORAGE_NOT_CONFIGURED", 503)
        value = json.loads(boto3.client("secretsmanager").get_secret_value(SecretId=arn)["SecretString"])
        return WasabiStorageProvider(value["bucket"], value["endpoint"], value["region"], value["accessKey"], value["secretKey"])
    return S3StorageProvider(os.environ["EVIDENCE_BUCKET"])
def mail(): return AmazonSesProvider(os.environ["SES_SENDER"])
def now(): return int(time.time())
def response(status, value):
    return {"statusCode": status, "headers": {"Content-Type": "application/json", "Cache-Control": "no-store"},
            "body": json.dumps(value, default=lambda x: int(x) if isinstance(x, Decimal) else str(x))}
def transaction(items):
    try:
        boto3.client("dynamodb").transact_write_items(TransactItems=items)
    except ClientError as exc:
        if exc.response["Error"]["Code"] == "TransactionCanceledException":
            raise Rejected("CONCURRENT_UPDATE", 409)
        raise

def rate(pk, bucket, limit):
    slot = now() // 60 if bucket == "api" else now() // 86400
    key = {"pk": "RATE#" + pk, "sk": f"{bucket}#{slot}"}
    try:
        table().update_item(Key=key, UpdateExpression="SET #n = if_not_exists(#n,:zero) + :one, #t = :ttl",
            ConditionExpression="attribute_not_exists(#n) OR #n < :limit", ExpressionAttributeNames={"#n": "count", "#t": "ttl"},
            ExpressionAttributeValues={":zero": 0, ":one": 1, ":limit": limit, ":ttl": now() + 172800})
    except ClientError as exc:
        if exc.response["Error"]["Code"] == "ConditionalCheckFailedException": raise Rejected("RATE_LIMIT", 429)
        raise

def actor(event):
    claims = event.get("requestContext", {}).get("authorizer", {}).get("jwt", {}).get("claims", {})
    # Only authenticated, verified Cognito accounts. API Gateway validates issuer/audience/signature/expiry.
    sub, email = claims.get("sub"), claims.get("email")
    if not sub or not email or str(claims.get("email_verified")).lower() != "true" or claims.get("token_use") != "id":
        raise Rejected("VERIFIED_ACCOUNT_REQUIRED", 401)
    if get("USER#" + sub, "DELETED"): raise Rejected("ACCOUNT_DELETED", 403)
    return "USER#" + sub, email

def usage(pk):
    month, reset = period(now())
    state, limit = entitlement(get(pk, "ENTITLEMENT"), now())
    used = int(get(pk, "USAGE#" + month).get("count", 0))
    return {"state": state, "monthlyLimit": limit, "used": used, "remaining": max(0, limit - used),
            "sosRemaining": max(0, limit - used), "uploadsRemaining": max(0, limit - used), "resetAt": reset, "serverTime": now()}

def event_get(pk, eid):
    item = get(pk, "EVENT#" + event_id(eid))
    if not item: raise Rejected("EVENT_NOT_FOUND", 404)
    if int(item["ttl"]) <= now(): raise Rejected("EVENT_EXPIRED", 410)
    if item.get("deleted"): raise Rejected("EVIDENCE_DELETED", 410)
    return item

def public_event(item):
    return {key: item[key] for key in ("eventId", "state", "createdAt", "ttl", "test", "error", "messageId") if key in item}

def reserve(pk, email, body):
    eid = event_id(body.get("eventId"))
    previous = get(pk, "EVENT#" + eid)
    if previous:
        return {**public_event(event_get(pk, eid)), "usage": usage(pk)}
    period_key, reset = period(now())
    current = usage(pk)
    last = get(pk, "COOLDOWN")
    override = body.get("override") is True  # Authenticated parent session only, still daily/monthly capped.
    if now() - int(last.get("at", 0)) < COOLDOWN_SECONDS and not override:
        previous = get(pk, "EVENT#" + last["eventId"])
        if previous and not previous.get("deleted") and int(previous.get("ttl", 0)) > now():
            return {**public_event(previous), "merged": True, "usage": current}
    if current["remaining"] <= 0: raise Rejected("MONTHLY_QUOTA_EXHAUSTED", 429)
    day = str(now() // 86400)
    state, _ = entitlement(get(pk, "ENTITLEMENT"), now())
    contacts = [item for item in query(pk) if item["sk"].startswith("CONTACT#") and item.get("verified")]
    recipients = [email] + [item["email"] for item in contacts if item["email"] != email]
    recipients = list(dict.fromkeys(recipients))[:1 if state == "FREE" else 5]
    # Default 48 hours Free, 7 days Premium. Local evidence is never removed here.
    item = {"pk": pk, "sk": "EVENT#" + eid, "eventId": eid, "state": "PENDING", "createdAt": now(),
            "ttl": now() + (172800 if state == "FREE" else 604800), "test": body.get("test") is True,
            "payload": safe_payload(body.get("payload", {})), "recipients": recipients, "totalBytes": 0}
    key = {"pk": pk, "sk": "USAGE#" + period_key}
    cool_condition = "attribute_not_exists(#at) OR #at <= :cutoff"
    tx = [
        {"Put": {"TableName": table().name, "Item": wire(item), "ConditionExpression": "attribute_not_exists(pk)"}},
        {"Update": {"TableName": table().name, "Key": wire(key),
            "UpdateExpression": "SET #n = if_not_exists(#n,:zero) + :one, #t = :ttl",
            "ConditionExpression": "attribute_not_exists(#n) OR #n < :limit", "ExpressionAttributeNames": {"#n": "count", "#t": "ttl"},
            "ExpressionAttributeValues": wire({":zero": 0, ":one": 1, ":limit": current["monthlyLimit"], ":ttl": reset + 86400 * 60})}},
        {"Update": {"TableName": table().name, "Key": wire({"pk": pk, "sk": "DAILY#" + day}),
            "UpdateExpression": "SET #n = if_not_exists(#n,:zero) + :one, #t = :ttl",
            "ConditionExpression": "attribute_not_exists(#n) OR #n < :limit", "ExpressionAttributeNames": {"#n": "count", "#t": "ttl"},
            "ExpressionAttributeValues": wire({":zero": 0, ":one": 1, ":limit": 3 if state == "FREE" else 10, ":ttl": now() + 172800})}},
    ]
    cooldown = {"TableName": table().name, "Item": wire({"pk": pk, "sk": "COOLDOWN", "at": now(), "eventId": eid})}
    if not override:
        cooldown.update(ConditionExpression=cool_condition, ExpressionAttributeNames={"#at": "at"}, ExpressionAttributeValues=wire({":cutoff": now() - COOLDOWN_SECONDS}))
    tx.append({"Put": cooldown})
    try: transaction(tx)
    except Rejected:
        prior = get(pk, "EVENT#" + eid)
        if prior: return {**public_event(prior), "usage": usage(pk)}
        if usage(pk)["remaining"] <= 0: raise Rejected("MONTHLY_QUOTA_EXHAUSTED", 429)
        raise
    return {**public_event(item), "usage": usage(pk)}

def query(pk):
    from boto3.dynamodb.conditions import Key
    result, start = [], None
    while True:
        args = {"KeyConditionExpression": Key("pk").eq(pk), "ConsistentRead": True}
        if start: args["ExclusiveStartKey"] = start
        page = table().query(**args); result.extend(page["Items"])
        start = page.get("LastEvaluatedKey")
        if not start: return result

def sign(pk, body):
    eid = event_id(body.get("eventId")); item = event_get(pk, eid)
    size, index, sha = validate_segment(body)
    # Segments remain immutable, and share the event reservation even after the first alert was sent.
    sk = f"CHUNK#{eid}#{index:03}"
    prior = get(pk, sk)
    if prior:
        if int(prior["size"]) != size or prior["sha256"] != sha: raise Rejected("SEGMENT_CONFLICT", 409)
        if prior.get("complete"): return {"complete": True, "index": index}
    else:
        chunk = {"pk": pk, "sk": sk, "eventId": eid, "index": index, "size": size, "sha256": sha,
            "contentType": body["contentType"], "durationMs": body.get("durationMs", 0), "ttl": int(item["ttl"]),
            "key": f"{'free' if usage(pk)['state']=='FREE' else 'premium'}/{pk}/{eid}/{index:03}.bin", "complete": False}
        transaction([
            {"Put": {"TableName": table().name, "Item": wire(chunk), "ConditionExpression": "attribute_not_exists(pk)"}},
            {"Update": {"TableName": table().name, "Key": wire({"pk": pk, "sk": item["sk"]}),
                "UpdateExpression": "SET totalBytes = totalBytes + :size",
                "ConditionExpression": "attribute_not_exists(deleted) AND totalBytes <= :max",
                "ExpressionAttributeValues": wire({":size": size, ":max": MAX_EVENT_BYTES - size})}}
        ])
        prior = chunk
    return {**storage().uploadChunk(prior["key"], size, sha, body["contentType"]), "index": index}

def complete(pk, body):
    eid = event_id(body.get("eventId")); event_get(pk, eid)
    index = body.get("index")
    if not isinstance(index, int) or not 0 <= index < MAX_SEGMENTS: raise Rejected("INVALID_SEGMENT")
    item = get(pk, f"CHUNK#{eid}#{index:03}")
    if not item: raise Rejected("SEGMENT_NOT_FOUND", 404)
    if not item.get("complete"):
        storage().completeUpload(item["key"], int(item["size"]), item["sha256"])
        table().update_item(Key={"pk": pk, "sk": item["sk"]}, UpdateExpression="SET complete = :yes", ExpressionAttributeValues={":yes": True})
    return {"complete": True, "index": index, "sha256": item["sha256"]}

def dispatch(pk, eid):
    item = event_get(pk, eid)
    # A timed-out API call safely repeats the same SQS FIFO message. Event claims prevent later duplicates.
    if item["state"] in ("SENT", "SENDING", "UNCERTAIN"): return public_event(item)
    try:
        table().update_item(Key={"pk": pk, "sk": item["sk"]}, UpdateExpression="SET #s=:queued",
            ConditionExpression="#s IN (:pending,:failed,:retry,:queued)", ExpressionAttributeNames={"#s": "state"},
            ExpressionAttributeValues={":queued": "QUEUED", ":pending": "PENDING", ":failed": "FAILED", ":retry": "RETRYING"})
    except ClientError as exc:
        if exc.response["Error"]["Code"] == "ConditionalCheckFailedException": return public_event(event_get(pk, eid))
        raise
    boto3.client("sqs").send_message(QueueUrl=os.environ["QUEUE_URL"], MessageBody=json.dumps({"pk": pk, "eventId": eid}),
        MessageGroupId=hashlib.sha256(pk.encode()).hexdigest(), MessageDeduplicationId=eid)
    return public_event(event_get(pk, eid))

def cancel_event(pk, eid):
    item = event_get(pk, eid)
    try:
        table().update_item(Key={"pk": pk, "sk": item["sk"]}, UpdateExpression="SET #s=:cancelled",
            ConditionExpression="NOT (#s IN (:sent,:sending,:uncertain))", ExpressionAttributeNames={"#s": "state"},
            ExpressionAttributeValues={":cancelled": "CANCELLED", ":sent": "SENT", ":sending": "SENDING", ":uncertain": "UNCERTAIN"})
    except ClientError as exc:
        if exc.response["Error"]["Code"] != "ConditionalCheckFailedException": raise
    return public_event(event_get(pk, eid))

def invite(pk, email):
    import secrets
    import re
    if not isinstance(email, str) or len(email) > 254 or not re.fullmatch(r"[^\s<>@]+@[^\s<>@]+\.[^\s<>@]+", email): raise Rejected("INVALID_EMAIL")
    if usage(pk)["state"] == "FREE": raise Rejected("PREMIUM_REQUIRED", 403)
    rate(pk, "invite", 5)
    code = str(secrets.randbelow(1_000_000)).zfill(6)
    digest = hashlib.sha256(email.lower().encode()).hexdigest()
    put({"pk": pk, "sk": "INVITE#" + digest, "email": email.lower(), "code": hashlib.sha256(code.encode()).hexdigest(), "ttl": now() + 600, "attempts": 0})
    mail().send(email, "NEXVARY GUARDIAN — CONTACT VERIFICATION (NO EMERGENCY)",
        f"A Guardian account requested adding you as a trusted contact. Share this code only with the person you trust: {code}\nExpires in 10 minutes. Ignore if unexpected.")
    return {"invitation": digest}

def confirm(pk, body):
    digest = body.get("invitation", ""); item = get(pk, "INVITE#" + digest)
    if not item or int(item.get("ttl", 0)) <= now() or int(item.get("attempts", 0)) >= 5: raise Rejected("INVITATION_EXPIRED")
    table().update_item(Key={"pk": pk, "sk": item["sk"]}, UpdateExpression="ADD attempts :one", ExpressionAttributeValues={":one": 1})
    import hmac
    if not hmac.compare_digest(item["code"], hashlib.sha256(str(body.get("code", "")).encode()).hexdigest()): raise Rejected("INVALID_CODE")
    if len([x for x in query(pk) if x["sk"].startswith("CONTACT#")]) >= 4: raise Rejected("CONTACT_LIMIT")
    put({"pk": pk, "sk": "CONTACT#" + digest, "email": item["email"], "verified": True})
    table().delete_item(Key={"pk": pk, "sk": item["sk"]})
    return {"verified": True}

def erase(pk, eid=None):
    rows = query(pk)
    if eid:
        item = event_get(pk, eid)
        if item["state"] == "SENDING": raise Rejected("SENDING_IN_PROGRESS", 409)
        table().update_item(Key={"pk": pk, "sk": item["sk"]}, UpdateExpression="SET deleted=:yes", ExpressionAttributeValues={":yes": True})
    else:
        # Tombstone prevents re-creation by a still-valid JWT, including a failed Cognito deletion.
        put({"pk": pk, "sk": "DELETED", "at": now()})
    for tier in ("free", "premium"):
        storage().deleteEvidence(f"{tier}/{pk}/{eid}/" if eid else f"{tier}/{pk}/")
    for row in rows:
        if eid and row.get("eventId") != eid: continue
        # Keep a non-sensitive event tombstone until expiry so retries cannot reuse a quota reservation.
        if eid and row["sk"] == "EVENT#" + eid:
            put({"pk": pk, "sk": row["sk"], "eventId": eid, "ttl": row["ttl"], "deleted": True})
        else: table().delete_item(Key={"pk": pk, "sk": row["sk"]})
    if not eid:
        boto3.client("cognito-idp").admin_delete_user(UserPoolId=os.environ["USER_POOL"], Username=pk[5:])
    return {"deleted": True}

def handler(event, context):
    try:
        path = event.get("rawPath", "")
        if path.startswith("/view/") and event.get("requestContext", {}).get("http", {}).get("method") == "GET":
            return viewer(path.split("/")[-1])
        pk, email = actor(event)
        rate(pk, "api", 120)
        ip = event.get("requestContext", {}).get("http", {}).get("sourceIp", "unknown")
        rate("IP#" + hashlib.sha256(ip.encode()).hexdigest(), "api", 600)
        path = event.get("rawPath", ""); method = event.get("requestContext", {}).get("http", {}).get("method", "")
        raw = event.get("body") or "{}"
        if event.get("isBase64Encoded") or len(raw) > 16384: raise Rejected("INVALID_BODY", 413)
        try: body = json.loads(raw)
        except (ValueError, TypeError): raise Rejected("INVALID_JSON")
        if not isinstance(body, dict): raise Rejected("INVALID_JSON")
        if method == "POST" and path == "/billing/verify":
            from billing import verify
            return response(200, verify(pk, body, get, put, now))
        if method == "GET" and path == "/usage": return response(200, usage(pk))
        if method == "POST" and path == "/events": return response(200, reserve(pk, email, body))
        if method == "POST" and path == "/uploads/sign": return response(200, sign(pk, body))
        if method == "POST" and path == "/uploads/complete": return response(200, complete(pk, body))
        if method == "POST" and path == "/contacts/invite": return response(200, invite(pk, body.get("email")))
        if method == "POST" and path == "/contacts/confirm": return response(200, confirm(pk, body))
        if method == "GET" and path == "/contacts": return response(200, {"contacts": [x["email"] for x in query(pk) if x["sk"].startswith("CONTACT#")]})
        if method == "DELETE" and path.startswith("/contacts/"):
            table().delete_item(Key={"pk": pk, "sk": "CONTACT#" + path.split("/")[-1]}); return response(200, {"deleted": True})
        if method == "DELETE" and path == "/account": return response(200, erase(pk))
        if path.startswith("/events/"):
            parts = path.strip("/").split("/"); eid = event_id(parts[1])
            if method == "POST" and len(parts) == 3 and parts[2] == "cancel": return response(200, cancel_event(pk, eid))
            if method == "POST" and len(parts) == 3 and parts[2] == "dispatch": return response(200, dispatch(pk, eid))
            if method == "DELETE" and len(parts) == 2: return response(200, erase(pk, eid))
            if method == "GET" and len(parts) == 2:
                item = event_get(pk, eid)
                chunks = [x for x in query(pk) if x["sk"].startswith("CHUNK#" + eid + "#") and x.get("complete")]
                return response(200, {**public_event(item), "evidence": [{"index": x["index"], "sha256": x["sha256"], "size": x["size"], "url": storage().getSignedUrl(x["key"])} for x in chunks]})
        raise Rejected("NOT_FOUND", 404)
    except Rejected as exc: return response(exc.status, {"error": exc.code, "localSosAvailable": True})
    except ClientError:
        # Never echo AWS errors, recipient addresses or tokens in responses/logs.
        return response(503, {"error": "SERVICE_UNAVAILABLE", "localSosAvailable": True})
    except Exception:
        return response(500, {"error": "INTERNAL_ERROR", "localSosAvailable": True})

def message_text(item, links):
    p = item["payload"]
    lines = ["TEST ALERT — NO EMERGENCY" if item["test"] else "Emergency event detected.",
        "Name: " + p.get("displayName", ""), "Time (device): " + p.get("timestamp", ""),
        "Time (server): " + str(item["createdAt"]), "Location: " + json.dumps(p.get("location")),
        "Battery: " + str(p.get("battery")), "Network: " + p.get("network", ""), "Event ID: " + item["eventId"],
        "View evidence (private temporary links, 15 minutes):"]
    lines.extend(links or ["No finalized evidence uploaded yet. Open Guardian > Safety History to refresh secure links."])
    return "\n".join(lines)

def worker(event, context):
    failures = []
    for record in event.get("Records", []):
        pk = eid = None
        attempting_ses = False
        try:
            body = json.loads(record["body"]); pk, eid = body["pk"], event_id(body["eventId"])
            if get(pk, "DELETED"): continue
            item = event_get(pk, eid)
            if item["state"] in ("SENT", "UNCERTAIN"): continue
            if item["state"] == "SENDING":
                # SES has no idempotency key. A crash after acceptance is ambiguous: do not auto-send twice.
                if now() - int(item.get("sendingAt", 0)) > 180:
                    table().update_item(Key={"pk": pk, "sk": item["sk"]}, UpdateExpression="SET #s=:u", ExpressionAttributeNames={"#s": "state"}, ExpressionAttributeValues={":u": "UNCERTAIN"})
                else: failures.append({"itemIdentifier": record["messageId"]})
                continue
            try:
                table().update_item(Key={"pk": pk, "sk": item["sk"]}, UpdateExpression="SET #s=:s, sendingAt=:at",
                    ConditionExpression="#s IN (:queued,:retry,:failed)", ExpressionAttributeNames={"#s": "state"},
                    ExpressionAttributeValues={":s": "SENDING", ":at": now(), ":queued": "QUEUED", ":retry": "RETRYING", ":failed": "FAILED"})
            except ClientError as exc:
                if exc.response["Error"]["Code"] == "ConditionalCheckFailedException": continue
                raise
            # Persist each recipient acceptance independently; retry never resends accepted recipients.
            chunks = [x for x in query(pk) if x["sk"].startswith("CHUNK#" + eid + "#") and x.get("complete")]
            import secrets
            token = secrets.token_urlsafe(32)
            put({"pk": "VIEW#" + hashlib.sha256(token.encode()).hexdigest(), "sk": "VIEW", "owner": pk, "eventId": eid, "ttl": min(now()+900,int(item["ttl"]))})
            links = [os.environ["API_BASE_URL"].rstrip("/") + "/view/" + token]
            subject = "TEST ALERT — NO EMERGENCY" if item["test"] else "NEXVARY GUARDIAN — EMERGENCY ALERT"
            for recipient in item["recipients"]:
                digest = hashlib.sha256(recipient.encode()).hexdigest()
                if get(pk, f"RECEIPT#{eid}#{digest}"): continue
                attempting_ses = True
                receipt = mail().send(recipient, subject, message_text(item, links))
                put({"pk": pk, "sk": f"RECEIPT#{eid}#{digest}", "messageId": receipt, "ttl": int(item["ttl"]), "eventId": eid})
            table().update_item(Key={"pk": pk, "sk": item["sk"]}, UpdateExpression="SET #s=:s", ExpressionAttributeNames={"#s": "state"}, ExpressionAttributeValues={":s": "SENT"})
        except Rejected:
            continue  # Deleted/expired events must not reach SES.
        except Exception as exc:
            # Only an explicit SES rejection proves the provider did not accept the message.
            definite = not attempting_ses or isinstance(exc, ClientError) and exc.response["Error"]["Code"] in ("TooManyRequestsException", "ThrottlingException", "LimitExceededException", "MessageRejected", "MailFromDomainNotVerifiedException", "AccountSuspendedException", "SendingPausedException")
            if pk and eid:
                try:
                    table().update_item(Key={"pk": pk, "sk": "EVENT#" + eid}, UpdateExpression="SET #s=:s", ExpressionAttributeNames={"#s": "state"}, ExpressionAttributeValues={":s": "RETRYING" if definite else "UNCERTAIN"})
                except Exception: pass
            if definite: failures.append({"itemIdentifier": record["messageId"]})
    return {"batchItemFailures": failures}


def viewer(token):
    import html
    if len(token) != 43: raise Rejected("INVALID_LINK", 404)
    item = get("VIEW#" + hashlib.sha256(token.encode()).hexdigest(), "VIEW")
    if not item or int(item["ttl"]) <= now(): raise Rejected("LINK_EXPIRED", 410)
    pk, eid = item["owner"], item["eventId"]
    if get(pk, "DELETED"): raise Rejected("ACCOUNT_DELETED", 410)
    event_get(pk, eid)
    chunks = [x for x in query(pk) if x["sk"].startswith("CHUNK#"+eid+"#") and x.get("complete")]
    links = "".join('<li><a rel="noreferrer" href="'+html.escape(storage().getSignedUrl(x["key"], min(900,int(item["ttl"])-now())))+'">Evidence '+str(x["index"]+1)+'</a> · SHA-256 '+html.escape(x["sha256"])+"</li>" for x in sorted(chunks,key=lambda x:x["index"]))
    page = '<!doctype html><html lang="en"><meta name="viewport" content="width=device-width"><title>NEXVARY Guardian evidence</title><body><h1>Private emergency evidence</h1><p>This link expires after 15 minutes. Refresh to see newly finalized segments.</p><ul>'+(links or '<li>No finalized evidence yet. Refresh later.</li>')+'</ul></body></html>'
    return {"statusCode": 200, "headers": {"Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "X-Content-Type-Options": "nosniff", "Content-Security-Policy": "default-src 'none'; style-src 'none'; base-uri 'none'; frame-ancestors 'none'"}, "body": page}
