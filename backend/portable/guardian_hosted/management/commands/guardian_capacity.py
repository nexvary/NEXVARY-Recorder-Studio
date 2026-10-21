import json
from django.core.management.base import BaseCommand
from guardian_hosted.core import capacity

class Command(BaseCommand):
    help='Read service capacity without exposing user data or credentials.'
    def handle(self,*args,**options): self.stdout.write(json.dumps(capacity(),sort_keys=True))
