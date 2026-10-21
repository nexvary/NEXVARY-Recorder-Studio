import json
from django.conf import settings
from django.db import transaction, DatabaseError, IntegrityError
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.core.exceptions import RequestDataTooBig
from policy import Rejected
from . import auth, emergencies
from .core import now, pk, usage, capabilities, rate, enqueue, record_get, record_put, digest, email_ready
from .models import Account, Record, DeviceSession, EmergencyEvent, EmailJob, Viewer
from .queue import drain


def invite(account,body):
    import secrets
    from uuid import uuid4
    if usage(account)['state']=='FREE': raise Rejected('PREMIUM_REQUIRED',403)
    address=auth.email(body.get('email'))
    rate(str(account.id),'invite',5,86400)
    code=str(secrets.randbelow(1_000_000)).zfill(6)
    key=digest(address)
    record_put({'pk':pk(account),'sk':'INVITE#'+key,'email':address,'code':auth.otp_digest(account,'CONTACT:'+key,code),'expires':now()+600,'attempts':0})
    enqueue(account,'contact:'+str(uuid4()),address,'NEXVARY GUARDIAN — CONTACT CODE (NO EMERGENCY)',
        'A Guardian account requested adding you. Code: '+code+'. Expires in 10 minutes. Share only with a person you trust.',kind='CONTACT')
    return {'invitation':key}


def confirm_contact(account,body):
    import hmac
    key=body.get('invitation')
    if not isinstance(key,str) or len(key)!=64: raise Rejected('INVALID_INVITATION')
    with transaction.atomic():
        Account.objects.select_for_update().get(pk=account.id)
        record=Record.objects.select_for_update().filter(owner_key=pk(account),sk='INVITE#'+key).first()
        if not record or record.payload['expires']<=now() or record.payload['attempts']>=5: raise Rejected('INVITATION_EXPIRED')
        value=record.payload;value['attempts']+=1
        valid=isinstance(body.get('code'),str) and hmac.compare_digest(value['code'],auth.otp_digest(account,'CONTACT:'+key,body['code']))
        record.payload=value;record.save(update_fields=['payload'])
        if valid:
            if Record.objects.filter(owner_key=pk(account),sk__startswith='CONTACT#').count()>=4: raise Rejected('CONTACT_LIMIT')
            record_put({'pk':pk(account),'sk':'CONTACT#'+key,'email':value['email'],'verified':True})
            record.delete()
    if not valid: raise Rejected('INVALID_CODE')
    return {'verified':True}


def erase_account(account):
    with transaction.atomic():
        owner=Account.objects.select_for_update().get(pk=account.id)
        owner.deleted=True;owner.verified=False;owner.email=str(owner.id)+'@deleted.invalid';owner.password='!'
        owner.save(update_fields=['deleted','verified','email','password'])
        DeviceSession.objects.filter(account=owner).delete()
        EmergencyEvent.objects.filter(account=owner).update(deleted=True,payload={})
        Viewer.objects.filter(event__account=owner).delete()
        EmailJob.objects.filter(account=owner).exclude(state='SENDING').update(text='',recipient='',subject='',message_id='',state='CANCELLED')
        Record.objects.filter(owner_key=pk(owner)).delete()
        from .models import Challenge
        Challenge.objects.filter(account=owner).delete()
    return {'deleted':True,'cloudDeletionPending':True}


def billing(account,body):
    from billing import verify
    def claim(owner):
        try:
            with transaction.atomic():
                Record.objects.create(owner_key=owner['pk'],sk=owner['sk'],payload=owner)
        except IntegrityError:
            existing=record_get(owner['pk'],owner['sk'])
            if existing.get('owner')!=owner['owner']: raise Rejected('PURCHASE_OWNED_BY_ANOTHER_ACCOUNT',403)
    return verify(pk(account),body,record_get,record_put,now,claim=claim)


@csrf_exempt
def api(request,path=''):
    """Bearer-only API. Never uses browser cookies for authority."""
    try:
        ip=request.META.get('REMOTE_ADDR','unknown')
        rate(ip,'ip-api',120)
        if request.method=='GET' and path=='.well-known/nexvary-guardian': return JsonResponse(capabilities())
        if request.method=='GET' and path.startswith('view/'): return emergencies.viewer(path[5:])
        try: body=json.loads(request.body or b'{}')
        except (ValueError,UnicodeError): raise Rejected('INVALID_JSON')
        if not isinstance(body,dict): raise Rejected('INVALID_JSON')
        method=request.method
        if path.startswith('auth/'):
            if method!='POST': raise Rejected('METHOD_NOT_ALLOWED',405)
            rate(ip,'auth',30)
            action=path[5:]
            handlers={'signup':auth.signup,'confirm':auth.confirmed,'login':auth.login,'refresh':auth.refresh,'forgot':auth.forgot,'reset':auth.reset}
            if action=='logout':
                account=auth.actor(request)
                DeviceSession.objects.filter(pk=request.guardian_session.id).update(revoked=True)
                return JsonResponse({'ok':True})
            handler=handlers.get(action)
            if not handler: raise Rejected('NOT_FOUND',404)
            result=handler(body)
            # Request-triggered first attempt; cron is fallback, never a permanent daemon on shared hosting.
            address=body.get('email','').strip().lower() if isinstance(body.get('email'),str) else ''
            owner=Account.objects.filter(email=address,deleted=False).first()
            if owner and settings.EMAIL_INLINE and action in ('signup','forgot'): drain(1,10,owner.id)
            return JsonResponse(result)
        account=auth.actor(request)
        rate(str(account.id),'user-api',60)
        if method=='GET' and path=='usage': result={**usage(account),'capabilities':capabilities()}
        elif method=='POST' and path=='events': result=emergencies.reserve(account,body)
        elif method=='POST' and path=='uploads/sign': result=emergencies.sign(account,body)
        elif method=='POST' and path=='uploads/complete': result=emergencies.complete(account,body)
        elif method=='POST' and path=='contacts/invite': result=invite(account,body)
        elif method=='POST' and path=='contacts/confirm': result=confirm_contact(account,body)
        elif method=='GET' and path=='contacts': result={'contacts':[x.payload['email'] for x in Record.objects.filter(owner_key=pk(account),sk__startswith='CONTACT#')]}
        elif method=='DELETE' and path.startswith('contacts/'):
            Record.objects.filter(owner_key=pk(account),sk='CONTACT#'+path.split('/')[-1]).delete();result={'deleted':True}
        elif method=='POST' and path=='billing/verify': result=billing(account,body)
        elif method=='DELETE' and path=='account': result=erase_account(account)
        elif path.startswith('events/'):
            pieces=path.split('/');eid=pieces[1]
            if method=='POST' and len(pieces)==3 and pieces[2]=='dispatch': result=emergencies.dispatch(account,eid)
            elif method=='POST' and len(pieces)==3 and pieces[2]=='cancel': result=emergencies.cancel(account,eid)
            elif method=='DELETE' and len(pieces)==2: result=emergencies.delete_event(account,eid)
            elif method=='GET' and len(pieces)==2: result=emergencies.public(emergencies.find(account,eid))
            else: raise Rejected('NOT_FOUND',404)
        else: raise Rejected('NOT_FOUND',404)
        if settings.EMAIL_INLINE and (path.endswith('/dispatch') or path=='contacts/invite'):
            drain(1,10,account.id)
            if path.endswith('/dispatch'): result=emergencies.public(emergencies.find(account,path.split('/')[1]))
        return JsonResponse(result)
    except Rejected as error:
        response=JsonResponse({'error':error.code},status=error.status)
        if error.status==429: response['Retry-After']='60'
        return response
    except RequestDataTooBig: return JsonResponse({'error':'INVALID_BODY'},status=413)
    except DatabaseError: return JsonResponse({'error':'TEMPORARILY_UNAVAILABLE'},status=503)
    except Exception: return JsonResponse({'error':'SERVICE_UNAVAILABLE'},status=503)
