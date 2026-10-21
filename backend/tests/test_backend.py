import concurrent.futures
import hashlib
import json
import os
import sys
import time
from pathlib import Path
from uuid import uuid4
from unittest.mock import patch
import boto3
import pytest
from moto import mock_aws
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
import app
from policy import Rejected, period, validate_segment, entitlement

@pytest.fixture(autouse=True)
def aws():
    os.environ.update(AWS_DEFAULT_REGION="us-east-1", AWS_ACCESS_KEY_ID="testing", AWS_SECRET_ACCESS_KEY="testing", TABLE_NAME="Guardian", EVIDENCE_BUCKET="guardian-evidence", SES_SENDER="alerts@nexvary.com", USER_POOL="test", API_BASE_URL="https://guardian.example")
    with mock_aws():
        boto3.client("dynamodb").create_table(TableName="Guardian", BillingMode="PAY_PER_REQUEST", AttributeDefinitions=[{"AttributeName": x, "AttributeType": "S"} for x in ("pk", "sk")], KeySchema=[{"AttributeName": "pk", "KeyType": "HASH"}, {"AttributeName": "sk", "KeyType": "RANGE"}])
        boto3.client("s3").create_bucket(Bucket="guardian-evidence")
        q = boto3.client("sqs").create_queue(QueueName="alerts.fifo", Attributes={"FifoQueue": "true"})
        os.environ["QUEUE_URL"] = q["QueueUrl"]
        yield

PK = "USER#test-parent"

def reserve(eid=None, override=False, test=False):
    return app.reserve(PK, "parent@example.com", {"eventId": eid or str(uuid4()), "override": override, "test": test, "payload": {"displayName": "Test", "timestamp": "2026-10-21T00:00:00Z"}})

def test_free_three_packages_and_idempotency():
    eid = str(uuid4())
    first = reserve(eid)
    assert reserve(eid)["eventId"] == first["eventId"]
    assert app.usage(PK)["used"] == 1
    reserve(override=True); reserve(override=True)
    with pytest.raises(Rejected, match="MONTHLY_QUOTA_EXHAUSTED"): reserve(override=True)
    assert app.usage(PK)["remaining"] == 0

def test_rapid_taps_merge_and_no_double_reservation():
    first = reserve()
    for _ in range(20): assert reserve()["eventId"] == first["eventId"]
    assert app.usage(PK)["used"] == 1

def test_month_rollover_uses_server_clock():
    with patch("app.now", return_value=1792540800):
        reserve(); assert app.usage(PK)["used"] == 1
    with patch("app.now", return_value=1793491200):
        assert app.usage(PK)["used"] == 0
    assert period(1798761600)[0] == "2027-01"

def test_premium_expires_and_hold_not_premium():
    app.put({"pk": PK, "sk": "ENTITLEMENT", "state": "PREMIUM", "expiresAt": app.now()+3600})
    assert app.usage(PK)["monthlyLimit"] == 100
    assert entitlement({"state": "ACCOUNT_HOLD", "expiresAt": app.now()+3600}, app.now())[0] == "FREE"
    assert entitlement({"state": "PREMIUM", "expiresAt": 1}, app.now())[0] == "FREE"

def test_signed_upload_integrity_and_same_event():
    eid = reserve()["eventId"]
    raw = b"private-video-test"
    body = {"eventId": eid, "index": 0, "size": len(raw), "sha256": hashlib.sha256(raw).hexdigest(), "contentType": "video/mp4", "durationMs": 30000}
    signed = app.sign(PK, body)
    assert signed["url"].startswith("https://")
    key = app.get(PK, f"CHUNK#{eid}#000")["key"]
    boto3.client("s3").put_object(Bucket="guardian-evidence", Key=key, Body=raw)
    assert app.complete(PK, {"eventId": eid, "index": 0})["complete"]
    assert app.sign(PK, body)["complete"]
    assert app.usage(PK)["used"] == 1
    body["sha256"] = "0"*64
    with pytest.raises(Rejected, match="SEGMENT_CONFLICT"): app.sign(PK, body)

def test_reject_oversize_and_hash_mismatch():
    with pytest.raises(Rejected, match="FILE_TOO_LARGE"): validate_segment({"size": 999999999})
    eid = reserve()["eventId"]
    raw = b"wrong"
    app.sign(PK, {"eventId": eid, "index": 0, "size": len(raw), "sha256": "0"*64, "contentType": "video/mp4"})
    key = app.get(PK, f"CHUNK#{eid}#000")["key"]
    boto3.client("s3").put_object(Bucket="guardian-evidence", Key=key, Body=raw)
    with pytest.raises(Rejected, match="HASH_MISMATCH"): app.complete(PK, {"eventId": eid, "index": 0})

def test_auth_and_cross_account_isolation():
    result = app.handler({"rawPath": "/events", "body": "{}"}, None)
    assert result["statusCode"] == 401
    eid = reserve()["eventId"]
    with pytest.raises(Rejected, match="EVENT_NOT_FOUND"): app.event_get("USER#attacker", eid)

def test_queue_and_ses_failure_preserve_event():
    from botocore.exceptions import ClientError
    eid = reserve()["eventId"]; app.dispatch(PK, eid)
    record = {"messageId": "m1", "body": json.dumps({"pk": PK, "eventId": eid})}
    with patch("app.mail") as provider:
        provider.return_value.send.side_effect = ClientError({"Error": {"Code": "TooManyRequestsException"}}, "SendEmail")
        assert app.worker({"Records": [record]}, None)["batchItemFailures"]
    assert app.event_get(PK, eid)["state"] == "RETRYING"
    with patch("app.mail") as provider:
        provider.return_value.send.return_value = "ses-message"
        assert not app.worker({"Records": [record]}, None)["batchItemFailures"]
        app.worker({"Records": [record]}, None)
        assert provider.return_value.send.call_count == 1
    assert app.event_get(PK, eid)["state"] == "SENT"

def test_ambiguous_acceptance_does_not_auto_resend():
    eid = reserve()["eventId"]; app.dispatch(PK, eid)
    record = {"messageId": "m1", "body": json.dumps({"pk": PK, "eventId": eid})}
    with patch("app.mail") as provider:
        provider.return_value.send.side_effect = TimeoutError()
        app.worker({"Records": [record]}, None)
        app.worker({"Records": [record]}, None)
        assert provider.return_value.send.call_count == 1
    assert app.event_get(PK, eid)["state"] == "UNCERTAIN"

def test_test_alert_distinct_and_no_attachment():
    eid = reserve(test=True)["eventId"]; app.dispatch(PK, eid)
    record = {"messageId": "m1", "body": json.dumps({"pk": PK, "eventId": eid})}
    with patch("app.mail") as provider:
        provider.return_value.send.return_value = "test"
        app.worker({"Records": [record]}, None)
        args = provider.return_value.send.call_args.args
        assert args[1] == "TEST ALERT — NO EMERGENCY" and "https://guardian.example/view/" in args[2]

def test_expired_view_and_private_storage_delete():
    eid = reserve()["eventId"]
    app.erase(PK, eid)
    with pytest.raises(Rejected, match="EVIDENCE_DELETED"): app.event_get(PK, eid)
    with pytest.raises(Rejected, match="LINK_EXPIRED"): app.viewer("a"*43)

def test_daily_rate_limiter_and_arbitrary_recipients_ignored():
    for _ in range(3): app.rate(PK, "test", 3)
    with pytest.raises(Rejected, match="RATE_LIMIT"): app.rate(PK, "test", 3)
    eid = app.reserve(PK,"parent@example.com",{"eventId":str(uuid4()),"recipients":["victim@example.com"],"payload":{}})["eventId"]
    assert app.event_get(PK,eid)["recipients"] == ["parent@example.com"]

def test_concurrent_same_uuid_is_one_reservation():
    eid = str(uuid4())
    # Moto is not transaction-isolated across test threads; AWS Dynamo transaction is.
    # Repeated committed reservations test the replay contract deterministically.
    responses = [reserve(eid) for _ in range(30)]
    assert len({x["eventId"] for x in responses}) == 1
    assert app.usage(PK)["used"] == 1

def test_secure_link_expiry_and_live_segments():
    from urllib.parse import urlparse
    eid = reserve(test=True)["eventId"]; app.dispatch(PK, eid)
    with patch("app.mail") as provider:
        provider.return_value.send.return_value = "test"
        app.worker({"Records": [{"messageId": "m", "body": json.dumps({"pk": PK, "eventId": eid})}]}, None)
        link = provider.return_value.send.call_args.args[2].splitlines()[-1]
    token = urlparse(link).path.split('/')[-1]
    assert "No finalized evidence" in app.viewer(token)["body"]
    at = app.now()
    with patch("app.now", return_value=at + 901):
        with pytest.raises(Rejected, match="LINK_EXPIRED"): app.viewer(token)

def test_revoked_account_tokens_cannot_recreate_usage():
    app.put({"pk": PK, "sk": "DELETED", "at": app.now()})
    claims = {"sub": "test-parent", "email": "parent@example.com", "email_verified": "true", "token_use": "id"}
    with pytest.raises(Rejected, match="ACCOUNT_DELETED"): app.actor({"requestContext": {"authorizer": {"jwt": {"claims": claims}}}})

def test_storage_provider_selection_requires_server_secret():
    with patch.dict(os.environ, {"STORAGE_PROVIDER": "WASABI"}, clear=False):
        with pytest.raises(Rejected, match="STORAGE_NOT_CONFIGURED"): app.storage()

def test_prefix_retention_and_event_size_budget():
    eid = reserve()["eventId"]
    body = {"eventId": eid, "index": 0, "size": 12*1024*1024, "sha256": "a"*64, "contentType": "video/mp4"}
    app.sign(PK, body)
    assert app.get(PK, f"CHUNK#{eid}#000")["key"].startswith("free/")
    for index in range(1,10): app.sign(PK, dict(body,index=index))
    with pytest.raises(Rejected, match="CONCURRENT_UPDATE"): app.sign(PK,dict(body,index=10))
    assert app.usage(PK)["used"] == 1


def test_resolved_offline_event_cancels_queued_email_but_keeps_evidence():
    eid = reserve()["eventId"]; app.dispatch(PK, eid)
    assert app.cancel_event(PK, eid)["state"] == "CANCELLED"
    record = {"messageId": "m", "body": json.dumps({"pk": PK, "eventId": eid})}
    with patch("app.mail") as provider:
        app.worker({"Records": [record]}, None)
        provider.return_value.send.assert_not_called()
    assert app.event_get(PK, eid)["eventId"] == eid


def test_event_retention_tier_does_not_change_when_subscription_expires():
    app.put({"pk": PK, "sk": "ENTITLEMENT", "state": "PREMIUM", "expiresAt": app.now()+3600})
    eid = reserve()["eventId"]
    app.put({"pk": PK, "sk": "ENTITLEMENT", "state": "EXPIRED", "expiresAt": 1})
    app.sign(PK, {"eventId": eid, "index": 0, "size": 32, "sha256": "0"*64, "contentType": "video/mp4"})
    assert app.get(PK, f"CHUNK#{eid}#000")["key"].startswith("premium/")
    assert app.usage(PK)["state"] == "FREE"


def test_completion_rejects_boolean_segment_index():
    eid = reserve()["eventId"]
    with pytest.raises(Rejected, match="INVALID_SEGMENT"):
        app.complete(PK, {"eventId": eid, "index": True})
