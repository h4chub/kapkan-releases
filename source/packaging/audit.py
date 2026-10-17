#!/usr/bin/env python3
from pathlib import Path
import zipfile,hashlib,json,struct,plistlib,argparse
parser=argparse.ArgumentParser()
parser.add_argument("--windows-only",action="store_true")
args=parser.parse_args()
from macho_layout import inspect_universal
root=Path(__file__).resolve().parents[1];out=root/'dist'
base=zipfile.ZipFile(root/'app/KapKan-Launcher-base.jar');final=zipfile.ZipFile(root/'app/KapKan-Launcher.jar')
names=final.namelist();assert len(names)==len(set(names))
assert not [n for n in names if n.startswith(('net/minecraft/','org/junit/')) or 'SelfTest' in n or 'SmokeTest' in n]
assert b'gg.kapkan.release.ReleaseMain' in final.read('META-INF/MANIFEST.MF')
assert b'KapKan-Build: client-release-1.0.5' in final.read('META-INF/MANIFEST.MF')
assert b'Implementation-Version: 1.0.5' in final.read('META-INF/MANIFEST.MF')
assert b'KapKan-Hotfix: fabric-dependency-recovery-3' in final.read('META-INF/MANIFEST.MF')
assert 'gg/kapkan/release/RetryDownloader.class' in names
assert b'gg/kapkan/release/RetryDownloader' in final.read('gg/kapkan/launcher/install/BaseGameInstallerService.class')
changed=[n for n in base.namelist() if n!='META-INF/MANIFEST.MF' and n in names and base.read(n)!=final.read(n)]
assets=[n for n in base.namelist() if n.endswith(('.png','.jpg','.icns'))]
assert all(base.read(n)==final.read(n) for n in assets)
allowed={
'gg/kapkan/launcher/launch/LaunchPlan.class',
'gg/kapkan/launcher/system/HardwareProbe.class',
'gg/kapkan/v23/KapKanAutoTunePolicy.class',
'gg/kapkan/launcher/launch/LaunchPlanBuilder.class',
'gg/kapkan/v23/KapKanV23Launcher$App$4.class',
'gg/kapkan/launcher/install/BaseGameInstallerService.class',
'gg/kapkan/launcher/launch/MinecraftProcessLauncher.class',
'gg/kapkan/launcher/launch/FabricDetector.class',
'gg/kapkan/launcher/launch/FabricInstallerService.class',
'gg/kapkan/launcher/launch/ModLoaderInstallerService.class',
'gg/kapkan/launcher/launch/VersionProfileLoader.class',
'gg/kapkan/v23/KapKanV23Launcher$MicrosoftAuthService.class',
}
assert set(changed)<=allowed,changed
packages=[out/'KapKan-Windows-1.0.5.zip'] if args.windows_only else [out/'KapKan-macOS-1.0.5.zip',out/'KapKan-Windows-1.0.5.zip']
for p in packages:
 with zipfile.ZipFile(p) as z:
  assert z.testzip() is None
  for shipped in ['KapKan-Launcher.jar','KapKan-1.0.5-mc26.1-fabric.jar','KapKan-1.0.5-mc26.1.1-fabric.jar','KapKan-1.0.5-mc26.1.2-fabric.jar']:
   entry=next(n for n in z.namelist() if n.endswith('/Resources/'+shipped) or n=='Resources/'+shipped)
   assert z.read(entry)==(root/'app'/shipped).read_bytes(), 'Stale shipped jar: '+shipped
  assert not any('PersonalPack' in n or n.endswith(('.ttf','.otf')) for n in z.namelist())
  if 'macOS' in p.name:
   exe=z.getinfo('KapKan.app/Contents/MacOS/KapKan'); assert (exe.external_attr>>16)&0o111
   fat=z.read(exe.filename);assert struct.unpack_from('>II',fat)==(0xcafebabe,2)
   layout=inspect_universal(fat)
   plist=plistlib.loads(z.read('KapKan.app/Contents/Info.plist'))
   assert plist['CFBundleShortVersionString']=='1.0.5'
   assert plist['CFBundleVersion']=='10014'
   assert b'1.0.5' in z.read('Make-DMG.command')
for p in [root/'build/KapKan.exe',out/'KapKan-1.0.5-Setup.exe']:
 b=p.read_bytes();assert b[:2]==b'MZ';off=struct.unpack_from('<I',b,0x3c)[0];assert b[off:off+4]==b'PE\0\0'
 assert struct.unpack_from('<H',b,off+4)[0]==0x8664
 assert struct.unpack_from('<H',b,off+24+68)[0]==2 # Windows GUI subsystem
report={'release':'1.0.5','build_id':'client-release-1.0.5','publication':'HOLD','changed_baseline_classes':changed,'preserved_image_assets':assets,'application_entries':len(names),'native_device_tests':'not performed','artifacts':{}}
for p in [out/'KapKan-macOS-1.0.5.zip',out/'KapKan-Windows-1.0.5.zip',out/'KapKan-1.0.5-Setup.exe']:
 if p.is_file() and p.suffix in ('.exe','.zip'):
  report['artifacts'][p.name]={'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
(out/'ARTIFACT-AUDIT.json').write_text(json.dumps(report,indent=2)+'\n')
(out/'SHA256SUMS.txt').write_text(''.join(f"{v['sha256']}  {k}\n" for k,v in report['artifacts'].items() if k in {'KapKan-macOS-1.0.5.zip','KapKan-1.0.5-Setup.exe'}))
print('Archive / binary / asset audit: PASS')
print('Changed existing classes:', '\n'.join(changed))
