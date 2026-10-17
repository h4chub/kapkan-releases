from pathlib import Path
import subprocess,json,os,time,hashlib,uuid,zipfile,shutil
from PIL import Image,ImageStat,UnidentifiedImageError
B=Path(__file__).resolve().parent;S=B.parent/'source';D=B/'runtime'
R=B/('ablation-'+time.strftime('%Y%m%dT%H%M%SZ',time.gmtime()));R.mkdir()
shutil.copy2(B/'FrameTimeProbe.java',R/'FrameTimeProbe.java')
shutil.copy2(Path(__file__),R/'run-ablation.py')
CP=(D/'classpath.txt').read_text().strip();JAVA='/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java';JAVAC=JAVA[:-4]+'javac'
MODULES={'105':S.parents[1]/'recovered105/KapKan-1.0.5-Sources/app/KapKan-1.0.5-mc26.1.2-fabric.jar','106':S/'app/KapKan-1.0.6-mc26.1.2-fabric.jar'}
PERF={}
for path in sorted((S/'performance/mods').glob('*.jar')):
 with zipfile.ZipFile(path) as z:PERF[json.loads(z.read('fabric.mod.json'))['id']]=path
assert set(PERF)=={'immediatelyfast','lithium','ferritecore'},PERF
(R/'modules').mkdir()
for name,path in list(MODULES.items()):
 copy=R/'modules'/(name+'-'+path.name);shutil.copy2(path,copy);MODULES[name]=copy
for name,path in list(PERF.items()):
 copy=R/'modules'/path.name;shutil.copy2(path,copy);PERF[name]=copy
classes=R/'probe-classes';classes.mkdir()
subprocess.run([JAVAC,'--release','25','-proc:none','-cp',CP+os.pathsep+str(MODULES['106']),'-d',str(classes),str(B/'FrameTimeProbe.java')],check=True)
probe=R/'performance-qa-only.jar'
with zipfile.ZipFile(probe,'w',zipfile.ZIP_DEFLATED) as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'kapkan-performance-qa-only','version':'2.0.0','environment':'client','mixins':['performance-qa-only.mixins.json']}))
 z.writestr('performance-qa-only.mixins.json',json.dumps({'required':True,'package':'gg.kapkan.performanceqa','compatibilityLevel':'JAVA_25','client':['FrameTimeProbe'],'injectors':{'defaultRequire':1}}))
meta=json.loads((D/'metadata.json').read_text());results=[];referenceState=None;reference=None;compatibility=None
currentHarnessSha256=hashlib.sha256((B/'FrameTimeProbe.java').read_bytes()).hexdigest()
def evidence_folder(receipt,result):
 candidate=receipt.parent/(str(result['ordinal']).zfill(2)+'-'+result['kind'])
 if candidate.is_dir():return candidate
 linked=json.loads(receipt.read_text()).get('openingControlReceipt')
 assert linked,'Missing evidence folder for reference result'
 return evidence_folder(Path(linked),result)
if os.getenv('KAPKAN_QA_REFERENCE'):
 reference=Path(os.environ['KAPKAN_QA_REFERENCE']).resolve()
 opening=json.loads(reference.read_text())
 prefix_allowed=os.getenv('KAPKAN_QA_REUSE_VALID_PREFIX')=='1'
 assert (opening['status']=='PASS' and opening['timingValid']) or (prefix_allowed and opening['status']=='FAILED'),'Reference must be valid or explicitly reuse its valid prefix'
 assert opening['runs'] and all(r['timingValid'] and r['activeFrames']==0 and r['sampleInterruptions']==0 for r in opening['runs']),'Reference prefix contains an invalid sample'
 if opening['harnessSha256']!=currentHarnessSha256:
  compatibility_path=Path(os.environ['KAPKAN_QA_PREP_COMPATIBILITY']).resolve()
  compatibility=json.loads(compatibility_path.read_text())
  assert compatibility['approvedPrepOnlyReuse'] is True,'Prep-only control reuse was not approved'
  assert compatibility['oldHarnessSha256']==opening['harnessSha256'] and compatibility['newHarnessSha256']==currentHarnessSha256,'Compatibility review does not match exact probes'
  shutil.copy2(compatibility_path,R/'prep-compatibility-review.json')
 results.extend(dict(r,harnessSha256=r.get('harnessSha256',opening['harnessSha256'])) for r in opening['runs'])
 first=evidence_folder(reference,results[0])
 referenceState=json.loads((first/'sample-start-state.json').read_text())
 for result in results:
  folder=evidence_folder(reference,result)
  assert referenceState==json.loads((folder/'sample-start-state.json').read_text())==json.loads((folder/'sample-end-state.json').read_text()),'Reference prefix scene mismatch'
  module=MODULES['105' if result['kind']=='baseline105' else '106']
  assert result['moduleSha256']==hashlib.sha256(module.read_bytes()).hexdigest(),'Reference module differs'
variants={'baseline105':('105',[]),'bare106':('106',[]),'if106':('106',['immediatelyfast']),'bundle106':('106',['immediatelyfast','lithium','ferritecore']),'lf106':('106',['lithium','ferritecore'])}
order=os.getenv('KAPKAN_QA_RUN_ORDER','baseline105,bare106,if106,bundle106,lf106,bare106,baseline105').split(',')
quiet=os.getenv('KAPKAN_QA_QUIET')=='1'
assert quiet,'A fresh quiet window must be confirmed before sampling'
summary={'runOrder':([r['kind'] for r in results]+order),'runs':results,'quietWindowConfirmed':quiet,'timingValid':False,'status':'RUNNING','harnessSha256':currentHarnessSha256,'limits':['One Mac, one fixed stationary scene and OpenGL only; this does not establish gains for general gameplay or other hardware.','Controls at both ends detect order drift; one sample per mod subset cannot establish causality with statistical confidence.','Every sample requires continuously unfocused, visible, non-minimized rendering, unchanged camera/player, time/weather, framebuffer/options and repeated entity-position checks.','Background OS load and temperatures are not controlled instruments; GLFW visibility/minimization is checked but macOS window occlusion is not instrumented.','Requested1280x720 produces a2560x1440 Retina framebuffer on this Mac.'],'host':{'system':os.uname().sysname,'release':os.uname().release,'machine':os.uname().machine,'hardware':subprocess.check_output(['sysctl','-n','machdep.cpu.brand_string'],text=True).strip(),'thermalBefore':subprocess.check_output(['pmset','-g','therm'],text=True).strip()}}
if reference:summary['openingControlReceipt']=str(reference);summary['openingControlHarnessSha256']=opening['harnessSha256']
if compatibility:summary['prepOnlyControlReuseReview']=compatibility
def save():
 (R/'comparison.json').write_text(json.dumps(summary,indent=2)+'\n')
 (B/'latest-ablation.txt').write_text(str(R)+'\n')
save()
for i,name in enumerate(order,len(results)+1):
 module_id,mod_ids=variants[name];game=R/(str(i).zfill(2)+'-'+name);game.mkdir();mod=MODULES[module_id];sha=hashlib.sha256(mod.read_bytes()).hexdigest()
 (game/'options.txt').write_text('version:4786\nonboardAccessibility:false\nrenderDistance:6\nsimulationDistance:5\nmaxFps:260\nenableVsync:false\nguiScale:2\npauseOnLostFocus:false\nfullscreen:false\nsoundCategory_master:0.0\ntutorialStep:none\n')
 (game/'client.properties').write_text('onboardingShown=true\n')
 raw=bytearray(hashlib.md5(b'OfflinePlayer:QA_Bench').digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
 injection=[mod,probe]+[PERF[k] for k in mod_ids]
 command=[JAVA,'-XstartOnFirstThread','-Xms512m','-Xmx2300m','-Dkapkan.qa.warmupSeconds=30','-Dkapkan.qa.sampleSeconds=30','-Dkapkan.qa.expectedFocus=false','-Dfabric.addMods='+os.pathsep.join(map(str,injection)),'-Dkapkan.client.config='+str(game/'client.properties'),'-Dkapkan.presence.config='+str(game/'presence.properties'),'-cp',CP,'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','QA_Bench','--uuid',str(uuid.UUID(bytes=bytes(raw))),'--accessToken','0','--version','26.1.2','--versionType','release','--gameDir',str(game),'--assetsDir',str(D/'assets'),'--assetIndex',meta['assetIndex']['id'],'--width','1280','--height','720']
 (game/'run.json').write_text(json.dumps({'ordinal':i,'kind':name,'moduleSha256':sha,'injectedMods':[{'filename':p.name,'sha512':hashlib.sha512(p.read_bytes()).hexdigest()} for p in injection],'runtime':'macOS aarch64 native OpenGL, OracleJava25.0.2, Fabric0.19.5','world':'fresh single-player normal terrain seed1234,200 fixed armor stands,time/weather frozen,ambient spawning/random ticks off','renderDistance':6,'simulationDistance':5,'maxFps':260,'vsync':False,'expectedFocus':False},indent=2)+'\n')
 with (game/'process.log').open('w') as log:
  p=subprocess.Popen(command,cwd=game,stdout=log,stderr=subprocess.STDOUT)
  print('Started isolated native client:',i,name,p.pid,flush=True)
  sceneChecked=False;sceneAnnounced=False
  try:
   end=time.time()+300
   while time.time()<end:
    if (game/'scene-ready.txt').exists() and not sceneAnnounced:
     print('KAPKAN_QA_SCENE_READY '+str(game),flush=True);sceneAnnounced=True
    if (game/'frame-failed.txt').exists():raise RuntimeError((game/'frame-failed.txt').read_text())
    scene=game/'screenshots/KapKan-scene-check.png'
    if scene.exists() and not sceneChecked:
     try:
      with Image.open(scene) as saved:image=saved.convert('L')
      brightness=ImageStat.Stat(image).mean[0]
     except (UnidentifiedImageError,OSError):pass
     else:
      if brightness<10:raise RuntimeError('Scene is too dark/occluded: '+str(brightness))
      sceneChecked=True;print('Scene brightness validation PASS',name,round(brightness,2),flush=True)
    if (game/'frame-results.json').exists():
     assert hashlib.sha256(mod.read_bytes()).hexdigest()==sha,'Module changed during QA'
     assert sceneChecked,'Scene screenshot was not validated'
     result=json.loads((game/'frame-results.json').read_text());result.update(kind=name,ordinal=i,moduleSha256=sha,performanceModules=mod_ids,harnessSha256=currentHarnessSha256)
     assert result['timingValid'] and result['activeFrames']==0 and result['sampleInterruptions']==0
     states=[json.loads((game/f'sample-{point}-state.json').read_text()) for point in ['start','end']]
     assert states[0]==states[1],'Final scene/camera/focus changed'
     if referenceState is not None:
      assert states[0]==referenceState,'Scene/camera/focus mismatch against external opening control'
     elif results:
      first=R/(str(results[0]['ordinal']).zfill(2)+'-'+results[0]['kind'])/'sample-start-state.json'
      assert states[0]==json.loads(first.read_text()),'Scene/camera/focus mismatch against first control'
     results.append(result);save();print(json.dumps(result),flush=True);break
    if p.poll() is not None:raise RuntimeError(name+' exited before result; inspect '+str(game/'process.log'))
    time.sleep(1)
   else:raise TimeoutError(name+' benchmark timed out')
  except BaseException as e:
   summary['status']='FAILED';summary['failure']=str(e);save();raise
  finally:
   if p.poll() is None:
    try:p.wait(timeout=10)
    except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
summary['timingValid']=all(x['timingValid'] for x in results);summary['status']='PASS';summary['host']['thermalAfter']=subprocess.check_output(['pmset','-g','therm'],text=True).strip();save();print('Ablation saved:',R,flush=True)
