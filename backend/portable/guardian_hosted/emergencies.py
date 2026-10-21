import base64
import hmac
import secrets
from django.conf import settings
from django.db import transaction
from django.db.models import F
from django.utils.html import escape
from django.http import HttpResponse
from policy import Rejected, event_id, safe_payload, validate_segment, period, MAX_EVENT_BYTES
from .models import Account, EmergencyEvent, Segment, Viewer, Record, EmailJob
from .core import now, usage, take, digest, pk, enqueue, email_ready, storage_ready, storage, record_get


def find(account, eid):
    value = EmergencyEvent.objects.filter(account=account, event_id=event_id(eid)).first()
    if not value: raise Rejected('EVENT_NOT_FOUND', 404)
    if value.deleted: raise Rejected('EVIDENCE_DELETED', 410)
    if value.expires_at <= now(): raise Rejected('EVENT_EXPIRED', 410)
    return value


def public(item):
    return {'eventId': str(item.event_id), 'state': item.state, 'createdAt': item.created_at, 'ttl': item.expires_at, 'test': item.test,'packageType':item.package_type,'uploadEvidence':item.package_type=='FULL_EVIDENCE'}


def reserve(account, body):
    eid = event_id(body.get('eventId'))
    # Check an existing reservation first so retries remain valid during provider degradation.
    with transaction.atomic():
        owner = Account.objects.select_for_update().get(pk=account.id)
        prior = EmergencyEvent.objects.filter(account=owner, event_id=eid).first()
        if prior: return {**public(find(owner,eid)), 'usage': usage(owner)}
        if owner.cooldown_until > now():
            prior = EmergencyEvent.objects.filter(account=owner, event_id=owner.active_event, deleted=False, expires_at__gt=now()).exclude(state='CANCELLED').first()
            if prior: return {**public(prior), 'merged': True, 'usage': usage(owner)}
        if not email_ready(): raise Rejected('REMOTE_SERVICE_NOT_CONFIGURED', 503)
        current = usage(owner)
        if current['remaining'] <= 0: raise Rejected('MONTHLY_QUOTA_EXHAUSTED', 429)
        month, reset = period(now())
        take(f'quota:{owner.id}:{month}', current['monthlyLimit'], reset+60*86400)
        take(f'daily:{owner.id}:{now()//86400}', 3 if current['state']=='FREE' else 10, now()+172800)
        tier = 'free' if current['state']=='FREE' else 'premium'
        item = EmergencyEvent.objects.create(account=owner, event_id=eid, payload=safe_payload(body.get('payload',{})),
            tier=tier,package_type='FULL_EVIDENCE' if storage_ready() else 'ALERT_ONLY',created_at=now(), expires_at=now()+(172800 if tier=='free' else 604800), test=body.get('test') is True)
        owner.active_event, owner.cooldown_until = eid, now()+600
        owner.save(update_fields=['active_event','cooldown_until'])
        return {**public(item), 'usage': usage(owner)}


def sign(account, body):
    size, index, sha = validate_segment(body)
    with transaction.atomic():
        item = find(account, body.get('eventId'))
        item = EmergencyEvent.objects.select_for_update().get(pk=item.id)
        if item.package_type!='FULL_EVIDENCE':raise Rejected('CLOUD_EVIDENCE_NOT_ENABLED',503)
        if item.state == 'CANCELLED': raise Rejected('EVENT_CANCELLED', 409)
        prior = Segment.objects.filter(event=item, index=index).first()
        if prior:
            if prior.size != size or prior.sha256 != sha or prior.content_type != body['contentType']: raise Rejected('SEGMENT_CONFLICT', 409)
        else:
            if item.total_bytes + size > MAX_EVENT_BYTES: raise Rejected('EVENT_TOO_LARGE', 413)
            month, reset = period(now())
            take('storage:' + month, settings.STORAGE_MONTHLY_BYTES, reset+86400, size)
            prior = Segment.objects.create(event=item,index=index,size=size,sha256=sha,content_type=body['contentType'],duration_ms=body.get('durationMs',0),
                object_key=f'{item.tier}/{pk(account)}/{item.event_id}/{index:03}.bin')
            item.total_bytes += size; item.save(update_fields=['total_bytes'])
    if prior.complete: return {'complete':True,'index':index}
    return {**storage().uploadChunk(prior.object_key,size,sha,prior.content_type),'index':index}


def complete(account, body):
    item = find(account,body.get('eventId'))
    index = body.get('index')
    if isinstance(index,bool) or not isinstance(index,int) or not 0 <= index < 24: raise Rejected('INVALID_SEGMENT')
    if item.state == 'CANCELLED': raise Rejected('EVENT_CANCELLED',409)
    chunk = Segment.objects.filter(event=item,index=index).first()
    if not chunk: raise Rejected('SEGMENT_NOT_FOUND',404)
    if not chunk.complete:
        storage().completeUpload(chunk.object_key,chunk.size,chunk.sha256)
        Segment.objects.filter(pk=chunk.id).update(complete=True)
    return {'complete':True,'index':index,'sha256':chunk.sha256}


def viewer_token(item):
    raw = secrets.token_urlsafe(32)
    Viewer.objects.create(event=item,digest=digest(raw),expires_at=min(item.expires_at,now()+900))
    return settings.SERVICE_URL + '/view/' + raw


def dispatch(account, eid):
    with transaction.atomic():
        item = find(account,eid)
        item = EmergencyEvent.objects.select_for_update().get(pk=item.id)
        if item.state in ('SENT','SENDING','UNCERTAIN','CANCELLED'): return public(item)
        recipients = [account.email]
        if usage(account)['state'] != 'FREE':
            recipients += [r.payload['email'] for r in Record.objects.filter(owner_key=pk(account),sk__startswith='CONTACT#') if r.payload.get('verified')]
        for recipient in list(dict.fromkeys(recipients))[:1 if usage(account)['state']=='FREE' else 5]:
            enqueue(account,f'sos:{item.id}:{digest(recipient)}',recipient,
                'TEST ALERT — NO EMERGENCY' if item.test else 'NEXVARY GUARDIAN — EMERGENCY ALERT',kind='SOS',event=item,expires=item.expires_at)
        item.state='QUEUED'; item.save(update_fields=['state'])
        return public(item)


def cancel(account,eid):
    with transaction.atomic():
        item=find(account,eid)
        item=EmergencyEvent.objects.select_for_update().get(pk=item.id)
        if item.state not in ('SENT','SENDING','UNCERTAIN'):
            item.state='CANCELLED';item.save(update_fields=['state'])
            EmailJob.objects.filter(event=item,state__in=['QUEUED','RETRYING']).update(state='CANCELLED',text='')
        return public(item)


def alert_text(item):
    body=item.payload; place=body.get('location')
    location=f"https://maps.google.com/?q={place['latitude']},{place['longitude']}" if place else 'Unavailable'
    return ('TEST ALERT — NO EMERGENCY\n' if item.test else 'Emergency event detected.\n') + '\n'.join([
        'Name: '+str(body.get('displayName','')), 'Time: '+str(body.get('timestamp','')), 'Location: '+location,
        'Battery: '+str(body.get('battery')), 'Network: '+str(body.get('network','')), 'Event ID: '+str(item.event_id),
        ('View evidence (temporary private link): '+viewer_token(item)) if item.package_type=='FULL_EVIDENCE' else 'Evidence is saved locally on the device; this service sends text alerts only.'])


def viewer(raw):
    row=Viewer.objects.select_related('event','event__account').filter(digest=digest(raw),expires_at__gt=now()).first()
    if not row or row.event.deleted or row.event.account.deleted or row.event.expires_at <= now(): raise Rejected('LINK_EXPIRED',410)
    links=[]
    for part in Segment.objects.filter(event=row.event,complete=True).order_by('index'):
        url=storage().getSignedUrl(part.object_key,min(900,row.expires_at-now()))
        links.append(f'<li><a href="{escape(url)}" rel="noreferrer">Segment {part.index+1}</a> — SHA-256: {escape(part.sha256)}</li>')
    label='TEST ALERT — NO EMERGENCY' if row.event.test else 'NEXVARY Guardian evidence'
    return HttpResponse('<!doctype html><html><head><meta charset="utf-8"><title>'+label+'</title></head><body><h1>'+label+'</h1><p>'+escape(row.event.payload.get('displayName',''))+'</p><ul>'+''.join(links)+'</ul><p>Links expire. Reload this page while valid for newly closed segments.</p></body></html>')


def delete_event(account,eid):
    item=find(account,eid)
    with transaction.atomic():
        item=EmergencyEvent.objects.select_for_update().get(pk=item.id)
        item.deleted=True;item.payload={};item.save(update_fields=['deleted','payload'])
        Viewer.objects.filter(event=item).delete()
        EmailJob.objects.filter(event=item).exclude(state__in=['SENT','SENDING','UNCERTAIN']).update(state='CANCELLED',text='')
    # Files are deleted by the same bounded maintenance command; viewer revocation is immediate.
    return {'deleted':True,'cloudDeletionPending':Segment.objects.filter(event=item).exists()}
