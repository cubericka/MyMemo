"""Host-side pipeline tests with simulated external tools, NOT APK/device tests."""
import argparse
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import protect_release as release

class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.project = self.root / 'project with spaces'
        self.project.mkdir()
        self.cert = b'simulated public certificate; no real signing performed'
        self.pin = hashlib.sha256(self.cert).hexdigest()
        self.key = self.root / 'private key.p12'
        self.key.write_bytes(b'test placeholder')
        self.java = self.root / 'jdk with spaces'
        self.sdk = self.root / 'SDK with spaces'
        for path in [self.java/'bin/java', self.java/'bin/javac', self.java/'bin/java.exe', self.java/'bin/javac.exe',
                     self.sdk/'build-tools/35.0.0/zipalign', self.sdk/'build-tools/35.0.0/zipalign.exe',
                     self.sdk/'build-tools/35.0.0/lib/apksigner.jar']:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.touch()
        policy = self.project / 'app/build/generated/source/buildConfig/release/com/personal/memostarter/BuildConfig.java'
        policy.parent.mkdir(parents=True)
        policy.write_text(f'RELEASE_CERT_PINS = "{self.pin}";\nVERSION_CODE = 342;\nAPPLICATION_ID = "com.personal.memostarter";', encoding='utf-8')
        self.apk = self.root / 'unsigned.apk'
        with zipfile.ZipFile(self.apk, 'w') as z:
            for n in ['classes.dex', 'classes2.dex', 'resources.arsc', 'AndroidManifest.xml', 'lib/arm64-v8a/a.so', 'res/raw/data.bin']:
                z.writestr(n, b'original ' + n.encode())
        self.a = argparse.Namespace(keystore=str(self.key),pin=self.pin,version=342,app_id='com.personal.memostarter',
                                   java_home=str(self.java),sdk=str(self.sdk),build_tools='35.0.0',apk=str(self.apk),
                                   output=str(self.root/'out/final.apk'),alias='release')
        self.calls = []
        self.fail = None
        self.tamper = False
        self.wrong_signer = False
        self.env = patch.dict(os.environ, {'MYMEMO_STORE_PASSWORD':'test-only-no-real-key'})
        self.env.start()
        self.addCleanup(self.env.stop)

    def runner(self, args, **kwargs):
        self.calls.append(args)
        self.assertNotIn('test-only-no-real-key', ' '.join(args))
        self.assertTrue(kwargs['check'])
        if 'SealIndex' in args:
            Path(args[-2]).write_bytes(b'simulated signature')
            Path(args[-1]).write_bytes(self.cert)
        elif 'sign' in args:
            shutil.copyfile(args[-1], args[args.index('--out')+1])
        elif 'verify' in args:
            if self.fail == 'verify':
                raise subprocess.CalledProcessError(1, args)
            if self.tamper:
                path = Path(args[-1])
                with zipfile.ZipFile(path) as z:
                    data = {n:z.read(n) for n in z.namelist()}
                data['classes.dex'] = b'changed during sign'
                with zipfile.ZipFile(path, 'w') as z:
                    for n, b in data.items(): z.writestr(n,b)
            return subprocess.CompletedProcess(args, 0, 'Signer #1 certificate SHA-256 digest: ' + ('0'*64 if self.wrong_signer else self.pin), '')
        elif Path(args[0]).name.startswith('zipalign'):
            if '-c' in args:
                if self.fail == 'alignment': raise subprocess.CalledProcessError(1,args)
            else: shutil.copyfile(args[-2], args[-1])
        return subprocess.CompletedProcess(args, 0, '', '')

    def test_order_and_verified_publish(self):
        with patch('sys.stdout', new=io.StringIO()): release.seal(self.a,self.project,self.runner)
        self.assertTrue(Path(self.a.output).exists())
        commands=['seal' if 'SealIndex' in c else 'verify' if 'verify' in c else 'sign' if 'sign' in c else 'align-check' if '-c' in c else 'align' if '-P' in c else 'compile' for c in self.calls]
        self.assertEqual(['compile','seal','align','sign','verify','align-check'],commands)
        self.assertFalse(list(Path(self.a.output).parent.glob('.mymemo-pending-*')))

    def test_failures_never_publish_final_apk(self):
        for stage in ('verify','alignment'):
            self.fail = stage
            with self.assertRaises(subprocess.CalledProcessError): release.seal(self.a,self.project,self.runner)
            self.assertFalse(Path(self.a.output).exists())
            self.assertFalse(list(Path(self.a.output).parent.glob('.mymemo-pending-*')))

    def test_wrong_final_signer_is_rejected(self):
        self.wrong_signer=True
        with self.assertRaisesRegex(ValueError,'Final APK signer'): release.seal(self.a,self.project,self.runner)
        self.assertFalse(Path(self.a.output).exists())

    def test_content_change_is_rejected(self):
        self.tamper=True
        with self.assertRaisesRegex(ValueError,'APK content changed'): release.seal(self.a,self.project,self.runner)
        self.assertFalse(Path(self.a.output).exists())

    def test_existing_output_is_preserved(self):
        out=Path(self.a.output);out.parent.mkdir();out.write_bytes(b'existing verified file')
        with self.assertRaisesRegex(ValueError,'already exists'): release.seal(self.a,self.project,self.runner)
        self.assertEqual(b'existing verified file',out.read_bytes())
        self.assertEqual([],self.calls)

    def test_wrong_compiled_pin_stops_before_tools(self):
        self.a.pin='1'*64
        with self.assertRaisesRegex(ValueError,'not compiled'): release.seal(self.a,self.project,self.runner)
        self.assertEqual([],self.calls)

    def test_windows_tools_use_exe_and_jar(self):
        paths=release.tool_paths(self.java,self.sdk,windows=True)
        self.assertEqual('java.exe',paths['java'].name)
        self.assertEqual('zipalign.exe',paths['zipalign'].name)
        self.assertEqual('apksigner.jar',paths['apksigner'].name)

    def test_index_covers_dex_native_resource_and_rejects_self_reference(self):
        with zipfile.ZipFile(self.apk) as z:
            raw=release.make_index(z,'com.personal.memostarter',342).decode()
        for name in ['classes2.dex','lib/arm64-v8a/a.so','res/raw/data.bin']:
            self.assertIn('\t'+name+'\n',raw)
        with zipfile.ZipFile(self.apk,'a') as z:z.writestr(release.INDEX,b'old')
        with zipfile.ZipFile(self.apk) as z:
            with self.assertRaisesRegex(ValueError,'unsealed'):release.make_index(z,'com.personal.memostarter',342)

if __name__ == '__main__': unittest.main()
