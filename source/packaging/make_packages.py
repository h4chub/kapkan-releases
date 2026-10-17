#!/usr/bin/env python3
"""Package previously compiled native binaries. Uses only Python's standard library."""
from pathlib import Path
import hashlib, json, plistlib, shutil, struct, zipfile, os, tempfile, argparse
parser=argparse.ArgumentParser()
parser.add_argument("--windows-only",action="store_true")
parser.add_argument("--unsigned-macos",action="store_true",help="development only: skip macOS signing and verification")
args=parser.parse_args()
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'dist'; OUT.mkdir(exist_ok=True)

def copy_performance(resources:Path)->None:
    # The unmodified third-party binaries, corresponding sources and licenses
    # travel together. Replace only a generated distribution's previous copy.
    destination=resources/'performance'
    if destination.exists():shutil.rmtree(destination)
    shutil.copytree(ROOT/'performance',destination)

def zipdir(folder:Path,out:Path, include_root:bool=False)->None:
    # A previous complete distribution stays readable until its replacement is
    # closed and verified. Never expose a partially written release archive.
    descriptor,filename=tempfile.mkstemp(prefix='package-',suffix='.part',dir=out.parent);os.close(descriptor);temporary=Path(filename)
    try:
        with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
            for f in sorted(folder.rglob('*')):
                if f.is_file():
                    name=f.relative_to(folder.parent if include_root else folder).as_posix()
                    info=zipfile.ZipInfo(name,(2026,10,16,0,0,0)); info.create_system=3
                    mode=0o755 if (f.suffix=='.command' or f.name=='KapKan' or f.suffix=='.exe') else 0o644
                    info.external_attr=(0o100000|mode)<<16; info.compress_type=zipfile.ZIP_DEFLATED
                    z.writestr(info,f.read_bytes())
        with zipfile.ZipFile(temporary) as check:
            if check.testzip() is not None:raise ValueError('Archive CRC verification failed')
        temporary.replace(out)
    finally:temporary.unlink(missing_ok=True)

if not args.windows_only:
    from macho_layout import universal
    from macos_permissions import MICROPHONE_PURPOSE, validate_microphone_support, sign_app
    
    mac=OUT/'KapKan-macOS-1.0.6'; app=mac/'KapKan.app'; contents=app/'Contents'
    (contents/'MacOS').mkdir(parents=True,exist_ok=True);res=contents/'Resources';res.mkdir(exist_ok=True)
    layout=universal([ROOT/'build/KapKan-darwin-amd64',ROOT/'build/KapKan-darwin-arm64'],contents/'MacOS/KapKan')
    (OUT/'MACHO-LAYOUT.json').write_text(json.dumps(layout,indent=2)+'\n')
    info={'CFBundleDisplayName':'KapKan','CFBundleExecutable':'KapKan','CFBundleIconFile':'KapKan.icns','CFBundleIdentifier':'gg.kapkan.launcher','CFBundleInfoDictionaryVersion':'6.0','CFBundleName':'KapKan','CFBundlePackageType':'APPL','CFBundleShortVersionString':'1.0.6','CFBundleVersion':'10015','LSApplicationCategoryType':'public.app-category.games','LSMinimumSystemVersion':'12.0','NSHighResolutionCapable':True}
    info['NSMicrophoneUsageDescription']=MICROPHONE_PURPOSE
    entitlements=ROOT/'packaging/KapKan.entitlements'
    validate_microphone_support(info,plistlib.loads(entitlements.read_bytes()))
    (contents/'Info.plist').write_bytes(plistlib.dumps(info));(contents/'PkgInfo').write_bytes(b'APPL????')
    shutil.copy2(entitlements,res/'KapKan.entitlements')
    for n in ['KapKan-Launcher.jar','KapKan-AppIcon.png','KapKan.icns','KapKan-ClientCore-1.21.11-Beta1.jar','KapKan-1.0.6-mc26.1-fabric.jar','KapKan-1.0.6-mc26.1.1-fabric.jar','KapKan-1.0.6-mc26.1.2-fabric.jar']:
        shutil.copy2(ROOT/'app'/n,res/n)
    shutil.copy2(ROOT/'packaging/Make-DMG.command',mac/'Make-DMG.command')
    (mac/'Make-DMG.command').chmod(0o755)
    shutil.copy2(ROOT/'README.md',mac/'READ-ME.md')
    shutil.copy2(ROOT/'QA-REPORT.md',mac/'QA-REPORT.md')
    shutil.copy2(ROOT/'BUILD-INFO.json',res/'BUILD-INFO.json')
    shutil.copy2(ROOT/'RELEASE-GATE.json',res/'RELEASE-GATE.json')
    copy_performance(res)
    (res/'licenses').mkdir(exist_ok=True);shutil.copy2(ROOT/'GO-LICENSE.txt',res/'licenses/Go.txt')
    if args.unsigned_macos:
        print('DEVELOPMENT ONLY: macOS application signature not verified')
    else:sign_app(app)
win=OUT/'KapKan-Windows-1.0.6';wr=win/'Resources';wr.mkdir(parents=True,exist_ok=True)
shutil.copy2(ROOT/'build/KapKan.exe',win/'KapKan.exe')
shutil.copy2(ROOT/'app/KapKan.ico',win/'KapKan.ico')
for n in ['KapKan-Launcher.jar','KapKan-AppIcon.png','KapKan-ClientCore-1.21.11-Beta1.jar','KapKan-1.0.6-mc26.1-fabric.jar','KapKan-1.0.6-mc26.1.1-fabric.jar','KapKan-1.0.6-mc26.1.2-fabric.jar']:
    shutil.copy2(ROOT/'app'/n,wr/n)
(wr/'licenses').mkdir(exist_ok=True);shutil.copy2(ROOT/'GO-LICENSE.txt',wr/'licenses/Go.txt')
shutil.copy2(ROOT/'README.md',win/'READ-ME.md');shutil.copy2(ROOT/'QA-REPORT.md',win/'QA-REPORT.md')
shutil.copy2(ROOT/'BUILD-INFO.json',wr/'BUILD-INFO.json')
shutil.copy2(ROOT/'RELEASE-GATE.json',wr/'RELEASE-GATE.json')
copy_performance(wr)
if not args.windows_only:zipdir(mac,OUT/'KapKan-macOS-1.0.6.zip')
zipdir(win,OUT/'KapKan-Windows-1.0.6.zip')
payload=ROOT/'native/cmd/installer/payload.zip'
zipdir(win,payload)
(ROOT/'native/cmd/installer/payload.sha256').write_text(hashlib.sha256(payload.read_bytes()).hexdigest()+'\n')
print(('Windows app' if args.windows_only else 'Mac and Windows apps')+' and installer payload packaged into',OUT)
