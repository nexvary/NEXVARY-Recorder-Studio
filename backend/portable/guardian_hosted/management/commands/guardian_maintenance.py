from django.core.management.base import BaseCommand
from guardian_hosted.queue import drain, cleanup

class Command(BaseCommand):
    help='Bounded mail retries and private-object cleanup. Shared-host cron interval must be >=5 minutes.'
    def handle(self,*args,**options):
        sent=drain(limit=10,seconds=50)
        cleanup(limit=10,seconds=30)
        self.stdout.write(f'Processed maintenance; accepted emails: {sent}')
