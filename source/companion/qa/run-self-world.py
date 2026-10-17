#!/usr/bin/env python3
"""Real isolated single-player badge check. Requires JDK25, Xvfb and OpenGL; excludes sound assets."""
from pathlib import Path
import concurrent.futures,hashlib,json,os,shutil,subprocess,time,urllib.request,uuid,zipfile
R=Path(__file__).resolve().parents[2];D=Path(os.getenv('KAPKAN_DEPS',R/'companion/build/deps'));Q=Path(os.getenv('KAPKAN_QA_OUT',R/'qa-build8'));Q.mkdir(exist_ok=True)
meta=json.loads((D/'26.1.2.json').read_text());a=meta['assetIndex'];index=D/'assets/indexes'/f"{a['id']}.json";index.parent.mkdir(parents=True,exist_ok=True)
def fetch(url,p,sha):
 if not p.exists() or hashlib.sha1(p.read_bytes()).hexdigest()!=sha:
  data=urllib.request.urlopen(url,timeout=60).read();assert hashlib.sha1(data).hexdigest()==sha;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 return p
fetch(a['url'],index,a['sha1']);objects=json.loads(index.read_text())['objects']
def asset(item):
 name,v=item
 if '/sounds/' in name:return
 h=v['hash'];fetch(f'https://resources.download.minecraft.net/{h[:2]}/{h}',D/'assets/objects'/h[:2]/h,h)
with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:list(pool.map(asset,objects.items()))
libs=[]
for l in meta['libraries']:
 allowed='rules' not in l
 for rule in l.get('rules',[]):
  if rule.get('os',{}).get('name','linux')=='linux' and 'features' not in rule:allowed=rule['action']=='allow'
 if allowed:
  item=l['downloads']['artifact'];libs.append(fetch(item['url'],D/'game-libs'/item['path'],item['sha1']))
cp=os.pathsep.join(map(str,[D/'fabric-loader.jar',*sorted((D/'fabric-libs').glob('*.jar')),D/'minecraft-26.1.2.jar',*libs]))
probe=os.getenv('KAPKAN_QA_PROBE','SelfWorldProbe')
classes=R/'companion/build'/('world-qa-'+probe);classes.mkdir(exist_ok=True)
result=subprocess.run(['javac','--release','25','-proc:none','-cp',cp+os.pathsep+str(R/'app/KapKan-Presence-26.1.2.jar'),'-d',str(classes),str(R/'companion/qa/src/gg/kapkan/qa'/f'{probe}.java')],capture_output=True,text=True)
if result.returncode:print(result.stderr);raise SystemExit(result.returncode)
mod=R/'companion/build/self-world-test-only.jar'
with zipfile.ZipFile(mod,'w') as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'kapkan-self-world-qa','version':'1.0.0','environment':'client','mixins':['self-world-qa.mixins.json']}))
 z.writestr('self-world-qa.mixins.json',json.dumps({'required':True,'package':'gg.kapkan.qa','compatibilityLevel':'JAVA_25','client':[probe],'injectors':{'defaultRequire':1}}))
run_id=uuid.uuid4().hex[:12]
game=R/'companion/build'/('world-game-'+probe+'-'+run_id)
game.mkdir();cfg=game/'presence.properties' # deliberately absent for default-on test
(game/'options.txt').write_text('version:4772\nonboardAccessibility:false\nrenderDistance:3\nsimulationDistance:5\nmaxFps:20\nenableVsync:false\nguiScale:2\npauseOnLostFocus:false\nfullscreen:false\nsoundCategory_master:0.0\ntutorialStep:none\n')
e=os.environ.copy();e.update(DISPLAY='127.0.0.1:89',LIBGL_ALWAYS_SOFTWARE='1',ALSOFT_DRIVERS='null',XDG_RUNTIME_DIR=str(game))
xvfb=os.getenv('XVFB_BIN') or shutil.which('Xvfb');assert xvfb,'Set XVFB_BIN or install Xvfb'
tested_sha=hashlib.sha256((R/'app/KapKan-Presence-26.1.2.jar').read_bytes()).hexdigest()
(Q/'run.json').write_text(json.dumps({'run_id':run_id,'game_directory':str(game),'module_sha256':tested_sha,'probe':probe},indent=2)+'\n')
with (Q/'singleplayer-display.log').open('w') as displaylog,(Q/'singleplayer.log').open('w') as log:
 display=subprocess.Popen([xvfb,':89','-screen','0','1280x800x24','-ac','-listen','tcp','-nolisten','unix'],env=e,stdout=displaylog,stderr=subprocess.STDOUT)
 client=None;recorder=None;service=None;service_url=''
 try:
  if probe=='CapeWorldProbe':
   script="from server import Store, PresenceServer; import sys; s=PresenceServer(('127.0.0.1',0),Store(sys.argv[1])); print(s.server_port,flush=True); s.serve_forever()"
   service=subprocess.Popen(['python3','-u','-c',script,str(game/'service.sqlite3')],cwd=R/'presence-service',stdout=subprocess.PIPE,stderr=log,text=True)
   service_url='http://127.0.0.1:'+str(int(service.stdout.readline().strip()))
   cfg.write_text('enabled=true\nshowOwn=true\nendpoint='+service_url+'\n')
  time.sleep(1)
  raw=bytearray(hashlib.md5(b'OfflinePlayer:BadgeCheck').digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
  cmd=['java','-Xms256m','-Xmx1800m','-Dfabric.addMods='+str(R/'app/KapKan-Presence-26.1.2.jar')+os.pathsep+str(mod),'-Dkapkan.launchSession='+str(uuid.uuid4()),'-Dkapkan.presence.config='+str(cfg),'-Dkapkan.client.config='+str(game/'client.properties'),'-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','BadgeCheck','--uuid',str(uuid.UUID(bytes=bytes(raw))),'--accessToken','0','--version','26.1.2','--versionType','release','--gameDir',str(game),'--assetsDir',str(D/'assets'),'--assetIndex',str(a['id']),'--width','1200','--height','780']
  cmd.insert(1,'-Dkapkan.qa.serviceUrl='+service_url)
  client=subprocess.Popen(cmd,cwd=game,env=e,stdout=log,stderr=subprocess.STDOUT)
  end=time.time()+240
  while time.time()<end:
   if (game/'record-start.txt').exists() and recorder is None:
    recorder=subprocess.Popen(['ffmpeg','-y','-loglevel','error','-f','x11grab','-framerate','20','-video_size','1280x800','-i','127.0.0.1:89.0','-t','17','-c:v','libx264','-preset','veryfast','-crf','24','-pix_fmt','yuv420p',str(Q/'KapKan-Cape-Motion.mp4')],stdout=subprocess.DEVNULL,stderr=log)
   if (game/'qa-failed.txt').exists():raise RuntimeError((game/'qa-failed.txt').read_text())
   if (game/'qa-passed.txt').exists():
    assert hashlib.sha256((R/'app/KapKan-Presence-26.1.2.jar').read_bytes()).hexdigest()==tested_sha, 'Module changed during QA'
    log.flush()
    assert 'COSMETICS_WORLD_PASS' in (Q/'singleplayer.log').read_text() if probe=='CosmeticsWorldProbe' else True
    shutil.copy2(game/'qa-passed.txt',Q/'singleplayer-result.txt')
    for f in (game/'screenshots').glob('KapKan*.png'):shutil.copy2(f,Q/f.name)
    print((game/'qa-passed.txt').read_text(),flush=True);break
   if client.poll() is not None:raise RuntimeError('Client exited before verification; see the selected QA output folder')
   time.sleep(1)
  else:raise RuntimeError('Single-player check timed out; see the selected QA output folder')
 finally:
  if recorder is not None and recorder.poll() is None:
   recorder.terminate();recorder.wait(timeout=5)
  if client is not None and client.poll() is None:
   try:client.wait(timeout=5)
   except subprocess.TimeoutExpired:client.terminate();client.wait(timeout=10)
  display.terminate();display.wait(timeout=5)
  if service is not None:service.terminate();service.wait(timeout=5)
