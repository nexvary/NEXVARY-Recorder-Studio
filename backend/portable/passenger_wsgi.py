"""cPanel Setup Python App entry point. Application root must be outside public_html."""
import os
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from bootstrap import load_config
load_config()
if os.environ.get('GUARDIAN_TESTING')=='1':
    raise RuntimeError('Test settings must not be served by Passenger')
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'guardian_hosted.settings')
from django.core.wsgi import get_wsgi_application
application = get_wsgi_application()
