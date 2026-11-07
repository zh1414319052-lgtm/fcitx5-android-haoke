"""Sign with an existing external product identity; never creates keys.

Windows DPAPI credentials stay in memory and child-only environment variables.
Private material must remain outside this checkout. No passwords are printed.
SPDX-License-Identifier: GPL-3.0-only
"""
import argparse
import base64
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

APP_ID = 'org.fcitx.fcitx5.android.haoke'
CERT = 'a455f0d17ec4f31972c9a1dc7d9e3c8f5a7c31dd1e101765d25827780a917bf3'
ROOT = Path(__file__).resolve().parents[1]

def unprotect(path):
    if os.name != 'nt':
        raise RuntimeError('This local signing identity uses Windows CurrentUser DPAPI.')
    class Blob(ctypes.Structure):
        _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]
    raw = base64.b64decode(path.read_bytes(), validate=True)
    buf = ctypes.create_string_buffer(raw)
    src = Blob(len(raw), ctypes.cast(buf, ctypes.POINTER(ctypes.c_ubyte)))
    dst = Blob()
    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    crypt.CryptUnprotectData.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    crypt.CryptUnprotectData.restype = wintypes.BOOL
    if not crypt.CryptUnprotectData(ctypes.byref(src), None, None, None, None, 1, ctypes.byref(dst)):
        raise RuntimeError('Cannot unlock existing signing credentials for this Windows user.')
    try:
        value = json.loads(ctypes.string_at(dst.data, dst.size))
        if not all(isinstance(value.get(k), str) and value[k] for k in ('store_pass', 'key_pass')):
            raise RuntimeError('Missing signing credential fields.')
        return value
    finally:
        kernel.LocalFree(dst.data)

def outside(path):
    path = Path(path).resolve(strict=True)
    if path == ROOT or ROOT in path.parents:
        raise RuntimeError('Signing keys/credentials must be external to the source checkout.')
    return path

def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('identity', 'input', 'output', 'sdk', 'java-home', 'version'):
        p.add_argument('--' + name, required=True)
    args = p.parse_args()
    meta = json.loads(outside(args.identity).read_text(encoding='utf-8-sig'))
    if meta['applicationId'] != APP_ID or meta['certificateSha256'] != CERT:
        raise RuntimeError('Established product identity mismatch; do not rotate keys here.')
    key = outside(meta['keystoreFile'])
    secret = unprotect(outside(meta['credentialsFile']))
    env = os.environ.copy()
    env['JAVA_HOME'] = args.java_home
    env['PATH'] = str(Path(args.java_home)/'bin') + os.pathsep + env['PATH']
    env['HAOKE_SIGN_STORE_PASS'] = secret['store_pass']
    env['HAOKE_SIGN_KEY_PASS'] = secret['key_pass']
    def run(command, binary=False):
        result = subprocess.run([str(x) for x in command], env=env, capture_output=True)
        if result.returncode:
            raise RuntimeError('Signing tool failed: ' + Path(str(command[0])).name + ' (output suppressed to protect credentials)')
        return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')
    tools = Path(args.sdk)/'build-tools/36.0.0'
    incoming, output = Path(args.input).resolve(strict=True), Path(args.output).resolve()
    if incoming == output or output.exists():
        raise RuntimeError('Signing requires a distinct, absent output file; existing releases are immutable.')
    public = run([Path(args.java_home)/'bin/keytool.exe', '-exportcert', '-keystore', key,
                  '-storetype', meta['storeType'], '-alias', meta['alias'], '-storepass:env', 'HAOKE_SIGN_STORE_PASS'], True)
    if hashlib.sha256(public).hexdigest() != CERT:
        raise RuntimeError('Actual key certificate differs from established release identity.')
    badging = run([tools/'aapt2.exe', 'dump', 'badging', incoming])
    for expected in (f"name='{APP_ID}'", "versionCode='11301'", f"versionName='{args.version}'", "minSdkVersion:'23'"):
        if expected not in badging:
            raise RuntimeError('Unsigned APK identity/version mismatch: ' + expected)
    if re.search(r'^application-debuggable', badging, re.M):
        raise RuntimeError('Refusing to sign a debuggable APK as formal.')
    run([tools/'zipalign.exe', '-c', '-P', '16', '4', incoming])
    run([tools/'apksigner.bat', 'sign', '--ks', key, '--ks-type', meta['storeType'], '--ks-key-alias', meta['alias'],
         '--ks-pass', 'env:HAOKE_SIGN_STORE_PASS', '--key-pass', 'env:HAOKE_SIGN_KEY_PASS', '--out', output, incoming])
    verified = run([tools/'apksigner.bat', 'verify', '--verbose', '--print-certs', output])
    if CERT not in verified:
        raise RuntimeError('Final APK certificate verification failed.')
    run([tools/'apksigner.bat', 'verify', '--min-sdk-version', '34', '--max-sdk-version', '34', output])
    run([tools/'zipalign.exe', '-c', '-P', '16', '4', output])
    with output.open('rb') as file:
        digest = hashlib.file_digest(file, 'sha256').hexdigest()
    print(json.dumps({'signedApk': str(output), 'applicationId': APP_ID, 'versionName': args.version,
                      'versionCode': 11301, 'bytes': output.stat().st_size, 'sha256': digest, 'certificateSha256': CERT}))

if __name__ == '__main__':
    main()
