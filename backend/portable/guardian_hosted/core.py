import hashlib
import os
import time
from urllib.parse import urlparse
from django.conf import settings
from django.db import transaction
from django.db.models import F
from policy import Rejected, period, entitlement, FREE_MONTHLY, PREMIUM_MONTHLY
from providers import SmtpProvider, AmazonSesProvider, S3ChecksumStorageProvider, WasabiStorageProvider
from .models import Counter, Record, EmailJob, Segment, Account


def now(): return int(time.time())
def digest(value): return hashlib.sha256(value.encode()).hexdigest()
def pk(account): return 'USER#' + str(account.id)
def record_get(owner, key):
    value = Record.objects.filter(owner_key=owner, sk=key).first()
    return value.payload if value else {}
def record_put(item):
    Record.objects.update_or_create(owner_key=item['pk'], sk=item['sk'], defaults={'payload': item})


def take(key, limit, expiry, amount=1):
    """Conditional SQL increment: one winning reservation, including concurrent requests."""
    Counter.objects.get_or_create(key=key, defaults={'expires_at': expiry})
    if Counter.objects.filter(key=key, used__lte=limit-amount).update(used=F('used')+amount) != 1:
        raise Rejected('CAPACITY_LIMIT', 429)


def rate(subject, bucket='api', limit=60, window=60):
    slot = now() // window
    take('rate:' + bucket + ':' + digest(subject) + ':' + str(slot), limit, (slot+2)*window)


def usage(account):
    verified=record_get(pk(account),'ENTITLEMENT')
    state, limit = entitlement(verified, now())
    expiry = int(verified.get('expiresAt',0))
    subscription_state=state if state!='FREE' else ('ACCOUNT_HOLD' if verified.get('state')=='ACCOUNT_HOLD' else 'EXPIRED' if verified else 'FREE')
    if settings.EDITION == 'INSTITUTION' and settings.INSTITUTION_EXPIRES > now():
        state, limit, expiry = 'PREMIUM', PREMIUM_MONTHLY, settings.INSTITUTION_EXPIRES
        subscription_state='PREMIUM'
    elif settings.EDITION=='INSTITUTION' and settings.INSTITUTION_EXPIRES>0:
        subscription_state='EXPIRED'
    month, reset = period(now())
    counter = Counter.objects.filter(key=f'quota:{account.id}:{month}').first()
    used = counter.used if counter else 0
    return {'state': state, 'subscriptionState':subscription_state, 'edition': settings.EDITION, 'monthlyLimit': limit, 'used': used,
        'remaining': max(0, limit-used), 'sosRemaining': max(0, limit-used), 'uploadsRemaining': max(0, limit-used) if storage_ready() else 0,
        'resetAt': reset, 'serverTime': now(), 'expiresAt': expiry*1000, 'verifiedAt': now()*1000}


def email_ready():
    if not settings.EMAIL_SENDER: return False
    if settings.EMAIL_PROVIDER=='SES': return True
    return settings.EMAIL_PROVIDER=='SMTP' and all(os.environ.get(key) for key in ('GUARDIAN_SMTP_HOST','GUARDIAN_SMTP_USERNAME','GUARDIAN_SMTP_PASSWORD'))
def storage_ready(): return settings.STORAGE_VERIFIED and settings.STORAGE_PROVIDER != 'DISABLED' and bool(settings.STORAGE_BUCKET)
def storage():
    if not storage_ready(): raise Rejected('STORAGE_NOT_CONFIGURED', 503)
    if settings.STORAGE_PROVIDER == 'WASABI':
        return WasabiStorageProvider(settings.STORAGE_BUCKET, settings.S3_ENDPOINT, settings.S3_REGION,
            os.environ['GUARDIAN_STORAGE_ACCESS_KEY'], os.environ['GUARDIAN_STORAGE_SECRET_KEY'])
    import boto3
    from botocore.config import Config
    options = {'region_name': settings.S3_REGION, 'config': Config(signature_version='s3v4', connect_timeout=5, read_timeout=8, retries={'max_attempts': 1})}
    if settings.S3_ENDPOINT:
        address=urlparse(settings.S3_ENDPOINT)
        if address.scheme!='https' or not address.hostname or address.username: raise Rejected('STORAGE_NOT_CONFIGURED',503)
        options['endpoint_url']=settings.S3_ENDPOINT
    return S3ChecksumStorageProvider(settings.STORAGE_BUCKET,boto3.client('s3',**options))


def mail():
    if not email_ready(): raise Rejected('EMAIL_NOT_CONFIGURED', 503)
    if settings.EMAIL_PROVIDER == 'SES': return AmazonSesProvider(settings.EMAIL_SENDER)
    port = int(os.environ.get('GUARDIAN_SMTP_PORT', '587'))
    return SmtpProvider(settings.EMAIL_SENDER, os.environ['GUARDIAN_SMTP_HOST'], port,
        os.environ['GUARDIAN_SMTP_USERNAME'], os.environ['GUARDIAN_SMTP_PASSWORD'], direct_tls=port == 465)


def capabilities():
    return {'schemaVersion': 1, 'authType': 'NEXVARY', 'name': settings.SERVICE_NAME, 'edition': settings.EDITION,
        'remoteSos': email_ready(), 'evidenceUpload': storage_ready(),
        'packageType':'FULL_EVIDENCE' if storage_ready() else 'ALERT_ONLY',
        'billing': settings.EDITION=='MANAGED' and os.environ.get('GUARDIAN_PLAY_BILLING_ENABLED')=='1' and bool(os.environ.get('PLAY_SERVICE_ACCOUNT_FILE') or os.environ.get('PLAY_SECRET_ARN')),
        'queueMode': 'REQUEST_WITH_CRON_FALLBACK', 'retryIntervalSeconds': 300,
        'localSosAlways': True, 'serviceState': capacity()['state']}


def capacity():
    hour = Counter.objects.filter(key='mail-hour:' + str(now()//3600)).first()
    day = Counter.objects.filter(key='mail-day:' + str(now()//86400)).first()
    queued = EmailJob.objects.filter(state__in=['QUEUED', 'RETRYING'])
    oldest = queued.order_by('created_at').first()
    hourly = hour.used if hour else 0
    age = max(0, now()-oldest.created_at) if oldest else 0
    month,_=period(now())
    storage_counter=Counter.objects.filter(key='storage:'+month).first()
    stored=storage_counter.used if storage_counter else 0
    degraded=hourly >= settings.EMAIL_HOURLY_LIMIT*0.7 or age > 60 or stored >= settings.STORAGE_MONTHLY_BYTES*0.7
    return {'state': 'DEGRADED' if degraded else 'READY',
        'hourlyEmails': hourly, 'hourlyLimit': settings.EMAIL_HOURLY_LIMIT,
        'dailyEmails': day.used if day else 0, 'dailyLimit': settings.EMAIL_DAILY_LIMIT,
        'queued': queued.count(), 'oldestQueueSeconds': age,
        'monthlyUploadBytes':stored,'monthlyUploadBudget':settings.STORAGE_MONTHLY_BYTES,
        'uncertainEmails':EmailJob.objects.filter(state='UNCERTAIN').count(),
        'action': 'REVIEW_EXTERNAL_PROVIDER' if degraded else 'MONITOR'}


def enqueue(account, key, recipient, subject, text='', kind='AUTH', event=None, expires=None):
    job, _ = EmailJob.objects.get_or_create(key=key, defaults={'account': account, 'event': event, 'recipient': recipient,
        'subject': subject, 'text': text, 'kind': kind, 'created_at': now(), 'due_at': now(), 'expires_at': expires or now()+600})
    return job
