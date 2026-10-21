import smtplib
import ssl
import time
from django.conf import settings
from django.db import transaction
from django.db.models import Q, F, Case, When, Value, IntegerField
from botocore.exceptions import ClientError, ReadTimeoutError, EndpointConnectionError
from policy import Rejected
from providers import EmailAcceptanceUnknown
from .models import Account, EmergencyEvent, EmailJob, Segment, Counter, Challenge, DeviceSession, Viewer
from .core import now, mail, take, email_ready, storage
from .emergencies import alert_text


def aggregate(event_id):
    if not event_id: return
    event=EmergencyEvent.objects.get(pk=event_id)
    if event.deleted or event.state=='CANCELLED': return
    states=list(EmailJob.objects.filter(event_id=event_id).values_list('state',flat=True))
    if not states: return
    if 'UNCERTAIN' in states: state='UNCERTAIN'
    elif 'SENDING' in states: state='SENDING'
    elif all(x=='SENT' for x in states): state='SENT'
    elif 'RETRYING' in states: state='RETRYING'
    elif 'QUEUED' in states: state='QUEUED'
    else: state='FAILED'
    EmergencyEvent.objects.filter(pk=event_id).exclude(state='CANCELLED').update(state=state)


def classify(error):
    if isinstance(error,(EmailAcceptanceUnknown,ReadTimeoutError)): return 'UNCERTAIN','ACCEPTANCE_UNKNOWN'
    if isinstance(error,ssl.SSLCertVerificationError): return 'FAILED','TLS_VERIFICATION_FAILED'
    if isinstance(error,smtplib.SMTPRecipientsRefused):
        code=next(iter(error.recipients.values()))[0]
        return ('RETRYING' if 400 <= code < 500 else 'FAILED'),'RECIPIENT_REJECTED'
    if isinstance(error,smtplib.SMTPResponseException):
        return ('RETRYING' if 400 <= error.smtp_code < 500 else 'FAILED'),'SMTP_REJECTED'
    if isinstance(error,ClientError):
        code=error.response['Error']['Code']
        return ('RETRYING' if code in ('TooManyRequestsException','Throttling','ServiceUnavailableException') else 'FAILED'),'EMAIL_PROVIDER_REJECTED'
    if isinstance(error,(EndpointConnectionError,TimeoutError,OSError)): return 'RETRYING','EMAIL_CONNECTION_FAILED'
    # Unexpected provider failures may occur after acceptance. Prefer review over a duplicate SOS.
    return 'UNCERTAIN','PROVIDER_RESULT_UNKNOWN'


def drain(limit=1, seconds=10, account_id=None):
    if not email_ready(): return 0
    start=time.monotonic(); sent=0
    # A killed worker could have sent DATA. Never reclaim as a normal retry.
    abandoned=list(EmailJob.objects.filter(state='SENDING',claimed_at__lt=now()-300).values_list('event_id',flat=True))
    EmailJob.objects.filter(state='SENDING',claimed_at__lt=now()-300).update(state='UNCERTAIN',error='WORKER_RESULT_UNKNOWN',text='')
    for event_id in set(x for x in abandoned if x): aggregate(event_id)
    for _ in range(min(10,max(0,limit))):
        if time.monotonic()-start >= seconds: break
        candidates=EmailJob.objects.filter(state__in=['QUEUED','RETRYING'],due_at__lte=now())
        if account_id: candidates=candidates.filter(account_id=account_id)
        candidate=candidates.annotate(priority=Case(When(kind='SOS',then=Value(0)),default=Value(1),output_field=IntegerField())).order_by('priority','created_at').first()
        if not candidate: break
        try:
            with transaction.atomic():
                owner=Account.objects.select_for_update().get(pk=candidate.account_id)
                event=EmergencyEvent.objects.select_for_update().get(pk=candidate.event_id) if candidate.event_id else None
                if owner.deleted or candidate.expires_at <= now() or (event and (event.deleted or event.state=='CANCELLED' or event.expires_at <= now())):
                    EmailJob.objects.filter(pk=candidate.id,state__in=['QUEUED','RETRYING']).update(state='CANCELLED',text='');continue
                if EmailJob.objects.filter(pk=candidate.id,state__in=['QUEUED','RETRYING'],due_at__lte=now()).update(state='SENDING',claimed_at=now(),attempts=F('attempts')+1) != 1:
                    continue
                if candidate.kind!='SOS':
                    take('mail-auth-hour:'+str(now()//3600),max(1,int(settings.EMAIL_HOURLY_LIMIT*0.8)),now()+7200)
                take('mail-hour:'+str(now()//3600),settings.EMAIL_HOURLY_LIMIT,now()+7200)
                take('mail-day:'+str(now()//86400),settings.EMAIL_DAILY_LIMIT,now()+172800)
                if event:
                    event.state='SENDING';event.save(update_fields=['state'])
            candidate.refresh_from_db()
            try:
                content=alert_text(event) if event else candidate.text
                message=mail().send(candidate.recipient,candidate.subject,content)
                EmailJob.objects.filter(pk=candidate.id,state='SENDING').update(state='SENT',message_id=message,text='',error='')
                sent+=1
            except Exception as error:
                state,code=classify(error)
                if candidate.attempts >= 6 and state=='RETRYING': state='FAILED'
                EmailJob.objects.filter(pk=candidate.id,state='SENDING').update(state=state,error=code,
                    due_at=now()+min(3600,30*2**min(candidate.attempts,6)),text=candidate.text if state=='RETRYING' else '')
            aggregate(candidate.event_id)
        except Rejected as error:
            if error.code!='CAPACITY_LIMIT': raise
            day=Counter.objects.filter(key='mail-day:'+str(now()//86400)).first()
            due=(now()//86400+1)*86400 if day and day.used>=settings.EMAIL_DAILY_LIMIT else (now()//3600+1)*3600
            EmailJob.objects.filter(pk=candidate.id,state__in=['QUEUED','RETRYING']).update(state='RETRYING',due_at=due,error='HOST_CAPACITY_LIMIT')
            aggregate(candidate.event_id)
            break
    return sent


def cleanup(limit=10,seconds=30):
    """Bounded cron maintenance. Revoke views immediately; remove external objects before metadata."""
    started=time.monotonic()
    for event in EmergencyEvent.objects.filter(Q(deleted=True)|Q(account__deleted=True)|Q(expires_at__lte=now()),cloud_purged=False).order_by('id')[:limit]:
        if time.monotonic()-started >= seconds:break
        Viewer.objects.filter(event=event).delete()
        EmailJob.objects.filter(event=event).exclude(state__in=['SENT','UNCERTAIN','SENDING']).update(state='CANCELLED',text='')
        if Segment.objects.filter(event=event).exists():
            storage().deleteEvidence(f'{event.tier}/USER#{event.account_id}/{event.event_id}/')
            Segment.objects.filter(event=event).delete()
        EmergencyEvent.objects.filter(pk=event.id).update(deleted=True,payload={},cloud_purged=True)
    EmailJob.objects.filter(account__deleted=True).exclude(state='SENDING').update(text='',recipient='',subject='',message_id='')
    Challenge.objects.filter(expires_at__lt=now()-86400).delete()
    DeviceSession.objects.filter(expires_at__lt=now()).delete()
    Viewer.objects.filter(expires_at__lte=now()).delete()
    Counter.objects.filter(expires_at__lt=now()).delete()
    EmailJob.objects.filter(expires_at__lt=now()).exclude(state__in=['SENDING','SENT','UNCERTAIN']).update(state='CANCELLED',text='')

    EmailJob.objects.filter(expires_at__lt=now()-7*86400).exclude(state='SENDING').delete()
    EmergencyEvent.objects.filter(cloud_purged=True,expires_at__lt=now()-60*86400).delete()
