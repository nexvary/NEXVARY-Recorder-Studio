"""Load one explicitly selected private JSON configuration for both Passenger and cron."""
import json
import os
import stat
from pathlib import Path


def load_config():
    name=os.environ.get('GUARDIAN_CONFIG_FILE')
    if not name:return
    path=Path(name).resolve(strict=True)
    if any(part.lower() in ('public_html','htdocs','wwwroot') for candidate in (path,Path(name).absolute()) for part in candidate.parts):
        raise RuntimeError('Configuration must be outside the web root')
    public=os.environ.get('GUARDIAN_DOCUMENT_ROOT')
    if public and any(candidate.is_relative_to(Path(public).resolve()) for candidate in (path,Path(name).absolute())):raise RuntimeError('Configuration must be outside the web root')
    if stat.S_IMODE(path.stat().st_mode)&0o077:raise RuntimeError('Private configuration permissions must be 0600')
    if path.stat().st_size>32768:raise RuntimeError('Invalid private configuration')
    data=json.loads(path.read_text())
    if not isinstance(data,dict):raise RuntimeError('Invalid private configuration')
    for key,value in data.items():
        if not isinstance(key,str) or not key.startswith(('GUARDIAN_','AWS_','PLAY_')) or not isinstance(value,str):
            raise RuntimeError('Invalid private configuration entry')
        os.environ.setdefault(key,value)
