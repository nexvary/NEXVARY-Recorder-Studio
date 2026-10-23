import os
os.environ['GUARDIAN_TESTING']='1'
os.environ.setdefault('AWS_DEFAULT_REGION','us-east-1')
os.environ.setdefault('AWS_ACCESS_KEY_ID','testing')
os.environ.setdefault('AWS_SECRET_ACCESS_KEY','testing')
os.environ.setdefault('AWS_EC2_METADATA_DISABLED','true')
import pytest
from django.contrib.auth.hashers import make_password
from guardian_hosted.models import Account
from guardian_hosted.core import now

@pytest.fixture(autouse=True)
def safe_providers(settings,monkeypatch):
    for key in ('GUARDIAN_SMTP_HOST','GUARDIAN_SMTP_USERNAME','GUARDIAN_SMTP_PASSWORD'):
        monkeypatch.setenv(key,'synthetic-test-only')
    settings.EMAIL_PROVIDER='SMTP';settings.EMAIL_SENDER='alerts@nexvary.example';settings.EMAIL_INLINE=False
    settings.STORAGE_PROVIDER='DISABLED';settings.STORAGE_BUCKET='';settings.STORAGE_VERIFIED=False

@pytest.fixture
def account(db):
    return Account.objects.create(email='guardian@example.com',password=make_password('GuardianPass!738'),verified=True,created_at=now())

@pytest.fixture
def connected(settings):
    settings.STORAGE_PROVIDER='S3';settings.STORAGE_BUCKET='guardian-private-evidence';settings.STORAGE_VERIFIED=True
