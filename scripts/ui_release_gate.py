#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SRC = ROOT / "app/src/main/java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

errors = []
warnings = []
ANDROID = "{http://schemas.android.com/apk/res/android}"

required_locales = ["ar","en","tr","es","de","it","fr","ur","fa","ru"]
locale_paths = {
    "en": RES / "values/strings.xml",
    **{tag: RES / f"values-{tag}/strings.xml" for tag in required_locales if tag != "en"}
}

for tag, path in locale_paths.items():
    if not path.exists():
        errors.append(f"Missing locale file: {path}")
        continue
    try:
        ET.parse(path)
    except Exception as exc:
        errors.append(f"Invalid locale XML {path}: {exc}")

default_path = locale_paths["en"]
default_names = set()
if default_path.exists():
    root = ET.parse(default_path).getroot()
    default_names = {x.attrib["name"] for x in root.findall("string") if "name" in x.attrib}

for tag, path in locale_paths.items():
    if not path.exists():
        continue
    names = {x.attrib["name"] for x in ET.parse(path).getroot().findall("string") if "name" in x.attrib}
    missing = sorted(default_names - names)
    if missing:
        errors.append(f"Locale {tag} missing {len(missing)} strings: {', '.join(missing[:10])}")

layout_dir = RES / "layout"
layout_files = sorted(layout_dir.glob("activity_*.xml"))
if not layout_files:
    errors.append("No activity layouts found")

visible_widgets = {"TextView","Button","CheckBox","RadioButton","com.google.android.material.textfield.TextInputLayout"}

for path in layout_files:
    text = path.read_text(encoding="utf-8")
    if 'android:layoutDirection="rtl"' in text:
        errors.append(f"{path.name}: hard-coded RTL is forbidden; use locale direction")
    if 'android:gravity="end"' in text:
        errors.append(f"{path.name}: gravity=end found; use start for locale-aware text")
    if not text.lstrip().startswith("<?xml"):
        errors.append(f"{path.name}: malformed XML header")
    try:
        root = ET.fromstring(text)
    except Exception as exc:
        errors.append(f"{path.name}: XML parse failure: {exc}")
        continue

    if root.tag != "ScrollView":
        errors.append(f"{path.name}: root must be ScrollView for small-phone safety")

    if path.name != "activity_main.xml":
        ids = [el.attrib.get(ANDROID+"id","") for el in root.iter()]
        if "@+id/btnBack" not in ids:
            errors.append(f"{path.name}: missing btnBack")

    for el in root.iter():
        tag = el.tag.split("}")[-1]
        if tag in visible_widgets or tag.endswith("TextInputLayout"):
            for attr in ("text","hint"):
                value = el.attrib.get(ANDROID+attr)
                if value and not value.startswith("@string/") and not value.startswith("@null"):
                    if value.startswith("rtmp://") or value.startswith("rtmps://"):
                        continue
                    errors.append(f"{path.name}: hard-coded visible {attr}: {value[:60]}")

all_source = "
".join(p.read_text(encoding="utf-8") for p in SRC.rglob("*.kt"))
action_ids = set()
for path in layout_files:
    text = path.read_text(encoding="utf-8")
    for ident in re.findall(r'android:id="@\+id/((?:btn|card)[A-Za-z0-9_]+)"', text):
        action_ids.add(ident)

for ident in sorted(action_ids):
    if f"binding.{ident}.setOnClickListener" not in all_source:
        errors.append(f"Dead-action risk: {ident} has no binding listener")

for ident in ["themeHeader"]:
    if f"binding.{ident}.setOnClickListener" not in all_source:
        errors.append(f"Dead-action risk: {ident} has no binding listener")

expected_activities = [
    ".MainActivity",
    ".AboutActivity",
    ".LanguageActivity",
    ".screen.ScreenRecorderActivity",
    ".audio.VoiceRecorderActivity",
    ".media.AudioReplaceActivity",
    ".live.LiveBroadcastActivity",
]
manifest_text = MANIFEST.read_text(encoding="utf-8") if MANIFEST.exists() else ""
for activity in expected_activities:
    if f'android:name="{activity}"' not in manifest_text:
        errors.append(f"Manifest missing activity {activity}")

if 'android:localeConfig="@xml/locales_config"' not in manifest_text:
    errors.append("Manifest missing localeConfig")
if 'android:supportsRtl="true"' not in manifest_text:
    errors.append("Manifest must support RTL")

for required in [
    RES / "mipmap-anydpi-v26/ic_launcher.xml",
    RES / "mipmap-anydpi-v26/ic_launcher_round.xml",
    RES / "drawable/ic_launcher_foreground.xml",
]:
    if not required.exists():
        errors.append(f"Missing launcher icon resource: {required}")

about = ROOT / "app/src/main/java/com/nexvary/recorder/AboutActivity.kt"
if not about.exists():
    errors.append("AboutActivity.kt missing")
else:
    txt = about.read_text(encoding="utf-8")
    expected_links = {
        "https://nexvary.com/",
        "https://www.facebook.com/share/14p9krEn5ij/",
        "info@nexvary.com",
        "https://www.youtube.com/@NexvaryInc",
        "https://x.com/Nexvary",
    }
    for value in expected_links:
        if value not in txt:
            errors.append(f"About link missing: {value}")
    for url in re.findall(r'https://[^"\s]+', txt):
        parsed = urlparse(url)
        if parsed.scheme != "https" or not parsed.netloc:
            errors.append(f"Malformed HTTPS link: {url}")

for path in layout_files:
    text = path.read_text(encoding="utf-8")
    for fixed in re.findall(r'android:layout_(?:width|height)="(\d+)dp"', text):
        if int(fixed) > 720:
            errors.append(f"{path.name}: suspicious fixed dimension {fixed}dp")

if errors:
    print("UI RELEASE GATE: FAILED")
    for item in errors:
        print(f"ERROR: {item}")
    for item in warnings:
        print(f"WARN: {item}")
    sys.exit(1)

print("UI RELEASE GATE: PASSED")
print(f"Checked {len(layout_files)} activity layouts, {len(action_ids)} action controls and {len(required_locales)} locales.")
