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

    ids = [el.attrib.get(ANDROID+"id","") for el in root.iter()]

    if path.name == "activity_main.xml":
        if root.tag == "ScrollView":
            errors.append("activity_main.xml: main dashboard must be fixed and must not scroll")
        if "@+id/bottomNav" not in ids:
            errors.append("activity_main.xml: missing fixed bottomNav")
    else:
        if root.tag != "ScrollView":
            errors.append(f"{path.name}: root must be ScrollView for small-phone safety")
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

all_source = "\\n".join(p.read_text(encoding="utf-8") for p in SRC.rglob("*.kt"))
action_ids = set()
for path in layout_files:
    text = path.read_text(encoding="utf-8")
    for ident in re.findall(r'android:id="@\+id/((?:btn|card)[A-Za-z0-9_]+)"', text):
        action_ids.add(ident)

for ident in sorted(action_ids):
    if f"binding.{ident}.setOnClickListener" not in all_source:
        errors.append(f"Dead-action risk: {ident} has no binding listener")


# v0.4.0: all 12 runtime palettes must remain registered.
theme_manager = (SRC / "com" / "nexvary" / "recorder" / "ui" / "ThemeManager.kt").read_text(encoding="utf-8")
required_theme_names = [
    "theme_electric_blue", "theme_emerald", "theme_purple", "theme_amber",
    "theme_cyan", "theme_teal", "theme_lime", "theme_rose",
    "theme_crimson", "theme_orange", "theme_indigo", "theme_silver",
]
for theme_name in required_theme_names:
    if f"R.string.{theme_name}" not in theme_manager:
        errors.append(f"Missing registered runtime theme: {theme_name}")
if theme_manager.count("R.style.Theme_NexvaryRecorder_") < 12:
    errors.append("ThemeManager must register at least 12 runtime theme styles")


# v0.5.0 recorder-core invariants.
screen_activity = (SRC / "com" / "nexvary" / "recorder" / "screen" / "ScreenRecorderActivity.kt").read_text(encoding="utf-8")
screen_service = (SRC / "com" / "nexvary" / "recorder" / "screen" / "ScreenRecordService.kt").read_text(encoding="utf-8")
screen_layout = (layout_dir / "activity_screen_recorder.xml").read_text(encoding="utf-8")
manifest_text = MANIFEST.read_text(encoding="utf-8")

if "moveTaskToBack(" in screen_activity:
    errors.append("Screen recorder must not auto-minimize after projection starts")

for required_id in [
    "switchFloating", "switchTouches", "switchCamera",
    "groupCountdown", "btnStorage", "btnPauseResume"
]:
    if f'@+id/{required_id}' not in screen_layout:
        errors.append(f"Recorder control missing from UI: {required_id}")

for required_permission in [
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.WRITE_SETTINGS",
    "android.permission.CAMERA",
    "android.permission.FOREGROUND_SERVICE_CAMERA",
]:
    if required_permission not in manifest_text:
        errors.append(f"Recorder permission missing: {required_permission}")

for required_symbol in [
    "EXTRA_OUTPUT_TREE_URI", "EXTRA_SHOW_TOUCHES", "EXTRA_CAMERA",
    "ACTION_PAUSE", "ACTION_RESUME", "FloatingRecorderOverlay",
    "FloatingCameraOverlay",
]:
    if required_symbol not in screen_service:
        errors.append(f"Recorder core feature missing: {required_symbol}")

# v0.6.0 media tools invariants.
media_tools_activity = SRC / "com" / "nexvary" / "recorder" / "media" / "MediaToolsActivity.kt"
audio_converter = SRC / "com" / "nexvary" / "recorder" / "media" / "AudioFormatConverter.kt"
video_tools = SRC / "com" / "nexvary" / "recorder" / "media" / "VideoToolsEngine.kt"
media_layout = layout_dir / "activity_media_tools.xml"

for required_file in [media_tools_activity, audio_converter, video_tools, media_layout]:
    if not required_file.exists():
        errors.append(f"Media tools file missing: {required_file.name}")

if media_layout.exists():
    media_layout_text = media_layout.read_text(encoding="utf-8")
    for required_id in [
        "btnReplaceAudio", "btnChooseAudio", "btnConvertAudio",
        "btnChooseVideo", "btnTrimVideo", "btnConvertVideo"
    ]:
        if f'@+id/{required_id}' not in media_layout_text:
            errors.append(f"Media tools control missing: {required_id}")

if audio_converter.exists():
    text_audio = audio_converter.read_text(encoding="utf-8")
    for symbol in ["Target.WAV", "Target.M4A", "Target.OGG"]:
        if symbol not in text_audio:
            errors.append(f"Audio conversion target missing: {symbol}")

if video_tools.exists():
    text_video = video_tools.read_text(encoding="utf-8")
    for symbol in ["Container.MP4", "Container.THREE_GPP", "Container.WEBM", "fun trim("]:
        if symbol not in text_video:
            errors.append(f"Video tools feature missing: {symbol}")

project_text = "\n".join(
    p.read_text(encoding="utf-8", errors="ignore")
    for p in (ROOT / "app" / "src" / "main").rglob("*")
    if p.is_file() and p.suffix in {".kt", ".xml"}
)
for forbidden in ["FG Link", "fgmachines", "btnRecordFgLink", "FG_LINK_PACKAGE"]:
    if forbidden in project_text:
        errors.append(f"FG-specific recorder residue found: {forbidden}")

expected_activities = [
    ".MainActivity",
    ".AboutActivity",
    ".LanguageActivity",
    ".screen.ScreenRecorderActivity",
    ".audio.VoiceRecorderActivity",
    ".media.MediaToolsActivity",
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
