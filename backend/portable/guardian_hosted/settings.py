"""No credentials or production defaults in source. Same WSGI app on cPanel/VPS."""
import os
from pathlib import Path
from django.core.exceptions import ImproperlyConfigured

BASE_DIR = Path(__file__).resolve().parents[1]
TESTING = os.environ.get('GUARDIAN_TESTING') == '1'
SECRET_KEY = os.environ.get('GUARDIAN_SECRET_KEY', 'synthetic-test-key-never-use-for-production-123456789' if TESTING else '')
if len(SECRET_KEY) < 48:
    raise ImproperlyConfigured('Set a random GUARDIAN_SECRET_KEY of at least 48 characters outside the web root')
DEBUG = False
ALLOWED_HOSTS = os.environ.get('GUARDIAN_ALLOWED_HOSTS', 'testserver' if TESTING else '').split(',')
if not any(ALLOWED_HOSTS) or '*' in ALLOWED_HOSTS:
    raise ImproperlyConfigured('Set explicit GUARDIAN_ALLOWED_HOSTS')
ROOT_URLCONF = 'guardian_hosted.urls'
INSTALLED_APPS = ['guardian_hosted']
MIDDLEWARE = ['django.middleware.security.SecurityMiddleware', 'guardian_hosted.middleware.ApiBoundary']
USE_TZ = True
TIME_ZONE = 'UTC'
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'
DATA_UPLOAD_MAX_MEMORY_SIZE = 16 * 1024
SECURE_SSL_REDIRECT = not TESTING
SECURE_HSTS_SECONDS = 31536000
SECURE_HSTS_INCLUDE_SUBDOMAINS = False
SECURE_CONTENT_TYPE_NOSNIFF = True
# Enable only behind an operator-controlled proxy that strips incoming forwarded headers.
if os.environ.get('GUARDIAN_TRUST_PROXY') == '1':
    SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')
DB_ENGINE = os.environ.get('GUARDIAN_DB_ENGINE', 'sqlite' if TESTING else 'mysql')
if DB_ENGINE == 'mysql':
    DATABASES = {'default': {'ENGINE': 'django.db.backends.mysql', 'NAME': os.environ['GUARDIAN_DB_NAME'],
        'USER': os.environ['GUARDIAN_DB_USER'], 'PASSWORD': os.environ['GUARDIAN_DB_PASSWORD'],
        'HOST': os.environ.get('GUARDIAN_DB_HOST', 'localhost'), 'PORT': os.environ.get('GUARDIAN_DB_PORT', '3306'),
        'OPTIONS': {'charset': 'utf8mb4', 'init_command': "SET sql_mode='STRICT_TRANS_TABLES'"}, 'CONN_MAX_AGE': 0}}
elif DB_ENGINE == 'sqlite' and TESTING:
    DATABASES = {'default': {'ENGINE': 'django.db.backends.sqlite3', 'NAME': BASE_DIR / 'test-only.sqlite3'}}
else:
    raise ImproperlyConfigured('Production uses MySQL; SQLite is test-only')
AUTH_PASSWORD_VALIDATORS = [{'NAME': 'django.contrib.auth.password_validation.MinimumLengthValidator', 'OPTIONS': {'min_length': 10}},
    {'NAME': 'django.contrib.auth.password_validation.CommonPasswordValidator'}, {'NAME': 'django.contrib.auth.password_validation.NumericPasswordValidator'}]
SERVICE_NAME = os.environ.get('GUARDIAN_SERVICE_NAME', 'NEXVARY Guardian')
SERVICE_URL = os.environ.get('GUARDIAN_SERVICE_URL', 'https://testserver' if TESTING else '').rstrip('/')
from urllib.parse import urlparse
service_address = urlparse(SERVICE_URL)
if service_address.scheme != 'https' or not service_address.hostname or service_address.username or service_address.path or service_address.query or service_address.fragment:
    raise ImproperlyConfigured('GUARDIAN_SERVICE_URL must be an HTTPS origin without a path')
EDITION = os.environ.get('GUARDIAN_EDITION', 'MANAGED')
if EDITION not in ('MANAGED', 'INSTITUTION'):
    raise ImproperlyConfigured('Invalid service edition')
INSTITUTION_EXPIRES = int(os.environ.get('GUARDIAN_INSTITUTION_EXPIRES_AT', '0'))
ENROLLMENT_DOMAINS = tuple(x.strip().lower() for x in os.environ.get('GUARDIAN_ENROLLMENT_DOMAINS', '').split(',') if x.strip())
EMAIL_PROVIDER = os.environ.get('GUARDIAN_EMAIL_PROVIDER', 'DISABLED')
EMAIL_SENDER = os.environ.get('GUARDIAN_EMAIL_SENDER', '')
EMAIL_HOURLY_LIMIT = int(os.environ.get('GUARDIAN_EMAIL_HOURLY_LIMIT', '100'))
EMAIL_DAILY_LIMIT = int(os.environ.get('GUARDIAN_EMAIL_DAILY_LIMIT', '1000'))
SIGNUPS_DAILY_LIMIT = int(os.environ.get('GUARDIAN_SIGNUPS_DAILY_LIMIT', '50'))
EMAIL_INLINE = os.environ.get('GUARDIAN_EMAIL_INLINE', '1') == '1'
HOSTING_PROFILE = os.environ.get('GUARDIAN_HOSTING_PROFILE', 'NAMECHEAP_SHARED')
STORAGE_PROVIDER = os.environ.get('GUARDIAN_STORAGE_PROVIDER', 'DISABLED')
STORAGE_VERIFIED = os.environ.get('GUARDIAN_STORAGE_VERIFIED')=='1'
STORAGE_BUCKET = os.environ.get('GUARDIAN_STORAGE_BUCKET', '')
S3_ENDPOINT = os.environ.get('GUARDIAN_S3_ENDPOINT', '')
S3_REGION = os.environ.get('GUARDIAN_S3_REGION', 'us-east-1')
STORAGE_MONTHLY_BYTES = int(os.environ.get('GUARDIAN_STORAGE_MONTHLY_BYTES', str(3 * 1024**3)))
# No local video upload route or local evidence storage provider exists.
if STORAGE_PROVIDER not in ('DISABLED', 'S3', 'WASABI'):
    raise ImproperlyConfigured('Storage must be a private external object store')
if HOSTING_PROFILE == 'NAMECHEAP_SHARED' and STORAGE_PROVIDER == 'WASABI':
    raise ImproperlyConfigured('Use S3 checksum verification on shared hosting; verify Wasabi on an institution/VPS worker')
if EMAIL_HOURLY_LIMIT < 1 or EMAIL_DAILY_LIMIT < 1 or STORAGE_MONTHLY_BYTES < 1:
    raise ImproperlyConfigured('Capacity limits must be positive')
# Auth and evidence responses must never contain tracebacks or credentials.
LOGGING = {'version': 1, 'disable_existing_loggers': False, 'handlers': {'null': {'class': 'logging.NullHandler'}},
    'loggers': {'django.request': {'handlers': ['null'], 'propagate': False}}}
