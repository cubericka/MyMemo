#!/usr/bin/env python3
"""Windows-friendly release entry point. No passwords in command lines or files."""
import argparse
from datetime import datetime
import getpass
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid
import warnings
from protect_release import tool_paths, require_file

PROJECT = Path(__file__).resolve().parent.parent

def main():
    if sys.version_info < (3, 10):
        raise ValueError('Python 3.10 or newer is required')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java-home', default=os.environ.get('JAVA_HOME') or str(Path(os.environ.get('ProgramFiles', 'C:/Program Files')) / 'Android/Android Studio/jbr'))
    p.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path(os.environ.get('LOCALAPPDATA', str(Path.home() / 'AppData/Local'))) / 'Android/Sdk'))
    p.add_argument('--keystore', default=str(Path.home() / 'MyMemoSigning/mymemo-release.p12'))
    p.add_argument('--alias', default='release')
    p.add_argument('--check', action='store_true', help='Check prerequisites only; no password prompt or build')
    a = p.parse_args()
    policy = json.loads((PROJECT / 'tools/release-policy.json').read_text(encoding='utf-8'))
    pin = policy['certificate_sha256']
    if not re.fullmatch('[0-9a-f]{64}', pin):
        raise ValueError('Invalid release-policy.json certificate fingerprint')
    gradle = (PROJECT / 'app/build.gradle.kts').read_text(encoding='utf-8')
    for expected in (f'applicationId = "{policy["application_id"]}"', f'versionCode = {policy["version_code"]}', f'versionName = "{policy["version_name"]}"'):
        if expected not in gradle:
            raise ValueError('Version/package mismatch between Gradle and release-policy.json')
    key = require_file(a.keystore)
    if key.is_relative_to(PROJECT):
        raise ValueError('Move the signing key OUTSIDE the project before building')
    paths = tool_paths(a.java_home, a.sdk, policy['build_tools'])
    require_file(Path(a.sdk) / 'platforms/android-36/android.jar')
    wrapper = require_file(PROJECT / 'gradle/wrapper/gradle-wrapper.jar')
    result = subprocess.run([str(paths['java']), '-version'], check=True, capture_output=True, text=True)
    version = re.search(r'version "(\d+)', result.stderr + result.stdout)
    if not version or int(version[1]) < 17:
        raise ValueError('JDK 17 or newer is required (Android Studio bundled JBR is recommended)')
    print('Java:', a.java_home)
    print('Android SDK:', a.sdk)
    print('Certificate SHA-256:', pin)
    print('Package:', policy['application_id'], 'versionCode:', policy['version_code'])
    print('Prerequisite check passed. Password and signing key have NOT yet been verified.', flush=True)
    if a.check:
        return 0
    env = dict(os.environ)
    env.pop('MYMEMO_STORE_PASSWORD', None)  # never expose signing password to Gradle
    env['JAVA_HOME'] = str(Path(a.java_home).resolve())
    env['ANDROID_HOME'] = env['ANDROID_SDK_ROOT'] = str(Path(a.sdk).resolve())
    env['MYMEMO_RELEASE_CERT_SHA256'] = pin
    # Direct JVM invocation avoids Windows .bat shell quoting and password leakage to Gradle.
    print('Building unsigned Release (downloads may be required)...', flush=True)
    subprocess.run([str(paths['java']), '-Duser.language=en', '-Duser.country=US', '-cp', str(wrapper),
                    'org.gradle.wrapper.GradleWrapperMain', '--no-daemon', 'clean', ':app:assembleRelease'],
                   cwd=PROJECT, env=env, check=True)
    apk = require_file(PROJECT / 'app/build/outputs/apk/release/app-release-unsigned.apk')
    with warnings.catch_warnings():
        warnings.simplefilter('error', getpass.GetPassWarning)
        password = getpass.getpass('Keystore password (hidden): ')
    if not password:
        raise ValueError('Empty password is not accepted')
    env['MYMEMO_STORE_PASSWORD'] = password
    password = None
    run_id = datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8]
    output = PROJECT / 'release-output' / run_id / 'MyMemo-3.4.2-release.apk'
    try:
        subprocess.run([sys.executable, str(PROJECT / 'tools/protect_release.py'), '--apk', str(apk),
                        '--output', str(output), '--keystore', str(key), '--alias', a.alias,
                        '--pin', pin, '--sdk', a.sdk, '--java-home', a.java_home,
                        '--build-tools', policy['build_tools'], '--app-id', policy['application_id'],
                        '--version', str(policy['version_code'])], cwd=PROJECT, env=env, check=True)
    finally:
        env.pop('MYMEMO_STORE_PASSWORD', None)
    print('\nBUILD AND SIGNATURE VERIFICATION SUCCEEDED')
    print('Install this APK for final device testing:', output)
    print('The preview app remains separate; its memos are not automatically imported.')
    return 0

if __name__ == '__main__':
    try:
        sys.exit(main())
    except (Exception, KeyboardInterrupt) as e:
        print(f'FAILED: {e}\nDo not distribute unsigned/debug/intermediate APKs.', file=sys.stderr)
        sys.exit(1)
