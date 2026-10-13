#!/usr/bin/env python3
"""Seal unsigned R8 output and publish only after signature/alignment verification."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

INDEX = 'assets/integrity.index'
SIGNATURE = 'assets/integrity.signature'
OLD_PIN = 'aab6d1eccdaa88ded8b044e461eb273da40cbc0aae83a88f6e3ba341d04be3b1'

def require_file(path):
    path = Path(path).resolve()
    if not path.is_file():
        raise ValueError(f'Required file not found: {path}')
    return path

def tool_paths(java_home, sdk, build_tools='35.0.0', windows=None):
    windows = os.name == 'nt' if windows is None else windows
    suffix = '.exe' if windows else ''
    java_bin = Path(java_home) / 'bin'
    bt = Path(sdk) / 'build-tools' / build_tools
    return {name: require_file(path) for name, path in {
        'java': java_bin / ('java' + suffix),
        'javac': java_bin / ('javac' + suffix),
        'zipalign': bt / ('zipalign' + suffix),
        'apksigner': bt / 'lib' / 'apksigner.jar',
    }.items()}

def make_index(z, app_id, version):
    names = z.namelist()
    if len(names) != len(set(names)):
        raise ValueError('Duplicate ZIP entry names')
    if INDEX in names or SIGNATURE in names:
        raise ValueError('Input must be unsealed unsigned R8 APK')
    if not {'classes.dex', 'resources.arsc', 'AndroidManifest.xml'}.issubset(names):
        raise ValueError('Incomplete APK')
    entries = sorted(n for n in names if not n.endswith('/'))
    if any('\n' in n or '\r' in n or '\t' in n for n in entries):
        raise ValueError('Invalid ZIP entry name')
    if any(re.match(r'META-INF/.*\.(SF|RSA|DSA|EC)$', n, re.I) for n in entries):
        raise ValueError('Input contains old JAR signatures; build unsigned Release first')
    return ('MYMEMO-INTEGRITY-1\n' + app_id + '\t' + str(version) + '\n' +
            ''.join(hashlib.sha256(z.read(n)).hexdigest() + '\t' + n + '\n' for n in entries)).encode('utf-8')

def seal(a, project, runner=subprocess.run):
    project = Path(project).resolve()
    key = require_file(a.keystore)
    if key.is_relative_to(project):
        raise ValueError('Keystore must be outside the source project')
    if not re.fullmatch('[0-9a-f]{64}', a.pin) or a.pin == OLD_PIN:
        raise ValueError('Approved certificate SHA-256 required; preview key is forbidden')
    policy = require_file(project / 'app/build/generated/source/buildConfig/release/com/personal/memostarter/BuildConfig.java')
    source = policy.read_text(encoding='utf-8')
    pins = re.search(r'RELEASE_CERT_PINS = "([^"]*)"', source)
    if not pins or a.pin not in pins.group(1).split(','):
        raise ValueError('Signer pin was not compiled into this Release')
    if f'VERSION_CODE = {a.version};' not in source or f'APPLICATION_ID = "{a.app_id}";' not in source:
        raise ValueError('Release package/version policy mismatch')
    if not os.environ.get('MYMEMO_STORE_PASSWORD'):
        raise ValueError('Missing MYMEMO_STORE_PASSWORD; use build_release.py')
    apk = require_file(a.apk)
    out = Path(a.output).resolve()
    if out.exists() or out.with_suffix('.certificate.der').exists():
        raise ValueError('Output already exists; choose a new output path (no overwrite)')
    paths = tool_paths(a.java_home, a.sdk, a.build_tools)
    out.parent.mkdir(parents=True, exist_ok=True)
    # Staging is on the output filesystem. No final APK is exposed on tool failure.
    env = dict(os.environ)
    env['LD_LIBRARY_PATH'] = str(paths['zipalign'].parent / 'lib64') + os.pathsep + env.get('LD_LIBRARY_PATH', '')
    java = [str(paths['java']), '-Duser.language=en', '-Duser.country=US']
    signer = java + ['-jar', str(paths['apksigner'])]
    with tempfile.TemporaryDirectory(prefix='.mymemo-pending-', dir=out.parent) as temp:
        d = Path(temp)
        def run(args, capture=False):
            return runner([str(x) for x in args], check=True, env=env,
                          **({'capture_output': True, 'text': True, 'encoding': 'utf-8', 'errors': 'replace'} if capture else {}))
        run([paths['javac'], '-encoding', 'UTF-8', '-d', d, project / 'tools/SealIndex.java'])
        with zipfile.ZipFile(apk) as z:
            raw = make_index(z, a.app_id, a.version)
            (d / 'index').write_bytes(raw)
            run(java + ['-cp', str(d), 'SealIndex', str(key), a.alias, a.pin,
                        str(d / 'index'), str(d / 'signature'), str(d / 'signer.der')])
            if hashlib.sha256((d / 'signer.der').read_bytes()).hexdigest() != a.pin:
                raise ValueError('Exported certificate fingerprint mismatch')
            with zipfile.ZipFile(d / 'sealed.apk', 'w') as sealed:
                for info in z.infolist():
                    sealed.writestr(info, z.read(info.filename))
                sealed.writestr(INDEX, raw, compress_type=zipfile.ZIP_DEFLATED)
                sealed.writestr(SIGNATURE, (d / 'signature').read_bytes(), compress_type=zipfile.ZIP_STORED)
        run([paths['zipalign'], '-P', '16', '-f', '4', d / 'sealed.apk', d / 'aligned.apk'])
        run(signer + ['sign', '--ks', str(key), '--ks-type', 'PKCS12', '--ks-key-alias', a.alias,
                      '--ks-pass', 'env:MYMEMO_STORE_PASSWORD', '--key-pass', 'env:MYMEMO_STORE_PASSWORD',
                      '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true',
                      '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false',
                      '--out', str(d / 'signed.apk'), str(d / 'aligned.apk')])
        verification = run(signer + ['verify', '--verbose', '--print-certs', str(d / 'signed.apk')], capture=True)
        found = re.findall(r'Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)', verification.stdout)
        if len(found) != 1 or found[0].replace(':', '').lower() != a.pin:
            raise ValueError('Final APK signer differs from approved certificate')
        run([paths['zipalign'], '-c', '-P', '16', '4', d / 'signed.apk'])
        with zipfile.ZipFile(d / 'signed.apk') as z:
            if z.read(INDEX) != raw or z.read(SIGNATURE) != (d / 'signature').read_bytes():
                raise ValueError('Integrity metadata changed during signing')
            content = {n for n in z.namelist() if not n.endswith('/') and n not in (INDEX, SIGNATURE)}
            expected = {}
            for line in raw.decode('utf-8').splitlines()[2:]:
                digest, name = line.split('\t', 1)
                expected[name] = digest
            if content != set(expected) or any(hashlib.sha256(z.read(n)).hexdigest() != h for n, h in expected.items()):
                raise ValueError('APK content changed during alignment/signing')
        digest = hashlib.sha256((d / 'signed.apk').read_bytes()).hexdigest()
        # Final name is committed last, after all verification and public cert output.
        if out.exists():
            raise ValueError('Output was created by another process; refusing overwrite')
        out.with_suffix('.certificate.der').write_bytes((d / 'signer.der').read_bytes())
        os.replace(d / 'signed.apk', out)
    print('APK VERIFIED:', out)
    print('APK SHA-256:', digest)
    print('Device smoke testing is still required before customer distribution.')
    return out

def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('apk', 'output', 'keystore', 'alias', 'pin', 'sdk'):
        p.add_argument('--' + name, required=True)
    p.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    p.add_argument('--build-tools', default='35.0.0')
    p.add_argument('--app-id', default='com.personal.memostarter')
    p.add_argument('--version', type=int, default=342)
    a = p.parse_args()
    if not a.java_home:
        p.error('--java-home or JAVA_HOME is required')
    try:
        seal(a, Path(__file__).resolve().parent.parent)
    except (Exception, KeyboardInterrupt) as e:
        print(f'FAILED: {e}\nNo new verified APK was published by this attempt.', file=sys.stderr)
        return 1
    return 0

if __name__ == '__main__':
    sys.exit(main())
