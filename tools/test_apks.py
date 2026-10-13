#!/usr/bin/env python3
"""Build real tampered/re-signed APK fixtures, then run the shared runtime verifier.
Requires two EXTERNAL disposable test keys. Never use a production key here.
"""
import argparse, hashlib, json, os, pathlib, shutil, struct, subprocess, zipfile, zlib
p=argparse.ArgumentParser();p.add_argument('--apk',required=True);p.add_argument('--approved-key',required=True);p.add_argument('--untrusted-key',required=True);p.add_argument('--pin',required=True);p.add_argument('--sdk',required=True);p.add_argument('--output',required=True)
a=p.parse_args();root=pathlib.Path(__file__).resolve().parent.parent;out=pathlib.Path(a.output).resolve();out.mkdir(parents=True,exist_ok=True)
java=pathlib.Path(os.environ['JAVA_HOME'])/'bin';bt=pathlib.Path(a.sdk)/'build-tools/35.0.0';env=dict(os.environ,LD_LIBRARY_PATH=str(bt/'lib64')+':'+os.environ.get('LD_LIBRARY_PATH',''))
classes=out/'classes';classes.mkdir(exist_ok=True);cp=str(bt/'lib/apksigner.jar')
subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(classes),str(root/'app/src/main/java/com/personal/memostarter/PackageIntegrity.java'),str(root/'tools/VerifyApk.java')],check=True)
shutil.copyfile(a.apk,out/'normal.apk')
for label,key,var in [('validation',a.approved_key,'MYMEMO_STORE_PASSWORD'),('untrusted',a.untrusted_key,'MYMEMO_UNTRUSTED_PASSWORD')]:
 subprocess.run([str(java/'keytool'),'-exportcert','-keystore',key,'-alias','release','-storepass:env',var,'-file',str(out/(label+'.der'))],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
with zipfile.ZipFile(a.apk) as z: base={i.filename:(i,z.read(i.filename)) for i in z.infolist()}
index='assets/integrity.index'
def signed(name, changes=None, remove=(), other=False):
 raw=out/(name+'.unsigned');aligned=out/(name+'.aligned');entries=dict(base)
 for n in remove: entries.pop(n)
 for n,v in (changes or {}).items():entries[n]=(base[n][0] if n in base else zipfile.ZipInfo(n),v)
 with zipfile.ZipFile(raw,'w') as z:
  for i,data in entries.values():z.writestr(i,data)
 subprocess.run([str(bt/'zipalign'),'-f','-P','16','4',str(raw),str(aligned)],check=True,env=env)
 subprocess.run([str(bt/'apksigner'),'sign','--ks',a.untrusted_key if other else a.approved_key,'--ks-key-alias','release','--ks-pass','env:MYMEMO_UNTRUSTED_PASSWORD' if other else 'env:MYMEMO_STORE_PASSWORD','--v1-signing-enabled','false','--v4-signing-enabled','false','--out',str(out/name),str(aligned)],check=True,stdout=subprocess.DEVNULL)
 raw.unlink();aligned.unlink()
dex=bytearray(base['classes.dex'][1]);assert b'CHECKING' in dex;dex=dex.replace(b'CHECKING',b'CHECKINX',1)
dex[12:32]=hashlib.sha1(dex[32:]).digest();dex[8:12]=struct.pack('<I',zlib.adler32(dex[12:])&0xffffffff)
signed('dex-modified.apk',{'classes.dex':dex})
signed('resigned.apk',other=True)
native=next(n for n in base if n.startswith('lib/') and n.endswith('.so'))
b=bytearray(base[native][1]);b[-1]^=1;signed('native-modified.apk',{native:b})
b=bytearray(base['resources.arsc'][1]);b[-1]^=1;signed('resource-modified.apk',{'resources.arsc':b})
signed('added-dex.apk',{'classes999.dex':base['classes.dex'][1]})
signed('removed-resource.apk',remove=('resources.arsc',))
b=bytearray(base[index][1]);b[-3]^=1;signed('index-modified.apk',{index:b})
signed('unsealed.apk',remove=(index,'assets/integrity.signature'))
# Corrupt a signed DEX entry without re-signing: Android APK signature verification must fail.
raw=bytearray(pathlib.Path(a.apk).read_bytes());entry=base['classes.dex'][0];off=entry.header_offset;namesize,extra=struct.unpack_from('<HH',raw,off+26);raw[off+30+namesize+extra+20]^=1;(out/'signature-broken.apk').write_bytes(raw)
cases={'normal.apk':'OK','dex-modified.apk':'CONTENT_HASH','resigned.apk':'SIGNER','native-modified.apk':'CONTENT_HASH','resource-modified.apk':'CONTENT_HASH','added-dex.apk':'ENTRY_SET','removed-resource.apk':'ENTRY_SET','index-modified.apk':'INDEX_SIGNATURE','unsealed.apk':'UNREADABLE','signature-broken.apk':'APK_SIGNATURE_INVALID'}
results=[]
for file,expected in cases.items():
 r=subprocess.run([str(java/'java'),'-cp',str(classes)+os.pathsep+cp,'VerifyApk',str(out/file),a.pin],capture_output=True,text=True)
 actual=r.stdout.strip();assert actual==expected,(file,expected,actual,r.stderr)
 results.append({'file':file,'expected':expected,'actual':actual,'pass':True,'sha256':hashlib.sha256((out/file).read_bytes()).hexdigest()})
 print(file,actual)
(out/'results.json').write_text(json.dumps({'method':'Actual APK signatures via Android apksig + shared PackageIntegrity JVM verifier; no emulator installation','cases':results},indent=2))
