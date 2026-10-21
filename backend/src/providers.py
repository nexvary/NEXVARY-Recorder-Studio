"""All credentials come from the Lambda IAM role or a server-side secret."""
from abc import ABC, abstractmethod
import base64
import hashlib
import boto3
from botocore.config import Config
from policy import Rejected

class EvidenceStorageProvider(ABC):
    @abstractmethod
    def uploadChunk(self, key, size, sha256, content_type): pass
    @abstractmethod
    def completeUpload(self, key, size, sha256): pass
    @abstractmethod
    def getSignedUrl(self, key, expires=900): pass
    @abstractmethod
    def deleteEvidence(self, prefix): pass

class S3StorageProvider(EvidenceStorageProvider):
    def __init__(self, bucket, client=None):
        self.bucket = bucket
        self.client = client or boto3.client("s3", config=Config(signature_version="s3v4"))
    def uploadChunk(self, key, size, sha256, content_type):
        checksum = base64.b64encode(bytes.fromhex(sha256)).decode()
        args = dict(Bucket=self.bucket, Key=key, ContentLength=size, ContentType=content_type, ChecksumSHA256=checksum)
        return {"url": self.client.generate_presigned_url("put_object", Params=args, ExpiresIn=300),
                "headers": {"Content-Type": content_type, "x-amz-checksum-sha256": checksum}, "expiresIn": 300}
    def completeUpload(self, key, size, sha256):
        # Streaming verification works for S3 and Wasabi; never trust client metadata.
        obj = self.client.get_object(Bucket=self.bucket, Key=key)
        if obj["ContentLength"] != size:
            obj["Body"].close()
            raise Rejected("SIZE_MISMATCH")
        digest = hashlib.sha256()
        try:
            for block in obj["Body"].iter_chunks(64 * 1024):
                digest.update(block)
        finally:
            obj["Body"].close()
        if digest.hexdigest() != sha256:
            self.client.delete_object(Bucket=self.bucket, Key=key)
            raise Rejected("HASH_MISMATCH")
    def getSignedUrl(self, key, expires=900):
        return self.client.generate_presigned_url("get_object", Params={"Bucket": self.bucket, "Key": key}, ExpiresIn=min(3600, max(60, expires)))
    def deleteEvidence(self, prefix):
        for page in self.client.get_paginator("list_objects_v2").paginate(Bucket=self.bucket, Prefix=prefix):
            objects = [{"Key": item["Key"]} for item in page.get("Contents", [])]
            if objects:
                self.client.delete_objects(Bucket=self.bucket, Delete={"Objects": objects})

class WasabiStorageProvider(S3StorageProvider):
    """Server-side adapter, optional. Requires private bucket + tested lifecycle configuration."""
    def __init__(self, bucket, endpoint, region, access_key, secret_key):
        from urllib.parse import urlparse
        url = urlparse(endpoint)
        if url.scheme != "https" or not url.hostname or not url.hostname.endswith(".wasabisys.com"):
            raise ValueError("Use the official regional Wasabi HTTPS endpoint")
        super().__init__(bucket, boto3.client("s3", endpoint_url=endpoint, region_name=region,
            aws_access_key_id=access_key, aws_secret_access_key=secret_key, config=Config(signature_version="s3v4")))

class EmailProvider(ABC):
    @abstractmethod
    def send(self, recipient, subject, text): pass

class AmazonSesProvider(EmailProvider):
    def __init__(self, sender, client=None):
        self.sender, self.client = sender, client or boto3.client("sesv2")
    def send(self, recipient, subject, text):
        return self.client.send_email(FromEmailAddress=self.sender, Destination={"ToAddresses": [recipient]},
            Content={"Simple": {"Subject": {"Data": subject, "Charset": "UTF-8"},
                "Body": {"Text": {"Data": text, "Charset": "UTF-8"}}}})["MessageId"]
