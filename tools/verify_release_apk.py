# SPDX-License-Identifier: GPL-3.0-only
# Verify actual formal product, ZIP, ELF dependencies, four ABIs and Android 14 signature.
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile

apk = Path(sys.argv[1])
sdk = Path(r'C:\Users\Administrator\AppData\Local\Android\Sdk')
tools = sdk / 'build-tools/36.0.0'
os.environ['JAVA_HOME'] = r'C:\Program Files\Android\Android Studio\jbr'
os.environ['PATH'] = os.environ['JAVA_HOME'] + r'\bin;' + os.environ['PATH']
expected = {'armeabi-v7a': (1, 40), 'arm64-v8a': (2, 183), 'x86': (1, 3), 'x86_64': (2, 62)}
triples = dict(zip(expected, ['arm-linux-androideabi', 'aarch64-linux-android', 'i686-linux-android', 'x86_64-linux-android']))
stubs = sdk / 'ndk/28.0.13004108/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib'
result = {'apk': str(apk), 'size': apk.stat().st_size, 'sha256': hashlib.file_digest(apk.open('rb'), 'sha256').hexdigest(), 'abis': {}}

def run(args):
    p = subprocess.run([str(a) for a in args], capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert p.returncode == 0, p.stdout + p.stderr
    return p.stdout

def elf_details(data):
    assert data[:4] == b'\x7fELF' and data[5] == 1
    cls = data[4]
    machine = struct.unpack_from('<H', data, 18)[0]
    if cls == 2:
        phoff = struct.unpack_from('<Q', data, 32)[0]
        phsize, phnum = struct.unpack_from('<HH', data, 54)
    else:
        phoff = struct.unpack_from('<I', data, 28)[0]
        phsize, phnum = struct.unpack_from('<HH', data, 42)
    loads, dynamic, aligns = [], None, []
    for i in range(phnum):
        raw = struct.unpack_from('<IIQQQQQQ' if cls == 2 else '<IIIIIIII', data, phoff + i * phsize)
        if cls == 2:
            typ, _, off, va, _, size, _, align = raw
        else:
            typ, off, va, _, size, _, _, align = raw
        if typ == 1:
            loads.append((va, off, size))
            aligns.append(align)
        elif typ == 2:
            dynamic = (off, size)
    needed, strings = [], None
    if dynamic:
        step = 16 if cls == 2 else 8
        for off in range(dynamic[0], sum(dynamic), step):
            tag, value = struct.unpack_from('<qQ' if cls == 2 else '<iI', data, off)
            if tag == 0:
                break
            if tag == 1:
                needed.append(value)
            if tag == 5:
                strings = next(fileoff + value - va for va, fileoff, size in loads if va <= value < va + size)
    names = [data[strings + n:].split(b'\0', 1)[0].decode() for n in needed] if needed else []
    return cls, machine, aligns, names

with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    native = {}
    for name in z.namelist():
        if name.startswith('lib/') and name.endswith('.so'):
            _, abi, soname = name.split('/')
            native.setdefault(abi, {})[soname] = z.read(name)
    assert set(native) == set(expected), list(native)
    first_names = set(next(iter(native.values())))
    for abi, libs in native.items():
        assert set(libs) == first_names, (abi, set(libs) ^ first_names)
        assert 'libnative-lib.so' in libs and 'libc++_shared.so' in libs
        missing = []
        page_alignment = []
        for name, data in libs.items():
            cls, machine, aligns, needed = elf_details(data)
            assert (cls, machine) == expected[abi], (abi, name, cls, machine)
            if cls == 2:
                assert min(aligns) >= 16384, (abi, name, aligns)
            page_alignment.extend(aligns)
            for dep in needed:
                if dep not in libs and not (stubs / triples[abi] / '23' / dep).is_file():
                    missing.append((name, dep))
        assert not missing, (abi, missing)
        result['abis'][abi] = {'libraryCount': len(libs), 'elfClass': expected[abi][0], 'machine': expected[abi][1], 'minLoadAlignment': min(page_alignment), 'missingDependencies': missing}
    result['zipIntegrity'] = 'PASS'
badging = run([tools / 'aapt2.exe', 'dump', 'badging', apk])
result['badging'] = [s for s in badging.splitlines() if re.match(r'package:|(?:minS|s)dkVersion:|targetSdkVersion:|native-code:|uses-feature:|uses-library', s)]
assert "minSdkVersion:'23'" in badging and "versionCode='11302'" in badging and "versionName='0.1.3-haoke.2'" in badging
assert "name='org.fcitx.fcitx5.android.haoke'" in badging
result['signatureAllSupportedApis'] = run([tools / 'apksigner.bat', 'verify', '--verbose', '--print-certs', apk])
assert 'a455f0d17ec4f31972c9a1dc7d9e3c8f5a7c31dd1e101765d25827780a917bf3' in result['signatureAllSupportedApis']
result['signatureAndroid14'] = run([tools / 'apksigner.bat', 'verify', '--min-sdk-version', '34', '--max-sdk-version', '34', '--verbose', apk])
result['zipalign16k'] = run([tools / 'zipalign.exe', '-c', '-P', '16', '4', apk]) or 'PASS'
result['runtimeAndroid14'] = 'NOT TESTED; no authorized Android14 physical device connected'
out = apk.parent / 'universal-verification.json' if len(sys.argv) < 3 else Path(sys.argv[2])
out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False, indent=2))
