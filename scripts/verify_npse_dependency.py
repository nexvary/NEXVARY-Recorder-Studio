#!/usr/bin/env python3
"""Verify reviewed binary dependencies before Gradle executes; never downloads."""
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1] / "third_party/npse"
manifest = json.loads((ROOT / "SOURCE.json").read_text())
assert manifest["schema"] == 1 and manifest["version"] == "0.1.0-alpha01"
assert manifest["sourceCommit"] == "0908a3363401b2ccba4292fa82a27062ab6c908f"
actual = {p.relative_to(ROOT).as_posix() for p in (ROOT / "maven").rglob("*") if p.is_file()}
assert actual == set(manifest["files"]) and len(actual) == 4, "Unexpected or missing Maven artifact"
for name, digest in manifest["files"].items():
    path = ROOT / name
    assert path.resolve().is_relative_to(ROOT.resolve())
    assert hashlib.sha256(path.read_bytes()).hexdigest() == digest, f"NPSE checksum mismatch: {name}"
    if path.suffix == ".pom":
        xml = ET.parse(path).getroot()
        assert xml.find("{*}groupId").text == "com.nexvary.npse"
        assert xml.find("{*}version").text == manifest["version"]
        assert not xml.findall(".//{*}repositories"), "POM repository redirect forbidden"
    elif path.suffix == ".aar":
        with zipfile.ZipFile(path) as archive:
            assert "DiagnosticsService" in archive.read("proguard.txt").decode()
            assert "assets/NPSE-LICENSE.txt" in archive.namelist()
            assert "assets/Shizuku-API-LICENSE.txt" in archive.namelist()
    elif path.suffix == ".jar":
        with zipfile.ZipFile(path) as archive:
            assert "META-INF/LICENSE" in archive.namelist()
print("Reviewed NPSE Maven dependency: 4 checksums, POMs, notices and R8 rules verified")
