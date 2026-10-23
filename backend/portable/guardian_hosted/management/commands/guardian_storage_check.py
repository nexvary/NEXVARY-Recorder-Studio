"""Read-only AWS S3 guard. Does not upload files or enable billable services."""
import boto3
from django.conf import settings
from django.core.management.base import BaseCommand,CommandError


def check_private(client,bucket):
    flags=client.get_public_access_block(Bucket=bucket)['PublicAccessBlockConfiguration']
    if not all(flags.get(key) for key in ('BlockPublicAcls','IgnorePublicAcls','BlockPublicPolicy','RestrictPublicBuckets')):
        raise CommandError('Bucket must block every public-access mechanism')
    acl=client.get_bucket_acl(Bucket=bucket)
    if any(x.get('Grantee',{}).get('Type')=='Group' for x in acl.get('Grants',[])):
        raise CommandError('Bucket ACL must not grant public/group access')
    encryption=client.get_bucket_encryption(Bucket=bucket)['ServerSideEncryptionConfiguration']['Rules']
    if not any(x.get('ApplyServerSideEncryptionByDefault',{}).get('SSEAlgorithm') in ('AES256','aws:kms') for x in encryption):
        raise CommandError('Bucket encryption is required')


class Command(BaseCommand):
    help='Read-only private-bucket/encryption check; staged checksum/expiry/deletion tests are also required before activation.'
    def handle(self,*args,**options):
        if settings.STORAGE_PROVIDER!='S3' or not settings.STORAGE_BUCKET or settings.S3_ENDPOINT:
            raise CommandError('This check is for AWS S3 only. Other stores need provider-specific staged verification.')
        check_private(boto3.client('s3',region_name=settings.S3_REGION),settings.STORAGE_BUCKET)
        self.stdout.write('Private access and encryption checks passed. Complete staged upload/checksum/lifecycle/deletion tests, then set GUARDIAN_STORAGE_VERIFIED=1 privately. No configuration was changed.')
