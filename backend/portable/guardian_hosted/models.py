import uuid
from django.db import models

class Account(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    email = models.EmailField(unique=True)
    password = models.CharField(max_length=256)
    version = models.PositiveIntegerField(default=1)
    verified = models.BooleanField(default=False)
    deleted = models.BooleanField(default=False)
    created_at = models.BigIntegerField()
    cooldown_until = models.BigIntegerField(default=0)
    active_event = models.CharField(max_length=36, blank=True)

class DeviceSession(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    account = models.ForeignKey(Account, on_delete=models.CASCADE)
    refresh_digest = models.CharField(max_length=64, unique=True)
    expires_at = models.BigIntegerField()
    version = models.PositiveIntegerField()
    revoked = models.BooleanField(default=False)

class Challenge(models.Model):
    account = models.ForeignKey(Account, on_delete=models.CASCADE)
    purpose = models.CharField(max_length=12)
    digest = models.CharField(max_length=64)
    expires_at = models.BigIntegerField()
    attempts = models.PositiveIntegerField(default=0)
    consumed = models.BooleanField(default=False)

class Record(models.Model):
    owner_key = models.CharField(max_length=160)
    sk = models.CharField(max_length=160)
    payload = models.JSONField(default=dict)
    class Meta:
        constraints = [models.UniqueConstraint(fields=['owner_key', 'sk'], name='guardian_record_key')]

class Counter(models.Model):
    key = models.CharField(max_length=200, unique=True)
    used = models.BigIntegerField(default=0)
    expires_at = models.BigIntegerField()

class EmergencyEvent(models.Model):
    account = models.ForeignKey(Account, on_delete=models.CASCADE)
    event_id = models.UUIDField(default=uuid.uuid4)
    state = models.CharField(max_length=20, default='PENDING')
    payload = models.JSONField(default=dict)
    created_at = models.BigIntegerField()
    expires_at = models.BigIntegerField()
    tier = models.CharField(max_length=12)
    package_type = models.CharField(max_length=20,default='ALERT_ONLY')
    total_bytes = models.BigIntegerField(default=0)
    test = models.BooleanField(default=False)
    deleted = models.BooleanField(default=False)
    cloud_purged = models.BooleanField(default=False)
    class Meta:
        constraints = [models.UniqueConstraint(fields=['account', 'event_id'], name='guardian_event_owner_id')]

class Segment(models.Model):
    event = models.ForeignKey(EmergencyEvent, on_delete=models.CASCADE)
    index = models.PositiveIntegerField()
    size = models.PositiveIntegerField()
    sha256 = models.CharField(max_length=64)
    content_type = models.CharField(max_length=32)
    duration_ms = models.PositiveIntegerField(default=0)
    object_key = models.CharField(max_length=240)
    complete = models.BooleanField(default=False)
    class Meta:
        constraints = [models.UniqueConstraint(fields=['event', 'index'], name='guardian_segment_event_index')]

class EmailJob(models.Model):
    key = models.CharField(max_length=180, unique=True)
    account = models.ForeignKey(Account, on_delete=models.CASCADE)
    event = models.ForeignKey(EmergencyEvent, null=True, on_delete=models.CASCADE)
    kind = models.CharField(max_length=16)
    recipient = models.EmailField()
    subject = models.CharField(max_length=200)
    text = models.TextField(blank=True)
    state = models.CharField(max_length=20, default='QUEUED')
    created_at = models.BigIntegerField()
    due_at = models.BigIntegerField()
    expires_at = models.BigIntegerField()
    claimed_at = models.BigIntegerField(default=0)
    attempts = models.PositiveIntegerField(default=0)
    error = models.CharField(max_length=64, blank=True)
    message_id = models.CharField(max_length=256, blank=True)
    class Meta:
        indexes = [models.Index(fields=['state', 'due_at'], name='guardian_due_mail')]

class Viewer(models.Model):
    digest = models.CharField(max_length=64, unique=True)
    event = models.ForeignKey(EmergencyEvent, on_delete=models.CASCADE)
    expires_at = models.BigIntegerField()
