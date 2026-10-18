#!/usr/bin/env python3
"""Isolated native equipment HUD acceptance. Only run after root confirms module build stable."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,time,uuid,zipfile
B=Path(__file__).resolve().parent
S=B.parent/'source'
D=B.parent/'performance-qa/runtime'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--minecraft',choices=['26.1','26.1.1','26.1.2'],default='26.1.2')
parser.add_argument('--module',type=Path,help='Exact stable root-built companion module to copy; default source/app target')
parser.add_argument('--expected-sha256',help='Require the exact stable root-built module hash')
parser.add_argument('--java',type=Path,default=B.parent/'runtime-acceptance/runtimes/zulu-25-mac-aarch64/zulu25.36.205-ca-jre25.0.4.1-macosx_aarch64/Contents/Home/bin/java')
args=parser.parse_args();V=args.minecraft
assert hashlib.sha256(args.java.read_bytes()).hexdigest()=='038e856d3c7b8307152b5ea440f548f74fad8e0acd3d9615983173bf451290d4','Expected unchanged accepted vendor Zulu25 executable'
R=B/('transitions-'+V+'-'+time.strftime('%Y%m%dT%H%M%SZ',time.gmtime()));R.mkdir()
release=json.loads((S/'companion/dependencies.lock.json').read_text())['release']
original=args.module or S/'app'/('KapKan-'+release+'-mc'+V+'-fabric.jar')
mod=R/original.name;shutil.copy2(original,mod);sha=hashlib.sha256(mod.read_bytes()).hexdigest()
if args.expected_sha256:assert sha==args.expected_sha256,'Module differs from stable build hash'
CP=(D/(V+'-classpath.txt')).read_text().strip();classes=R/'probe-classes';classes.mkdir()
shutil.copy2(B/'EquipmentTransitionsProbe.java',R/'EquipmentTransitionsProbe.java')
subprocess.run(['/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/javac','--release','25','-proc:none','-cp',CP+os.pathsep+str(mod),'-d',str(classes),str(B/'EquipmentTransitionsProbe.java')],check=True)
probe=R/'equipment-hud-qa-only.jar'
with zipfile.ZipFile(probe,'w',zipfile.ZIP_DEFLATED) as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'kapkan-equipment-hud-qa-only','version':'1.0.0','environment':'client','mixins':['equipment-hud-qa.mixins.json']}))
 z.writestr('equipment-hud-qa.mixins.json',json.dumps({'required':True,'package':'gg.kapkan.hudqa','compatibilityLevel':'JAVA_25','client':['EquipmentTransitionsProbe'],'injectors':{'defaultRequire':1}}))
ifmods=list((S/'performance/mods').glob('ImmediatelyFast*.jar'));assert len(ifmods)==1
ifmod=R/ifmods[0].name;shutil.copy2(ifmods[0],ifmod)
assert hashlib.sha512(ifmod.read_bytes()).hexdigest()=='b665037aee8e76e330f068f640b340b97ce8c0c0e940dfccda8a5b00f772dfa9b2d20594bb950e4f2595b359ad3c27190a4d998da28d4507a9747ab507980d11'
game=R/'game';game.mkdir()
(game/'options.txt').write_text('version:4790\nonboardAccessibility:false\nrenderDistance:4\nsimulationDistance:4\nmaxFps:120\nenableVsync:false\nguiScale:2\npauseOnLostFocus:false\nfullscreen:false\nsoundCategory_master:0.0\ntutorialStep:none\n')
(game/'client.properties').write_text('onboardingShown=true\narmor=true\nfps=false\ncoordinates=false\nkeystrokes=false\nclicks=false\neffects=false\ntoggleSprint=false\nsprintIndicator=false\n')
(game/'presence.properties').write_text('enabled=false\nshowOwn=false\n')
raw=bytearray(hashlib.md5(b'OfflinePlayer:QA_Bench').digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
meta=json.loads((D/(V+'-metadata.json')).read_text());injection=[mod,probe,ifmod]
command=[str(args.java),'-XstartOnFirstThread','-Xms512m','-Xmx2300m','-Dkapkan.qa.version='+V,'-Dfabric.addMods='+os.pathsep.join(map(str,injection)),'-Dkapkan.client.config='+str(game/'client.properties'),'-Dkapkan.presence.config='+str(game/'presence.properties'),'-cp',CP,'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','QA_Bench','--uuid',str(uuid.UUID(bytes=bytes(raw))),'--accessToken','0','--version',V,'--versionType','release','--gameDir',str(game),'--assetsDir',str(D/'assets'),'--assetIndex',meta['assetIndex']['id'],'--width','1280','--height','720']
record={'minecraft':V,'moduleSha256':sha,'javaExecutableSha256':hashlib.sha256(args.java.read_bytes()).hexdigest(),'runtime':'verified unmodified Zulu25 JRE25.0.4.1, macOS arm64 native OpenGL, Fabric0.19.5','scope':'Private fresh flat world, actual removal/swap/cache/empty-editor transitions. No timing, UI automation or installed-app writes.','injected':[{'file':p.name,'sha512':hashlib.sha512(p.read_bytes()).hexdigest()} for p in injection]}
(R/'run.json').write_text(json.dumps(record,indent=2)+'\n')
with (R/'process.log').open('w') as log:
 p=subprocess.Popen(command,cwd=game,stdout=log,stderr=subprocess.STDOUT);print('Started private equipment HUD QA',V,p.pid,flush=True)
 try:
  end=time.time()+300
  while time.time()<end:
   if (game/'hud-qa-failed.txt').exists():raise RuntimeError((game/'hud-qa-failed.txt').read_text())
   if (game/'hud-qa-pass.json').exists():
    data=json.loads((game/'hud-qa-pass.json').read_text());assert data['status']=='PASS'
    record['result']=data;record['status']='PASS_VISUAL_REVIEW_PENDING';(R/'PASS.json').write_text(json.dumps(record,indent=2)+'\n')
    print('PASS actual inventory/HUD transitions; inspect empty gameplay/editor screenshots:',R,flush=True);break
   if p.poll() is not None:raise RuntimeError('QA game exited before acceptance result; inspect '+str(R/'process.log'))
   time.sleep(1)
  else:raise TimeoutError('HUD QA timed out')
 finally:
  if p.poll() is None:
   try:p.wait(timeout=10)
   except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
