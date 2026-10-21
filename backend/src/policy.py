"""Pure, server-clock based policies. One reservation covers all segments in one SOS."""
from datetime import datetime, timezone
from uuid import UUID

FREE_MONTHLY = 3
PREMIUM_MONTHLY = 100  # Fair use; never gates local SOS.
COOLDOWN_SECONDS = 600
MAX_SEGMENT_BYTES = 12 * 1024 * 1024
MAX_EVENT_BYTES = 120 * 1024 * 1024
MAX_SEGMENTS = 24
STATES = {"PENDING", "UPLOADING", "QUEUED", "SENDING", "SENT", "FAILED", "RETRYING", "UNCERTAIN"}

class Rejected(Exception):
    def __init__(self, code, status=400):
        super().__init__(code)
        self.code, self.status = code, status

def event_id(value):
    try:
        return str(UUID(value))
    except (ValueError, TypeError, AttributeError):
        raise Rejected("INVALID_EVENT_ID")

def period(now):
    dt = datetime.fromtimestamp(now, timezone.utc)
    next_month = dt.replace(year=dt.year + (dt.month == 12), month=dt.month % 12 + 1, day=1, hour=0, minute=0, second=0, microsecond=0)
    return dt.strftime("%Y-%m"), int(next_month.timestamp())

def entitlement(value, now):
    value = value or {}
    if value.get("state") in ("PREMIUM", "TRIAL", "GRACE_PERIOD") and int(value.get("expiresAt", 0)) > now and now - int(value.get("verifiedAt", now)) < 86400:
        return value["state"], PREMIUM_MONTHLY
    return "FREE", FREE_MONTHLY

def validate_segment(body):
    import re
    size, index = body.get("size"), body.get("index")
    if isinstance(size, bool) or not isinstance(size, int) or not 1 <= size <= MAX_SEGMENT_BYTES:
        raise Rejected("FILE_TOO_LARGE", 413)
    if isinstance(index, bool) or not isinstance(index, int) or not 0 <= index < MAX_SEGMENTS:
        raise Rejected("INVALID_SEGMENT")
    sha = body.get("sha256", "")
    if not isinstance(sha, str) or not re.fullmatch("[a-f0-9]{64}", sha):
        raise Rejected("INVALID_HASH")
    if body.get("contentType") not in ("video/mp4", "audio/mp4", "image/jpeg"):
        raise Rejected("INVALID_CONTENT_TYPE")
    duration = body.get("durationMs", 0)
    if isinstance(duration, bool) or not isinstance(duration, int) or not 0 <= duration <= 600_000:
        raise Rejected("INVALID_DURATION")
    return size, index, sha

def safe_payload(body):
    """No client recipients, links, subject, arbitrary HTML or storage keys are accepted."""
    if not isinstance(body, dict): raise Rejected("INVALID_PAYLOAD")
    result = {}
    for key, limit in (("displayName", 120), ("timestamp", 64), ("network", 32), ("message", 500)):
        value = body.get(key, "")
        if not isinstance(value, str):
            raise Rejected("INVALID_PAYLOAD")
        result[key] = value[:limit].replace("\r", " ").replace("\n", " ")
    battery = body.get("battery")
    result["battery"] = battery if isinstance(battery, int) and not isinstance(battery, bool) and 0 <= battery <= 100 else None
    location = body.get("location") or body.get("lastKnownLocation")
    if isinstance(location, dict):
        try:
            lat, lon = float(location["latitude"]), float(location["longitude"])
            if not -90 <= lat <= 90 or not -180 <= lon <= 180:
                raise ValueError()
            result["location"] = {"latitude": str(lat), "longitude": str(lon), "timestamp": str(location.get("timestamp", ""))[:64]}
        except (KeyError, ValueError, TypeError):
            raise Rejected("INVALID_LOCATION")
    return result
