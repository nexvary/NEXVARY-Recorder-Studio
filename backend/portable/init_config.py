#!/usr/bin/env python3
"""Create a new private template with a random signing secret; never print it or overwrite a file."""
import argparse
import json
import os
import secrets
from pathlib import Path
from urllib.parse import urlparse

parser=argparse.ArgumentParser()
parser.add_argument('path');parser.add_argument('--service-url',required=True)
parser.add_argument('--edition',choices=['MANAGED','INSTITUTION'],default='MANAGED')
args=parser.parse_args()
url=urlparse(args.service_url)
if url.scheme!='https' or not url.hostname or url.path or url.username or url.query or url.fragment:parser.error('Use an HTTPS origin')
path=Path(args.path).resolve()
if any(part.lower() in ('public_html','htdocs','wwwroot') for part in path.parts):parser.error('Use a directory outside the web root')
path.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
value={'GUARDIAN_SECRET_KEY':secrets.token_urlsafe(64),'GUARDIAN_ALLOWED_HOSTS':url.hostname,'GUARDIAN_SERVICE_URL':args.service_url,
    'GUARDIAN_EDITION':args.edition,'GUARDIAN_HOSTING_PROFILE':'NAMECHEAP_SHARED','GUARDIAN_DB_ENGINE':'mysql',
    'GUARDIAN_DB_NAME':'','GUARDIAN_DB_USER':'','GUARDIAN_DB_PASSWORD':'','GUARDIAN_EMAIL_PROVIDER':'DISABLED',
    'GUARDIAN_EMAIL_SENDER':'','GUARDIAN_SMTP_HOST':'','GUARDIAN_SMTP_PORT':'587','GUARDIAN_SMTP_USERNAME':'','GUARDIAN_SMTP_PASSWORD':'',
    'GUARDIAN_EMAIL_HOURLY_LIMIT':'100','GUARDIAN_EMAIL_DAILY_LIMIT':'1000','GUARDIAN_SIGNUPS_DAILY_LIMIT':'50',
    'GUARDIAN_STORAGE_PROVIDER':'DISABLED','GUARDIAN_STORAGE_BUCKET':'','GUARDIAN_STORAGE_MONTHLY_BYTES':str(3*1024**3)}
fd=os.open(path,os.O_CREAT|os.O_EXCL|os.O_WRONLY|getattr(os,'O_NOFOLLOW',0),0o600)
with os.fdopen(fd,'w') as stream:json.dump(value,stream,indent=2);stream.write('\n')
print('Created private configuration. Complete database, mail and optional private storage settings before activation.')
