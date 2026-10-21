#!/usr/bin/env python3
"""Reproducible source deployment bundle. Never include private config, databases or credentials."""
from pathlib import Path
import zipfile
import hashlib
root=Path(__file__).resolve().parents[1]
out=root/'dist/NEXVARY-Guardian-Hosted-Backend-0.11.0.zip'
out.parent.mkdir(exist_ok=True)
files=[]
for directory in ['backend/portable','backend/src']:
    for file in (root/directory).rglob('*'):
        if not file.is_file() or any(x in file.parts for x in ['__pycache__','.pytest_cache','.venv']):continue
        if file.suffix not in ('.py','.txt','.md','.ini'):continue
        files.append(file)
for name in ['HOSTING_RESEARCH_2026-10-21.md','NAMECHEAP_DEPLOYMENT.md','INSTITUTION_DEPLOYMENT.md','FREE_PREMIUM_QUOTAS.md','AWS_SES_SETUP.md']:
    files.append(root/name)
with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED) as archive:
    for file in sorted(files):
        item=zipfile.ZipInfo(str(file.relative_to(root)),date_time=(2026,10,21,0,0,0))
        item.external_attr=0o100644<<16;item.compress_type=zipfile.ZIP_DEFLATED
        archive.writestr(item,file.read_bytes())
with zipfile.ZipFile(out) as archive:
    assert archive.testzip() is None
    assert 'backend/portable/passenger_wsgi.py' in archive.namelist()
    assert not any(name.endswith(('.sqlite3','.env','.p12','.jks')) for name in archive.namelist())
print(out.name,len(files),'files',hashlib.sha256(out.read_bytes()).hexdigest())
