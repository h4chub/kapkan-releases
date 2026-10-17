from pathlib import Path
import subprocess,json,os,time,hashlib,uuid,zipfile,shutil,sys
B=Path(__file__).resolve().parent;S=B.parent/'source';D=B/'runtime';V=sys.argv[1];assert V in ['26.1','26.1.1','26.1.2']
R=B/('selected-smoke-'+V+'-'+time.strftime('%Y%m%dT%H%M%SZ',time.gmtime()));R.mkdir();CP=(D/(V+'-classpath.txt')).read_text().strip();JAVA=os.getenv('KAPKAN_QA_JAVA','/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java');JAVAC='/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/javac'
java_sha256=hashlib.sha256(Path(JAVA).read_bytes()).hexdigest();assert java_sha256=='038e856d3c7b8307152b5ea440f548f74fad8e0acd3d9615983173bf451290d4','Final smoke must use verified managed Zulu25 runtime'
java_version=subprocess.check_output([JAVA,'-version'],stderr=subprocess.STDOUT,text=True).strip()
shutil.copy2(B/'selected-subset-src/FrameTimeProbe.java',R/'FrameTimeProbe.java')
original=S/'app'/('KapKan-1.0.6-mc'+V+'-fabric.jar');mod=R/original.name;shutil.copy2(original,mod)
classes=R/'probe-classes';classes.mkdir();subprocess.run([JAVAC,'--release','25','-proc:none','-cp',CP+os.pathsep+str(mod),'-d',str(classes),str(B/'selected-subset-src/FrameTimeProbe.java')],check=True)
probe=R/'performance-qa-only.jar'
with zipfile.ZipFile(probe,'w',zipfile.ZIP_DEFLATED) as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'kapkan-performance-qa-only','version':'1.0.0','environment':'client','mixins':['performance-qa-only.mixins.json']}))
 z.writestr('performance-qa-only.mixins.json',json.dumps({'required':True,'package':'gg.kapkan.performanceqa','compatibilityLevel':'JAVA_25','client':['FrameTimeProbe'],'injectors':{'defaultRequire':1}}))
game=R/'game';game.mkdir();(game/'options.txt').write_text('version:4790\nonboardAccessibility:false\nrenderDistance:6\nsimulationDistance:5\nmaxFps:260\nenableVsync:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\n');(game/'client.properties').write_text('onboardingShown=true\n')
raw=bytearray(hashlib.md5(b'OfflinePlayer:QA_Bench').digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
candidates=[]
for candidate in (S/'performance/mods').glob('*.jar'):
 with zipfile.ZipFile(candidate) as z:
  if json.loads(z.read('fabric.mod.json'))['id']=='immediatelyfast':candidates.append(candidate)
assert len(candidates)==1,'Expected one selected IF candidate'
ifmod=R/candidates[0].name;shutil.copy2(candidates[0],ifmod)
assert hashlib.sha512(ifmod.read_bytes()).hexdigest()=='b665037aee8e76e330f068f640b340b97ce8c0c0e940dfccda8a5b00f772dfa9b2d20594bb950e4f2595b359ad3c27190a4d998da28d4507a9747ab507980d11'
injection=[mod,probe,ifmod];meta=json.loads((D/(V+'-metadata.json')).read_text())
command=[JAVA,'-XstartOnFirstThread','-Xms512m','-Xmx2300m','-Dkapkan.qa.smokeOnly=true','-Dkapkan.qa.version='+V,'-Dfabric.addMods='+os.pathsep.join(map(str,injection)),'-Dkapkan.client.config='+str(game/'client.properties'),'-Dkapkan.presence.config='+str(game/'presence.properties'),'-cp',CP,'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','QA_Bench','--uuid',str(uuid.UUID(bytes=bytes(raw))),'--accessToken','0','--version',V,'--versionType','release','--gameDir',str(game),'--assetsDir',str(D/'assets'),'--assetIndex',meta['assetIndex']['id'],'--width','1280','--height','720']
record={'minecraft':V,'moduleSha256':hashlib.sha256(mod.read_bytes()).hexdigest(),'injected':[{'file':p.name,'sha512':hashlib.sha512(p.read_bytes()).hexdigest()} for p in injection],'runtime':'macOS aarch64 native OpenGL, verified vendor Zulu25 JRE, Fabric0.19.5','javaVersion':java_version,'javaExecutableSha256':java_sha256,'scope':'Final selected IF-only subset, isolated fresh single-player world, no performance measurement','performanceMeasured':False};(R/'run.json').write_text(json.dumps(record,indent=2)+'\n')
with (R/'process.log').open('w') as log:
 p=subprocess.Popen(command,cwd=game,stdout=log,stderr=subprocess.STDOUT);print('Started smoke',V,p.pid,flush=True)
 try:
  end=time.time()+300
  while time.time()<end:
   if (game/'world-smoke.json').exists():
    data=json.loads((game/'world-smoke.json').read_text());assert data['status']=='PASS';record['result']=data;record['status']='PASS';(R/'PASS.json').write_text(json.dumps(record,indent=2)+'\n');print('PASS',V,str(R),flush=True);break
   if (game/'frame-failed.txt').exists():raise RuntimeError((game/'frame-failed.txt').read_text())
   if p.poll() is not None:raise RuntimeError('Client exited before world smoke; inspect '+str(R/'process.log'))
   time.sleep(1)
  else:raise TimeoutError('World smoke timeout')
 finally:
  if p.poll() is None:
   try:p.wait(timeout=10)
   except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
