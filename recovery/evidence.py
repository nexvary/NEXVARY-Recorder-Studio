import pathlib,sys,json,zipfile,xml.etree.ElementTree as E,collections,re
r=pathlib.Path(sys.argv[1]);out=r/"out";zdir=r/"zips";proof=out/"verification";proof.mkdir()
for api in [33,34,35]:
    with zipfile.ZipFile(zdir/("diag"+str(api)+".zip")) as z:
        names=[n for n in z.namelist() if pathlib.PurePosixPath(n).name.startswith("TEST-") and n.endswith(".xml")]
        assert len(names)==1;root=E.fromstring(z.read(names[0]))
        assert root.attrib["tests"]=="69"
        assert all(root.attrib.get(k,"0")=="0" for k in ["failures","errors","skipped"])
        (proof/("api-"+str(api)+".xml")).write_bytes(z.read(names[0]))
        for n in z.namelist():
            if n.endswith("measurements.json"):(proof/("api-"+str(api)+"-"+pathlib.PurePosixPath(n).name)).write_bytes(z.read(n))
for name,skips in [("mysql",0),("sqlite",2)]:
    with zipfile.ZipFile(zdir/(name+".zip")) as z:
        root=next(E.fromstring(z.read("portable-results.xml")).iter("testsuite"))
        assert root.attrib["tests"]=="39" and root.attrib["skipped"]==str(skips) and root.attrib["failures"]==root.attrib["errors"]=="0"
        (proof/(name+"-results.xml")).write_bytes(z.read("portable-results.xml"))
with zipfile.ZipFile(zdir/"quality.zip") as z:
    lint=E.fromstring(z.read("lint-results-debug.xml"));counts=collections.Counter(x.attrib.get("severity") for x in lint.findall("issue"));assert not counts.get("Error") and not counts.get("Fatal")
    html=z.read("tests/testDebugUnitTest/index.html").decode();assert re.findall(r'<div class="counter">(.*?)</div>',html)[:3]==["42","0","0"]
    for n in z.namelist():
        p=pathlib.PurePosixPath(n);assert not p.is_absolute() and ".." not in p.parts
        if not n.endswith("/"):
            dest=proof/"android-quality"/p;dest.parent.mkdir(exist_ok=True,parents=True);dest.write_bytes(z.read(n))
screens=out/"screenshots";screens.mkdir()
with zipfile.ZipFile(zdir/"screens35.zip") as z:
    png=[n for n in z.namelist() if n.endswith(".png")];assert len(png)==26
    for n in png:(screens/pathlib.PurePosixPath(n).name).write_bytes(z.read(n))
assert all((screens/n).is_file() for n in ["timer-presets-ar.png","settings-ar.png","safety-settings-ar.png","sharing-settings-ar.png"])
manifest=E.fromstring((r/"manifest.xml").read_text());ns="{http://schemas.android.com/apk/res/android}"
assert manifest.attrib["package"]=="com.nexvary.recorder" and manifest.attrib[ns+"versionCode"]=="19" and manifest.attrib[ns+"versionName"]=="0.11.1"
assert manifest.find("application").attrib.get(ns+"debuggable","false")=="false"
(proof/"results.json").write_text(json.dumps({"unit":42,"instrumentation":[{"api":a,"passed":69,"failed":0,"skipped":0} for a in [33,34,35]],"portable":{"mysql_passed":39,"sqlite_passed":37,"sqlite_skipped":2},"lint":dict(counts),"screenshots":26},indent=2))
