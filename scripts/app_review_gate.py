#!/usr/bin/env python3
"""Check navigation declarations and the bundled help in every shipped language."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java'
res = root / 'app/src/main/res'
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
names = {node.attrib[android + 'name'] for node in manifest.findall('application/activity')}
errors = []
classes = {}
for file in src.rglob('*.kt'):
    text = file.read_text()
    package = re.search(r'^package\s+([\w.]+)', text, re.M)
    if not package:
        continue
    for name in re.findall(r'^class\s+(\w+Activity)\b', text, re.M):
        classes[package[1] + '.' + name] = file
for name, file in classes.items():
    short = name.removeprefix('com.nexvary.recorder')
    if name not in names and short not in names:
        errors.append(f'Activity missing from manifest: {file.relative_to(root)}')
for activity in manifest.findall('application/activity'):
    name = activity.attrib[android + 'name']
    if name != '.MainActivity' and activity.attrib.get(android + 'exported') != 'false':
        errors.append(f'Internal page is exported: {name}')
def strings(folder):
    return {x.attrib['name']: ''.join(x.itertext()) for x in ET.parse(folder / 'strings.xml').getroot().findall('string')}
base = strings(res / 'values')
help_keys = {key for key in base if key.startswith(('guide_', 'more_'))}
assert len([key for key in help_keys if key.startswith('guide_q_')]) == 12
for locale in ('values', 'values-ar', 'values-de', 'values-es', 'values-fa', 'values-fr', 'values-it', 'values-ru', 'values-tr', 'values-ur'):
    local = strings(res / locale)
    for key in help_keys:
        if not local.get(key, '').strip():
            errors.append(f'{locale}: missing help text {key}')
        if '%1$d' in base[key] and '%1$d' not in local.get(key, ''):
            errors.append(f'{locale}: missing results count format in {key}')
if errors:
    raise SystemExit('\n'.join(errors))
print(f'Page/help review passed: {len(classes)} activity classes, 12 FAQ topics, 10 languages; internal pages unexported.')
