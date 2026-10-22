import sys,json,pathlib,re,base64,os,shlex
import pyzipper
backup=pathlib.Path(sys.argv[1]); unlock=pathlib.Path(sys.argv[2]).read_text(); dst=pathlib.Path(sys.argv[3]);dst.mkdir(mode=0o700,exist_ok=True)
candidates=[]
for line in unlock.splitlines():
    candidates.extend([line.strip(),line.split(":",1)[-1].strip(),line.split("=",1)[-1].strip()])
candidates+=re.findall(r"[A-Za-z0-9_+/=-]{24,}",unlock)
entries=None
for password in dict.fromkeys(candidates):
    try:
        with pyzipper.AESZipFile(backup) as z:
            z.setpassword(password.strip(" \t\r\n\"'").encode())
            assert sum(i.file_size for i in z.infolist())<4_000_000
            entries=[(i.filename,z.read(i)) for i in z.infolist() if not i.is_dir()]
        break
    except Exception: pass
if entries is None: raise RuntimeError("Private backup could not be restored")
files=[];values={}
for i,(name,data) in enumerate(entries):
    p=dst/(str(i)+"_"+pathlib.PurePosixPath(name).name);p.write_bytes(data);p.chmod(0o600);files.append((name,p))
    try:
        text=data.decode("utf-8")
        try:
            v=json.loads(text)
            if isinstance(v,dict):values.update({k:str(x) for k,x in v.items() if k.startswith("ANDROID_")})
        except Exception:pass
        for line in text.splitlines():
            m=re.match(r"^(?:export\s+)?(ANDROID_[A-Z0-9_]+)\s*=\s*(.*)$",line.strip())
            if m:
                try: parts=shlex.split(m.group(2));value=parts[0] if len(parts)==1 else m.group(2).strip("'\"")
                except Exception:value=m.group(2).strip("'\"")
                values[m.group(1)]=value
    except UnicodeDecodeError:pass
key=next((p for n,p in files if n.lower().endswith((".p12",".jks",".keystore"))),None)
if key is None and values.get("ANDROID_KEYSTORE_BASE64"):
    key=dst/"release.p12";key.write_bytes(base64.b64decode(values["ANDROID_KEYSTORE_BASE64"]));key.chmod(0o600)
if key is None:raise RuntimeError("Keystore missing")
password=values.get("ANDROID_KEYSTORE_PASSWORD")
if password is None:
    p=next((p for n,p in files if pathlib.PurePosixPath(n).name=="store-password.txt"),None)
    if p:password=p.read_text().strip()
if not password:raise RuntimeError("Keystore credential missing")
store=dst/"store.txt";store.write_text(password);store.chmod(0o600)
keypass=dst/"key.txt";keypass.write_text(values.get("ANDROID_KEY_PASSWORD",password));keypass.chmod(0o600)
meta={"keystore":str(key),"storePasswordFile":str(store),"keyPasswordFile":str(keypass),"alias":values.get("ANDROID_KEY_ALIAS","guardian-release-2026")}
(dst/"private.json").write_text(json.dumps(meta));(dst/"private.json").chmod(0o600)
