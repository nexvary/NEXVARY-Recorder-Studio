import hashlib
import hmac
import secrets
import uuid
import jwt
from django.conf import settings
from django.contrib.auth.hashers import make_password, check_password
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError
from django.core.validators import validate_email
from django.db import transaction
from django.db.models import F
from policy import Rejected
from .models import Account, DeviceSession, Challenge, EmailJob
from .core import now, digest, enqueue, email_ready, rate, take


def email(value):
    if not isinstance(value, str): raise Rejected('INVALID_EMAIL')
    result = value.strip().lower()
    try: validate_email(result)
    except ValidationError: raise Rejected('INVALID_EMAIL')
    return result


def password(value):
    if not isinstance(value, str) or not 10 <= len(value) <= 128: raise Rejected('INVALID_PASSWORD')
    try: validate_password(value)
    except ValidationError: raise Rejected('INVALID_PASSWORD')
    return make_password(value)


def otp_digest(account, purpose, code):
    return hmac.new(settings.SECRET_KEY.encode(), f'otp:{account.id}:{purpose}:{code}'.encode(), hashlib.sha256).hexdigest()


def challenge(account, purpose):
    # Serialize against the account: a resend invalidates earlier codes and pending challenge mail.
    with transaction.atomic():
        Account.objects.select_for_update().get(pk=account.id)
        Challenge.objects.filter(account=account, purpose=purpose, consumed=False).update(consumed=True)
        EmailJob.objects.filter(account=account, kind=purpose, state__in=['QUEUED','RETRYING']).update(state='CANCELLED', text='')
        code = str(secrets.randbelow(1_000_000)).zfill(6)
        row = Challenge.objects.create(account=account, purpose=purpose, digest=otp_digest(account, purpose, code), expires_at=now()+600)
        enqueue(account, f'auth:{row.id}', account.email, 'NEXVARY GUARDIAN — ACCOUNT CODE (NO EMERGENCY)',
            f'Your {purpose.lower()} code is {code}. Expires in 10 minutes. Ignore this email if unexpected.', kind=purpose)


def consume(account, purpose, code, action):
    if not isinstance(code, str) or len(code) != 6 or not code.isascii() or not code.isdigit(): return False
    with transaction.atomic():
        row = Challenge.objects.select_for_update().filter(account=account, purpose=purpose, consumed=False).order_by('-id').first()
        if not row or row.expires_at <= now() or row.attempts >= 5: return False
        row.attempts += 1
        valid = hmac.compare_digest(row.digest, otp_digest(account, purpose, code))
        row.consumed = valid
        row.save(update_fields=['attempts','consumed'])
        if valid: action()
    return valid


def signup(body):
    if not email_ready(): raise Rejected('EMAIL_NOT_CONFIGURED', 503)
    address = email(body.get('email'))
    if settings.EDITION == 'INSTITUTION' and address not in settings.ENROLLMENT_DOMAINS and address.rsplit('@',1)[-1] not in settings.ENROLLMENT_DOMAINS:
        raise Rejected('INSTITUTION_ENROLLMENT_REQUIRED', 403)
    encoded = password(body.get('password'))
    with transaction.atomic():
        account, created = Account.objects.get_or_create(email=address, defaults={'password': encoded, 'created_at': now()})
        if created: take('signups:'+str(now()//86400),settings.SIGNUPS_DAILY_LIMIT,now()+172800)
    if not account.deleted and not account.verified:
        rate(address, 'account-code', 3, 3600)
        challenge(account, 'VERIFY')
    return {'ok': True}


def confirmed(body):
    account = Account.objects.filter(email=email(body.get('email')), deleted=False).first()
    if not account or not consume(account, 'VERIFY', body.get('code'), lambda: Account.objects.filter(pk=account.id).update(verified=True)):
        raise Rejected('INVALID_OR_EXPIRED_CODE')
    return {'ok': True}


def tokens(account, session, refresh):
    access = jwt.encode({'sub': str(account.id), 'sid': str(session.id), 'ver': account.version,
        'iss': settings.SERVICE_URL, 'aud': 'nexvary-guardian-android', 'iat': now(), 'nbf': now(), 'exp': now()+900,
        'jti': str(uuid.uuid4()), 'token_use': 'id'}, settings.SECRET_KEY, algorithm='HS256')
    return {'IdToken': access, 'RefreshToken': refresh, 'ExpiresIn': 900}


def login(body):
    address = email(body.get('email'))
    rate(address,'login-account',5,60)
    value = body.get('password')
    if not isinstance(value, str) or len(value) > 128: raise Rejected('LOGIN_FAILED', 401)
    account = Account.objects.filter(email=address, deleted=False).first()
    # Apply Django's password hasher also when the account is absent.
    valid = check_password(value, account.password) if account else bool(make_password(value)) and False
    if not account or not valid or not account.verified: raise Rejected('LOGIN_FAILED', 401)
    rate(str(account.id), 'sessions', 10, 3600)
    refresh = secrets.token_urlsafe(48)
    session = DeviceSession.objects.create(account=account, refresh_digest=digest(refresh), expires_at=now()+14*86400, version=account.version)
    return tokens(account, session, refresh)


def refresh(body):
    token = body.get('refreshToken')
    if not isinstance(token, str) or not 32 <= len(token) <= 256: raise Rejected('LOGIN_REQUIRED', 401)
    with transaction.atomic():
        session = DeviceSession.objects.select_for_update().select_related('account').filter(refresh_digest=digest(token), revoked=False).first()
        if not session or session.expires_at <= now() or session.account.deleted or session.version != session.account.version:
            raise Rejected('LOGIN_REQUIRED', 401)
        replacement = secrets.token_urlsafe(48)
        session.refresh_digest = digest(replacement)
        session.save(update_fields=['refresh_digest'])
        return tokens(session.account, session, replacement)


def actor(request):
    authorization = request.headers.get('Authorization', '')
    if not authorization.startswith('Bearer '): raise Rejected('LOGIN_REQUIRED', 401)
    try:
        claims = jwt.decode(authorization[7:], settings.SECRET_KEY, algorithms=['HS256'], issuer=settings.SERVICE_URL,
            audience='nexvary-guardian-android', options={'require': ['exp','iat','nbf','sub','sid','ver','token_use']})
        if claims['token_use'] != 'id': raise ValueError()
        session = DeviceSession.objects.select_related('account').get(pk=claims['sid'], account_id=claims['sub'], revoked=False)
        account = session.account
        if account.deleted or not account.verified or session.expires_at <= now() or claims['ver'] != account.version or session.version != account.version:
            raise ValueError()
    except (jwt.InvalidTokenError, ValueError, TypeError, DeviceSession.DoesNotExist, ValidationError):
        raise Rejected('LOGIN_REQUIRED', 401)
    request.guardian_session = session
    return account


def forgot(body):
    address = email(body.get('email'))
    if not email_ready(): raise Rejected('EMAIL_NOT_CONFIGURED', 503)
    rate(address, 'account-code', 3, 3600)
    account = Account.objects.filter(email=address, verified=True, deleted=False).first()
    if account: challenge(account, 'RESET')
    return {'ok': True}


def reset(body):
    address = email(body.get('email'))
    encoded = password(body.get('password'))
    account = Account.objects.filter(email=address, verified=True, deleted=False).first()
    def apply():
        Account.objects.filter(pk=account.id).update(password=encoded, version=F('version')+1)
        DeviceSession.objects.filter(account=account).update(revoked=True)
    if not account or not consume(account, 'RESET', body.get('code'), apply): raise Rejected('INVALID_OR_EXPIRED_CODE')
    return {'ok': True}
