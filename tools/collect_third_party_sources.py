"""Collect fixed corresponding source/data, verifying prebuilder recipe hashes.

Usage: python tools/collect_third_party_sources.py PATH_TO_PINNED_PREBUILDER
The prebuilder checkout must already have its recursive submodules initialized.
No credentials, Git metadata, compiler caches or user files enter the archive.
"""
from pathlib import Path
import concurrent.futures, hashlib, json, re, subprocess, sys, tarfile, urllib.request

ROOT = Path(__file__).resolve().parents[1]
PREBUILDER = Path(sys.argv[1]).resolve()
OUT = ROOT / "third-party/corresponding-source"
OUT.mkdir(parents=True, exist_ok=True)
EXPECTED = "eb156443de3b387089e51f9bd19df4e3ddce1732"
def git(*args):
    return subprocess.check_output(["git","-C",str(PREBUILDER),*args],text=True,encoding="utf-8").strip()
assert git("rev-parse","HEAD") == EXPECTED
submodules = git("submodule","status","--recursive")
assert not any(line.startswith(("-","+","U")) for line in submodules.splitlines()), "Source submodule missing or changed"
cfg = {}
for line in (PREBUILDER/"build.cfg").read_text(encoding="utf-8").splitlines():
    match = re.fullmatch(r"([a-z0-9_]+)\s*=\s*(.+)",line)
    if match: cfg[match[1]] = match[2]
items=[]
for name in ["en_dict","lm_sc","pinyin_dict","table","py_stroke","py_table","jyutping_dict","jyutping_model"]:
    items.append((cfg[name],cfg["fcitx_data_url"]+cfg[name],cfg[name+"_sha256"]))
boost=f"boost-{cfg['boost_version']}-cmake.tar.xz"
items.append((boost,f"https://github.com/boostorg/boost/releases/download/boost-{cfg['boost_version']}/{boost}",cfg['boost_sha256']))
items.append(("chaizi.txt",cfg['chinese_addon_repo']+"chaizi.txt",cfg['chaizi_sha256']))
items.append(("bcprov-jdk18on-1.86-sources.jar","https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.86/bcprov-jdk18on-1.86-sources.jar",None))
def digest(path):
    with path.open("rb") as f:return hashlib.file_digest(f,"sha256").hexdigest()
def collect(item):
    name,url,expected=item
    path=OUT/name
    if not path.exists() or (expected and digest(path)!=expected):
        req=urllib.request.Request(url,headers={"User-Agent":"Haoke-Agent/1.0"})
        with urllib.request.urlopen(req,timeout=60) as response, path.open("wb") as file:
            assert response.geturl().startswith("https://")
            while chunk:=response.read(1024*1024):file.write(chunk)
    actual=digest(path)
    assert not expected or actual==expected,(name,"source hash mismatch")
    result={"file":name,"url":url,"bytes":path.stat().st_size,"sha256":actual,"recipeHashVerified":expected is not None}
    print(json.dumps({"collected":name,"bytes":result['bytes'],"verified":result['recipeHashVerified']}),flush=True)
    return result
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
    records=list(executor.map(collect,items))

archive=OUT/"prebuilder-eb156443-complete-source.tar.gz"
def source_only(info):
    if ".git" in Path(info.name).parts:return None
    info.uid=info.gid=0;info.uname=info.gname="";info.mtime=0
    return info
with tarfile.open(archive,"w:gz",compresslevel=6) as tar:
    tar.add(PREBUILDER,arcname="prebuilder",filter=source_only)
records.append({"file":archive.name,"url":f"https://github.com/fcitx5-android/prebuilder/tree/{EXPECTED}","bytes":archive.stat().st_size,"sha256":digest(archive),"includesRecursiveSubmodules":True})
manifest={"prebuiltCommit":"86ce2c95d42f1132746fbf60c278193aa1f4b758","prebuilderCommit":EXPECTED,"submodules":submodules.splitlines(),"archives":records,"note":"Native libraries are reproduced using the original pinned recipes and patches. Source snapshots have no Git metadata; initialize clean local Git baselines before invoking recipes that reset/apply patches."}
(OUT/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
print(json.dumps({"complete":True,"archives":len(records),"bytes":sum(x['bytes'] for x in records)}),flush=True)
