import json
import re
from unittest.mock import patch,Mock
from uuid import uuid4
import hashlib
import base64
import smtplib
import pytest
import boto3
import jwt
from moto import mock_aws
from django.test import Client
from django.contrib.auth.hashers import check_password
from guardian_hosted import auth,emergencies
from guardian_hosted.core import usage,now,capacity,pk,record_put
from guardian_hosted.models import Account,EmailJob,Counter,DeviceSession,Challenge,Segment,EmergencyEvent,Viewer,Record
from guardian_hosted.queue import drain,cleanup
from policy import Rejected
from providers import EmailAcceptanceUnknown

pytestmark=pytest.mark.django_db


def request(client,path,body=None,token=None,method='post'):
    kwargs={'content_type':'application/json'}
    if token:kwargs['HTTP_AUTHORIZATION']='Bearer '+token
    return getattr(client,method)('/'+path,data=json.dumps(body or {}) if method!='get' else {},**kwargs)


def login(account): return auth.login({'email':account.email,'password':'GuardianPass!738'})
def reserve(account):return emergencies.reserve(account,{'eventId':str(uuid4()),'payload':{'displayName':'Guardian QA','timestamp':'2026-10-21T00:00:00Z'}})


def test_public_discovery_exposes_capabilities_without_secrets(client):
    response=client.get('/.well-known/nexvary-guardian')
    assert response.status_code==200
    value=response.json();assert value['authType']=='NEXVARY';assert value['remoteSos'];assert not value['evidenceUpload'];assert value['retryIntervalSeconds']==300
    assert not any(x in response.content.decode().lower() for x in ['password','secret_key','smtp_host'])
    assert response['Cache-Control']=='no-store'


def test_signup_confirm_login_and_authenticated_usage(client):
    body={'email':'NewGuardian@example.com','password':'StrongGuardian!784'}
    assert request(client,'auth/signup',body).status_code==200
    account=Account.objects.get(email='newguardian@example.com')
    assert check_password(body['password'],account.password)
    job=EmailJob.objects.get(account=account);code=re.search(r'code is (\d{6})',job.text)[1]
    assert request(client,'auth/login',body).status_code==401
    assert request(client,'auth/confirm',{'email':body['email'],'code':code}).status_code==200
    response=request(client,'auth/login',body);assert response.status_code==200
    token=response.json()['IdToken']
    assert request(client,'usage',token=token,method='get').json()['monthlyLimit']==3
    assert request(client,'usage',method='get').status_code==401


def test_otp_attempts_persist_and_lock_after_five_wrong_codes(client):
    body={'email':'new@example.com','password':'StrongGuardian!784'}
    request(client,'auth/signup',body)
    account=Account.objects.get(email=body['email'])
    real=re.search(r'code is (\d{6})',EmailJob.objects.get(account=account).text)[1]
    wrong='000001' if real!='000001' else '000002'
    for _ in range(5):assert request(client,'auth/confirm',{'email':body['email'],'code':wrong}).status_code==400
    assert Challenge.objects.get(account=account).attempts==5
    assert request(client,'auth/confirm',{'email':body['email'],'code':real}).status_code==400
    account.refresh_from_db();assert not account.verified


def test_refresh_rotates_token_and_logout_revokes_access(client,account):
    values=login(account)
    newer=request(client,'auth/refresh',{'refreshToken':values['RefreshToken']})
    assert newer.status_code==200
    assert newer.json()['RefreshToken']!=values['RefreshToken']
    assert request(client,'auth/refresh',{'refreshToken':values['RefreshToken']}).status_code==401
    assert request(client,'auth/logout',token=newer.json()['IdToken']).status_code==200
    assert request(client,'usage',token=newer.json()['IdToken'],method='get').status_code==401


def test_forged_access_and_wrong_audience_are_rejected(client,account,settings):
    values=login(account)
    claims=jwt.decode(values['IdToken'],options={'verify_signature':False});claims['aud']='other-client'
    wrong=jwt.encode(claims,settings.SECRET_KEY,algorithm='HS256')
    assert request(client,'usage',token=wrong,method='get').status_code==401
    assert request(client,'usage',token=values['IdToken']+'corrupted',method='get').status_code==401


def test_reset_changes_password_and_revokes_all_sessions(client,account):
    values=login(account)
    assert request(client,'auth/forgot',{'email':account.email}).status_code==200
    code=re.search(r'code is (\d{6})',EmailJob.objects.get(account=account,kind='RESET').text)[1]
    assert request(client,'auth/reset',{'email':account.email,'code':code,'password':'NewGuardianPass!478'}).status_code==200
    assert request(client,'usage',token=values['IdToken'],method='get').status_code==401
    assert request(client,'auth/login',{'email':account.email,'password':'NewGuardianPass!478'}).status_code==200


def test_institution_enrollment_and_license_are_server_owned(client,settings):
    settings.EDITION='INSTITUTION';settings.INSTITUTION_EXPIRES=now()+86400;settings.ENROLLMENT_DOMAINS=('school.example',)
    body={'email':'parent@other.example','password':'StrongGuardian!784'}
    assert request(client,'auth/signup',body).status_code==403
    body['email']='parent@school.example'
    assert request(client,'auth/signup',body).status_code==200
    owner=Account.objects.get(email=body['email'])
    assert usage(owner)['state']=='PREMIUM'
    settings.INSTITUTION_EXPIRES=now()-1
    assert usage(owner)['state']=='FREE'


def test_free_client_cannot_request_institution_or_paid_quota(client,account):
    token=login(account)['IdToken']
    response=request(client,'usage',{'edition':'INSTITUTION','state':'PREMIUM'},token=token,method='get')
    assert response.json()['monthlyLimit']==3


def test_unconfigured_email_does_not_reserve_quota(account,settings):
    settings.EMAIL_PROVIDER='DISABLED'
    with pytest.raises(Rejected,match='REMOTE_SERVICE_NOT_CONFIGURED'):reserve(account)
    assert usage(account)['used']==0


def test_full_package_quota_idempotency_and_rapid_tap_merge(account,connected):
    first=reserve(account)
    for _ in range(12):assert reserve(account)['eventId']==first['eventId']
    assert usage(account)['used']==1
    assert emergencies.reserve(account,{'eventId':first['eventId'],'payload':{}})['eventId']==first['eventId']
    for _ in range(2):
        Account.objects.filter(pk=account.id).update(cooldown_until=0);reserve(account)
    Account.objects.filter(pk=account.id).update(cooldown_until=0)
    with pytest.raises(Rejected,match='MONTHLY_QUOTA_EXHAUSTED'):reserve(account)
    assert usage(account)['remaining']==0


def test_monthly_reset_uses_server_time(account,connected):
    with patch('guardian_hosted.core.now',return_value=1792540800),patch('guardian_hosted.emergencies.now',return_value=1792540800):
        reserve(account);assert usage(account)['used']==1
    with patch('guardian_hosted.core.now',return_value=1793491200):assert usage(account)['used']==0


def test_cross_account_access_and_body_recipients_are_rejected(account,connected):
    value=reserve(account)
    other=Account.objects.create(email='other@example.com',password='!',verified=True,created_at=now())
    with pytest.raises(Rejected,match='EVENT_NOT_FOUND'):emergencies.find(other,value['eventId'])
    item=EmergencyEvent.objects.get(event_id=value['eventId']);item.payload['recipients']=['attacker@example.com'];item.save()
    emergencies.dispatch(account,value['eventId'])
    assert list(EmailJob.objects.values_list('recipient',flat=True))==[account.email]


def test_signed_upload_checksum_verification_and_conflict(account,connected):
    with mock_aws():
        s3=boto3.client('s3');s3.create_bucket(Bucket='guardian-private-evidence')
        eid=reserve(account)['eventId'];raw=b'private-test-evidence';sha=hashlib.sha256(raw).hexdigest()
        body={'eventId':eid,'index':0,'size':len(raw),'sha256':sha,'contentType':'video/mp4'}
        signed=emergencies.sign(account,body);assert signed['url'].startswith('https://')
        segment=Segment.objects.get(index=0)
        s3.put_object(Bucket='guardian-private-evidence',Key=segment.object_key,Body=raw,ChecksumAlgorithm='SHA256',ChecksumSHA256=base64.b64encode(bytes.fromhex(sha)).decode())
        assert emergencies.complete(account,{'eventId':eid,'index':0})['complete']
        assert emergencies.sign(account,body)['complete']
        body['sha256']='0'*64
        with pytest.raises(Rejected,match='SEGMENT_CONFLICT'):emergencies.sign(account,body)


def test_storage_budget_rolls_back_partial_segment_reservation(account,connected,settings):
    settings.STORAGE_MONTHLY_BYTES=8
    eid=reserve(account)['eventId']
    with pytest.raises(Rejected,match='CAPACITY_LIMIT'):
        emergencies.sign(account,{'eventId':eid,'index':0,'size':16,'sha256':'0'*64,'contentType':'video/mp4'})
    assert Segment.objects.count()==0
    assert EmergencyEvent.objects.get(event_id=eid).total_bytes==0


def test_event_tier_survives_premium_expiration(account,connected):
    record_put({'pk':pk(account),'sk':'ENTITLEMENT','state':'PREMIUM','expiresAt':now()+300,'verifiedAt':now()})
    eid=reserve(account)['eventId']
    record_put({'pk':pk(account),'sk':'ENTITLEMENT','state':'EXPIRED','expiresAt':1,'verifiedAt':now()})
    with patch('guardian_hosted.emergencies.storage') as provider:
        provider.return_value.uploadChunk.return_value={'url':'https://example.com'}
        emergencies.sign(account,{'eventId':eid,'index':0,'size':16,'sha256':'0'*64,'contentType':'video/mp4'})
    assert Segment.objects.get().object_key.startswith('premium/')


def test_dispatch_queue_and_acceptance_receipt_prevent_duplicates(account,connected):
    eid=reserve(account)['eventId'];emergencies.dispatch(account,eid);emergencies.dispatch(account,eid)
    with patch('guardian_hosted.queue.mail') as provider:
        provider.return_value.send.return_value='accepted'
        assert drain()==1;assert drain()==0
        assert provider.return_value.send.call_count==1
    assert emergencies.find(account,eid).state=='SENT'
    assert EmailJob.objects.get().text==''


def test_smtp_definitive_failure_retries_same_event(account,connected):
    eid=reserve(account)['eventId'];emergencies.dispatch(account,eid)
    with patch('guardian_hosted.queue.mail') as provider:
        provider.return_value.send.side_effect=smtplib.SMTPDataError(451,b'temporary')
        assert drain()==0
    assert emergencies.find(account,eid).state=='RETRYING'
    EmailJob.objects.update(due_at=0)
    with patch('guardian_hosted.queue.mail') as provider:
        provider.return_value.send.return_value='accepted';assert drain()==1
    assert usage(account)['used']==1


def test_ambiguous_smtp_acceptance_is_never_automatically_resent(account,connected):
    eid=reserve(account)['eventId'];emergencies.dispatch(account,eid)
    with patch('guardian_hosted.queue.mail') as provider:
        provider.return_value.send.side_effect=EmailAcceptanceUnknown()
        drain();drain()
        assert provider.return_value.send.call_count==1
    assert emergencies.find(account,eid).state=='UNCERTAIN'


def test_host_capacity_limit_defers_email_without_losing_event(account,connected,settings):
    settings.EMAIL_HOURLY_LIMIT=1
    eid=reserve(account)['eventId'];emergencies.dispatch(account,eid)
    Counter.objects.create(key='mail-hour:'+str(now()//3600),used=1,expires_at=now()+7200)
    with patch('guardian_hosted.queue.mail') as provider:
        assert drain()==0;provider.assert_not_called()
    job=EmailJob.objects.get();assert job.state=='RETRYING';assert job.attempts==0
    assert EmergencyEvent.objects.get().state=='RETRYING'
    assert capacity()['action']=='REVIEW_EXTERNAL_PROVIDER'


def test_cancel_before_send_blocks_delivery(account,connected):
    eid=reserve(account)['eventId'];emergencies.dispatch(account,eid);emergencies.cancel(account,eid)
    with patch('guardian_hosted.queue.mail') as provider:
        assert drain()==0;provider.assert_not_called()
    assert emergencies.find(account,eid).state=='CANCELLED'


def test_dead_worker_is_uncertain_not_a_new_send(account,connected):
    eid=reserve(account)['eventId'];emergencies.dispatch(account,eid)
    EmailJob.objects.update(state='SENDING',claimed_at=now()-301)
    with patch('guardian_hosted.queue.mail') as provider:drain();provider.assert_not_called()
    assert EmailJob.objects.get().state=='UNCERTAIN'


def test_expiring_viewer_is_revoked_immediately_on_delete(account,connected):
    eid=reserve(account)['eventId'];item=emergencies.find(account,eid)
    raw=emergencies.viewer_token(item).split('/')[-1]
    assert emergencies.viewer(raw).status_code==200
    emergencies.delete_event(account,eid)
    with pytest.raises(Rejected,match='LINK_EXPIRED'):emergencies.viewer(raw)
    assert item.account_id==account.id


def test_expired_viewer_and_xss_are_handled(account,connected):
    item=emergencies.find(account,reserve(account)['eventId']);item.payload['displayName']='<script>alert(1)</script>';item.save()
    raw=emergencies.viewer_token(item).split('/')[-1]
    content=emergencies.viewer(raw).content.decode();assert '<script>' not in content;assert '&lt;script&gt;' in content
    Viewer.objects.update(expires_at=now()-1)
    with pytest.raises(Rejected,match='LINK_EXPIRED'):emergencies.viewer(raw)


def test_delete_account_revokes_all_sessions_and_erases_private_metadata(client,account,connected):
    values=login(account);reserve(account)
    response=request(client,'account',token=values['IdToken'],method='delete')
    assert response.status_code==200
    assert request(client,'usage',token=values['IdToken'],method='get').status_code==401
    account.refresh_from_db();assert account.deleted;assert account.email.endswith('@deleted.invalid')
    assert EmergencyEvent.objects.get().payload=={}


def test_body_size_and_content_type_do_not_accept_video_uploads(client,account):
    token=login(account)['IdToken']
    assert client.post('/events',data=b'X'*17000,content_type='application/json',HTTP_AUTHORIZATION='Bearer '+token).status_code==413
    assert client.post('/uploads/sign',data=b'video',content_type='video/mp4',HTTP_AUTHORIZATION='Bearer '+token).status_code==415
    assert request(client,'events',{'payload':'bad'},token=token).status_code==400


def test_mail_only_profile_sends_location_without_claiming_video_upload(account):
    eid=reserve(account)['eventId'];item=emergencies.find(account,eid)
    assert item.package_type=='ALERT_ONLY'
    assert emergencies.public(item)['uploadEvidence'] is False
    emergencies.dispatch(account,eid)
    with patch('guardian_hosted.queue.mail') as provider:
        provider.return_value.send.return_value='accepted';assert drain()==1
        text=provider.return_value.send.call_args.args[2]
        assert 'saved locally' in text and '/view/' not in text
    assert usage(account)['used']==1 and usage(account)['uploadsRemaining']==0
    with pytest.raises(Rejected,match='CLOUD_EVIDENCE_NOT_ENABLED'):
        emergencies.sign(account,{'eventId':eid,'index':0,'size':16,'sha256':'0'*64,'contentType':'video/mp4'})


def test_cleanup_progresses_past_already_purged_tombstones(account,connected):
    for _ in range(4):
        Account.objects.filter(pk=account.id).update(cooldown_until=0)
        # A server-verified Premium test entitlement supplies a bounded fair-use quota.
        record_put({'pk':pk(account),'sk':'ENTITLEMENT','state':'PREMIUM','expiresAt':now()+300,'verifiedAt':now()})
        eid=reserve(account)['eventId'];emergencies.delete_event(account,eid)
    cleanup(limit=2);assert EmergencyEvent.objects.filter(cloud_purged=True).count()==2
    cleanup(limit=2);assert EmergencyEvent.objects.filter(cloud_purged=True).count()==4


def test_server_signup_budget_rolls_back_account_creation(client,settings):
    settings.SIGNUPS_DAILY_LIMIT=1
    assert request(client,'auth/signup',{'email':'first@example.com','password':'StrongGuardian!784'}).status_code==200
    assert request(client,'auth/signup',{'email':'second@example.com','password':'StrongGuardian!784'}).status_code==429
    assert not Account.objects.filter(email='second@example.com').exists()


def test_unverified_storage_stays_in_mail_only_mode(account,settings):
    settings.STORAGE_PROVIDER='S3';settings.STORAGE_BUCKET='unverified-bucket';settings.STORAGE_VERIFIED=False
    assert emergencies.find(account,reserve(account)['eventId']).package_type=='ALERT_ONLY'


def test_storage_guard_rejects_public_access_or_missing_encryption():
    from django.core.management.base import CommandError
    from guardian_hosted.management.commands.guardian_storage_check import check_private
    client=Mock()
    client.get_public_access_block.return_value={'PublicAccessBlockConfiguration':{'BlockPublicAcls':True,'IgnorePublicAcls':True,'BlockPublicPolicy':True,'RestrictPublicBuckets':True}}
    client.get_bucket_acl.return_value={'Grants':[{'Grantee':{'Type':'CanonicalUser'}}]}
    client.get_bucket_encryption.return_value={'ServerSideEncryptionConfiguration':{'Rules':[{'ApplyServerSideEncryptionByDefault':{'SSEAlgorithm':'AES256'}}]}}
    check_private(client,'private')
    client.get_public_access_block.return_value['PublicAccessBlockConfiguration']['BlockPublicPolicy']=False
    with pytest.raises(CommandError,match='public-access'):check_private(client,'public')
    client.get_public_access_block.return_value['PublicAccessBlockConfiguration']['BlockPublicPolicy']=True
    client.get_bucket_encryption.return_value={'ServerSideEncryptionConfiguration':{'Rules':[]}}
    with pytest.raises(CommandError,match='encryption'):check_private(client,'unencrypted')


def test_explicit_retry_requeues_definitive_failure_but_not_ambiguous_delivery(account,connected):
    eid=reserve(account)['eventId'];emergencies.dispatch(account,eid)
    with patch('guardian_hosted.queue.mail') as provider:
        provider.return_value.send.side_effect=smtplib.SMTPDataError(550,b'definitive rejection')
        drain();assert emergencies.find(account,eid).state=='FAILED'
    emergencies.dispatch(account,eid)
    with patch('guardian_hosted.queue.mail') as provider:assert drain()==0;provider.assert_not_called()
    emergencies.dispatch(account,eid,retry=True)
    with patch('guardian_hosted.queue.mail') as provider:
        provider.return_value.send.side_effect=EmailAcceptanceUnknown();drain()
        emergencies.dispatch(account,eid,retry=True);drain();assert provider.return_value.send.call_count==1
    assert emergencies.find(account,eid).state=='UNCERTAIN' and usage(account)['used']==1


def test_auth_mail_cannot_use_the_reserved_sos_hourly_headroom(account,connected,settings):
    from guardian_hosted.core import enqueue
    settings.EMAIL_HOURLY_LIMIT=2
    enqueue(account,'auth-first',account.email,'Account code','Synthetic only')
    with patch('guardian_hosted.queue.mail') as provider:
        provider.return_value.send.return_value='accepted';assert drain()==1
    enqueue(account,'auth-second',account.email,'Account code','Synthetic only')
    with patch('guardian_hosted.queue.mail') as provider:assert drain()==0;provider.assert_not_called()
    eid=reserve(account)['eventId'];emergencies.dispatch(account,eid)
    with patch('guardian_hosted.queue.mail') as provider:
        provider.return_value.send.return_value='accepted';assert drain()==1
    assert emergencies.find(account,eid).state=='SENT'
    assert EmailJob.objects.get(key='auth-second').state=='RETRYING'
