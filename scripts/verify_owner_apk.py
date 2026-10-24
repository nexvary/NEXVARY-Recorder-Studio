#!/usr/bin/env python3
"""Fail closed before delivering a Guardian release APK to the owner.

Uses official Android SDK tools; no private signing material is needed.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

RELEASE_CERT = "ca4640c7afcd5522210b2b60aefe121be2bc4ece87a0b9ca4ee14425891be7f6"
PACKAGE = "com.nexvary.recorder"


def run(*command):
    return subprocess.run(command, check=True, text=True, capture_output=True,
                          timeout=90).stdout


def inspect(apk, apksigner, aapt2):
    signature = run(apksigner, "verify", "--verbose", "--print-certs", str(apk))
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)", signature)
    if certificates != [RELEASE_CERT]:
        raise ValueError("Not the original Guardian release certificate; do not deliver")
    badging = run(aapt2, "dump", "badging", str(apk))
    identity = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not identity or identity[1] != PACKAGE:
        raise ValueError("Not a Guardian APK")
    if re.search(r"^application-debuggable", badging, re.M):
        raise ValueError("Debuggable APK cannot be an owner release")
    return {"package": identity[1], "versionCode": int(identity[2]),
            "versionName": identity[3], "certificateSha256": RELEASE_CERT,
            "debuggable": False, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--previous", type=Path, required=True,
                        help="Previously delivered original release APK")
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--aapt2", required=True)
    args = parser.parse_args()
    current = inspect(args.apk, args.apksigner, args.aapt2)
    previous = inspect(args.previous, args.apksigner, args.aapt2)
    config = (Path(__file__).resolve().parents[1] / "app/build.gradle.kts").read_text()
    code = int(re.search(r"versionCode = (\d+)", config)[1])
    name = re.search(r'versionName = "([^"]+)"', config)[1]
    if current["versionCode"] != code or current["versionName"] != name:
        raise ValueError("APK version does not match the reviewed source")
    if current["versionCode"] <= previous["versionCode"]:
        raise ValueError("Owner update must have a higher versionCode")
    print(json.dumps({"current": current, "previous": previous,
                      "sameSigningIdentity": True,
                      "physicalInstall": "PENDING"}, indent=2))


if __name__ == "__main__":
    main()
