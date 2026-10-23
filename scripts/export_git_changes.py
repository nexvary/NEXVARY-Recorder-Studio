"""Emit committed UTF-8 changes for authenticated Git data transport; no credentials."""
import base64
import json
import subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
paths=subprocess.check_output(['git','diff','--name-only','origin/feature/background-camera-recording','HEAD'],cwd=root,text=True).splitlines()
print(json.dumps([{'path':path,'mode':'100644','type':'blob','content':(root/path).read_text()} for path in paths if not path.endswith('.png')]))
