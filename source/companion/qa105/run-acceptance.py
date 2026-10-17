#!/usr/bin/env python3
"""Actual isolated Fabric dedicated server and two graphical client acceptance run.

TEST ONLY. Never bundled into the shipped mod/launcher. Offline local identities
exist exclusively on the loopback development server, not public servers.
"""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,time,uuid,zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'qa/1.0.5'
D=BASE/'runtime'
TC=Path(os.environ.get('KAPKAN_QA_TOOLCHAINS',ROOT.parents[1]/'toolchains105'))
JAVA=str(TC/'jdk25/bin/java');JAVAC=str(TC/'jdk25/bin/javac')
VERSION=os.getenv('KAPKAN_QA_VERSION','26.1.2')
MODE=os.getenv('KAPKAN_QA_MODE','acceptance')
PROBE='FeatureProbe' if MODE=='features' else 'UiInteractionProbe' if MODE=='ui' else 'SmokeProbe' if MODE=='smoke' else 'AcceptanceProbe'
DISPLAY_NUMBER=os.getenv('KAPKAN_QA_DISPLAY','105')
PORT=os.getenv('KAPKAN_QA_PORT','25578')
CLIENT_HEAP=os.getenv('KAPKAN_QA_CLIENT_HEAP','1400m')
SERVER_HEAP=os.getenv('KAPKAN_QA_SERVER_HEAP','1200m')
MOD=ROOT/'app'/f'KapKan-1.0.5-mc{VERSION}-fabric.jar'
assert MOD.exists(),str(MOD)
RUN=BASE/('actual-'+MODE+'-'+VERSION+'-'+time.strftime('%Y%m%dT%H%M%SZ',time.gmtime()))
RUN.mkdir(parents=True)
CP=(D/(VERSION+'-classpath.txt')).read_text().strip()
BUILD=RUN/'probe-classes';BUILD.mkdir()
subprocess.run([JAVAC,'--release','25','-proc:none','-cp',CP+os.pathsep+str(MOD),'-d',str(BUILD),str(ROOT/'companion/qa105'/(PROBE+'.java'))],check=True)
QAMOD=RUN/'kapkan105-qa-only.jar'
with zipfile.ZipFile(QAMOD,'w',zipfile.ZIP_DEFLATED) as z:
 for f in BUILD.rglob('*.class'):z.write(f,f.relative_to(BUILD))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'kapkan105-acceptance-qa','version':'1.0.0','environment':'client','mixins':['kapkan105-qa.mixins.json']}))
 z.writestr('kapkan105-qa.mixins.json',json.dumps({'required':True,'package':'gg.kapkan.qa105','compatibilityLevel':'JAVA_25','client':[PROBE],'injectors':{'defaultRequire':1}}))
MODSHA=hashlib.sha256(MOD.read_bytes()).hexdigest()
(RUN/'run.json').write_text(json.dumps({'minecraft':VERSION,'module':str(MOD),'moduleSha256':MODSHA,'java':JAVA,'runtime':'Linux Mesa llvmpipe software OpenGL / Xvfb','clients':1 if MODE in ('features','smoke','ui') else 2,'server':'Private localhost Fabric server; isolated offline QA identities','scope':'Actual client processes, no fixture players','serverPort':PORT,'display':DISPLAY_NUMBER,'clientHeap':CLIENT_HEAP,'serverHeap':SERVER_HEAP},indent=2)+'\n')
(BASE/'latest-actual-run.txt').write_text(str(RUN)+'\n')
processes=[];logs=[]
env=os.environ.copy();SYSROOT=TC/'sysroot';env.update(DISPLAY='127.0.0.1:'+DISPLAY_NUMBER,LD_LIBRARY_PATH=str(SYSROOT/'usr/lib/x86_64-linux-gnu'),LIBGL_ALWAYS_SOFTWARE='1',ALSOFT_DRIVERS='null')
def start(cmd,name,cwd,stdin=False):
 log=(RUN/(name+'.log')).open('w');logs.append(log)
 p=subprocess.Popen(cmd,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE if stdin else subprocess.DEVNULL,text=True)
 processes.append(p);return p

def commands(server,items):
 server.stdin.write('\n'.join(items)+'\n');server.stdin.flush()

def wait_server(server):
 end=time.time()+180
 while time.time()<end:
  text=(RUN/'server.log').read_text()
  if 'Done (' in text:return
  if server.poll() is not None:raise RuntimeError('Server exited '+str(server.returncode)+'; '+text[-3000:])
  time.sleep(1)
 raise RuntimeError('Server start timed out')

def wait_markers(current,marker,timeout=90):
 end=time.time()+timeout
 while time.time()<end:
  for role,p in current:
   failure=RUN/role/'qa-failed.txt'
   if failure.exists():raise RuntimeError(role+': '+failure.read_text())
   if p.poll() is not None:raise RuntimeError(role+' exited during '+marker)
  if all((RUN/r/marker).exists() for r,_ in current):return
  time.sleep(1)
 raise RuntimeError('Lifecycle transition timed out: '+marker)

def identity(name):
 b=bytearray(hashlib.md5(('OfflinePlayer:'+name).encode()).digest());b[6]=(b[6]&15)|48;b[8]=(b[8]&63)|128;return str(uuid.UUID(bytes=bytes(b)))

def clients(restart=False):
 out=[]
 meta=json.loads((D/(VERSION+'.json')).read_text())
 for role,name in ([('alpha','QA_Alpha')] if MODE in ('features','smoke','ui') else [('alpha','QA_Alpha'),('beta','QA_Beta')]):
  game=RUN/role;game.mkdir(exist_ok=True)
  if not restart:
   (game/'options.txt').write_text('version:4786\nonboardAccessibility:false\nrenderDistance:2\nsimulationDistance:5\nmaxFps:15\nenableVsync:false\nguiScale:2\npauseOnLostFocus:false\nfullscreen:false\nsoundCategory_master:0.0\ntutorialStep:none\n')
   (game/'presence.properties').write_text('enabled=true\nshowOwn=true\n')
  args=[JAVA,'-Xms256m','-Xmx'+CLIENT_HEAP,'-Dfabric.addMods='+str(MOD)+os.pathsep+str(QAMOD),'-Dkapkan.client.config='+str(game/'client.properties'),'-Dkapkan.presence.config='+str(game/'presence.properties'),'-Dkapkan.qa.features='+str(MODE=='features').lower(),'-Dkapkan.qa.serverPort='+PORT,'-Dkapkan.qa.version='+VERSION,'-Dkapkan.qa.role='+role,'-Dkapkan.qa.restart='+str(restart).lower(),'-Dkapkan.qa.peerDir='+str(RUN/('beta' if role=='alpha' else 'alpha')),'-cp',CP,'net.fabricmc.loader.impl.launch.knot.KnotClient','--username',name,'--uuid',identity(name),'--accessToken','0','--version',VERSION,'--versionType','release','--gameDir',str(game),'--assetsDir',str(D/'assets'),'--assetIndex',meta['assetIndex']['id'],'--width','1280','--height','800']
  out.append((role,start(args,role+('-restart' if restart else ''),game)))
 return out

def wait_clients(current,server,restart=False):
 end=time.time()+780;prepared=False
 while time.time()<end:
  if MODSHA!=hashlib.sha256(MOD.read_bytes()).hexdigest():raise RuntimeError('Production module changed during test; rerun against final module')
  text=(RUN/'server.log').read_text()
  if not prepared and all((RUN/r/'joined-ready.txt').exists() for r,_ in current):
   commands(server,['fill -10 60 -10 10 60 10 minecraft:stone','tp QA_Alpha 0 61 3 0 0','tp QA_Beta 0 61 0 0 0','time set day','weather clear','effect give QA_Alpha minecraft:speed 999999 1 true','effect give QA_Beta minecraft:night_vision 999999 0 true','item replace entity QA_Alpha armor.head with minecraft:diamond_helmet','item replace entity QA_Alpha armor.chest with minecraft:diamond_chestplate','item replace entity QA_Alpha armor.legs with minecraft:diamond_leggings','item replace entity QA_Alpha armor.feet with minecraft:diamond_boots'])
   prepared=True;print('Actual two clients joined; server equipment/effects/positions prepared',flush=True)
  for role,p in current:
   failure=RUN/role/'qa-failed.txt'
   if failure.exists():raise RuntimeError(role+': '+failure.read_text())
   if p.poll() is not None:raise RuntimeError(role+' client exited '+str(p.returncode))
  marker='restart-passed.txt' if restart else 'qa-passed.txt'
  if all((RUN/r/marker).exists() for r,_ in current):
   print(('Restart' if restart else 'Initial')+' actual-client checks passed',flush=True);return
  time.sleep(1)
 raise RuntimeError('Actual-client test timeout; inspect '+str(RUN))

def wait_ui(current,server,restart=False):
 end=time.time()+240
 while time.time()<end:
  role,p=current[0];game=RUN/role
  if (game/'ui-failed.txt').exists():raise RuntimeError((game/'ui-failed.txt').read_text())
  if p.poll() is not None:raise RuntimeError('UI interaction client exited '+str(p.returncode))
  marker=game/('ui-restart-passed.txt' if restart else 'ui-passed.txt')
  if marker.exists():print('ACTUAL_MINECRAFT_UI_INTERACTIONS_PASS '+marker.read_text().strip(),flush=True);return
  time.sleep(1)
 raise RuntimeError('Actual UI interactions timed out')

def wait_smoke(current,server):
 end=time.time()+240;prepared=False
 while time.time()<end:
  role,p=current[0];game=RUN/role
  if not prepared and (game/'joined-ready.txt').exists():
   commands(server,['fill -10 60 -10 10 60 10 minecraft:stone','tp QA_Alpha 0 61 3 0 0','time set day','weather clear','effect give QA_Alpha minecraft:speed 600 1 true','item replace entity QA_Alpha armor.head with minecraft:diamond_helmet'])
   prepared=True
  if (game/'smoke-failed.txt').exists():raise RuntimeError((game/'smoke-failed.txt').read_text())
  if p.poll() is not None:raise RuntimeError('Smoke client exited '+str(p.returncode))
  if (game/'smoke-passed.txt').exists():print('ACTUAL_MINECRAFT_TARGET_SMOKE_PASS '+(game/'smoke-passed.txt').read_text().strip(),flush=True);return
  time.sleep(1)
 raise RuntimeError('Actual target smoke timed out')

def wait_features(current,server):
 end=time.time()+480;prepared=False;native_input=False
 while time.time()<end:
  role,p=current[0];game=RUN/role
  if not prepared and (game/'joined-ready.txt').exists():
   commands(server,['fill -25 60 -25 25 60 25 minecraft:stone','tp QA_Alpha 0 61 3 0 0','time set day','weather clear','effect give QA_Alpha minecraft:speed 600 1 true','item replace entity QA_Alpha armor.head with minecraft:diamond_helmet','item replace entity QA_Alpha armor.chest with minecraft:diamond_chestplate','item replace entity QA_Alpha armor.legs with minecraft:diamond_leggings','item replace entity QA_Alpha armor.feet with minecraft:diamond_boots'])
   prepared=True
  if not native_input and (game/'mouse-input-ready.txt').exists():
   xdotool=str(SYSROOT/'usr/bin/xdotool')
   windows=subprocess.check_output([xdotool,'search','--pid',str(p.pid)],env=env,text=True).splitlines()
   assert windows,'Minecraft X11 window not found'
   subprocess.run([xdotool,'windowfocus','--sync',windows[-1],'mousemove','--window',windows[-1],'500','300','mousedown','1','mousedown','3','keydown','w'],env=env,check=True)
   native_input=True
  if (game/'feature-failed.txt').exists():raise RuntimeError((game/'feature-failed.txt').read_text())
  if p.poll() is not None:raise RuntimeError('Feature client exited '+str(p.returncode))
  if (game/'feature-passed.txt').exists():
   if native_input:subprocess.run([str(SYSROOT/'usr/bin/xdotool'),'mouseup','1','mouseup','3','keyup','w'],env=env,check=True)
   print('ACTUAL_MINECRAFT_FEATURES_PASS '+(game/'feature-passed.txt').read_text().strip(),flush=True);return
  time.sleep(1)
 raise RuntimeError('Actual feature probe timed out')

try:
 display=start([str(SYSROOT/'usr/bin/Xvfb'),':'+DISPLAY_NUMBER,'-screen','0','1600x1000x24','-nolisten','unix','-listen','tcp','-ac','-fp',str(SYSROOT/'usr/share/fonts/X11/misc')],'display',RUN)
 serverdir=RUN/'server';serverdir.mkdir()
 (serverdir/'eula.txt').write_text('# Isolated local development verification of Minecraft\neula=true\n')
 (serverdir/'server.properties').write_text('server-ip=127.0.0.1\nserver-port='+PORT+'\nonline-mode=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft\\:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngamemode=creative\ndifficulty=peaceful\nspawn-protection=0\nmax-players=4\nmotd=KapKan private development QA\n')
 # Fabric recognizes and unpacks Mojang's official bundled dedicated server.
 fabriclibs=[str(p) for p in (D/'fabric-libs').rglob('*.jar')]
 servercp=os.pathsep.join(fabriclibs+[str(D/(VERSION+'-server.jar'))])
 server=start([JAVA,'-Xms256m','-Xmx'+SERVER_HEAP,'-Dfabric.addMods='+str(MOD),'-Dfabric.gameJarPath='+str(D/(VERSION+'-server.jar')),'-cp',servercp,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],'server',serverdir,stdin=True)
 wait_server(server);print('Actual Fabric dedicated server ready',flush=True)
 if MODE=='features':
  current=clients();wait_features(current,server)
 elif MODE=='smoke':
  current=clients();wait_smoke(current,server)
 elif MODE=='ui':
  current=clients();wait_ui(current,server)
  shutil.copy2(RUN/'alpha/options.txt',RUN/'ui-options-rebound-k.txt')
  for role,p in current:p.terminate()
  for role,p in current:
   try:p.wait(timeout=15)
   except subprocess.TimeoutExpired:p.kill();p.wait(timeout=5)
  current=clients(restart=True);wait_ui(current,server,restart=True)
  shutil.copy2(RUN/'alpha/options.txt',RUN/'ui-options-restored-j.txt')
 else:
  current=clients();wait_clients(current,server)
  for role,_ in current:(RUN/role/'dimension-request.txt').write_text('Transition requested\n')
  commands(server,['execute in minecraft:the_nether run tp QA_Alpha 0 70 3 180 0','execute in minecraft:the_nether run tp QA_Beta 0 70 -3 180 0'])
  wait_markers(current,'dimension-passed.txt');print('Actual dimension transition checks passed',flush=True)
  commands(server,['kill QA_Alpha','kill QA_Beta'])
  wait_markers(current,'respawn-passed.txt');print('Actual death and respawn checks passed',flush=True)
  for role,_ in current:(RUN/role/'reconnect-request.txt').write_text('Reconnect requested\n')
  wait_markers(current,'reconnect-passed.txt');print('Actual disconnect/reconnect checks passed',flush=True)
  for role,p in current:p.terminate()
  for role,p in current:
   try:p.wait(timeout=15)
   except subprocess.TimeoutExpired:p.kill();p.wait(timeout=5)
  for role,_ in current:
   for marker in ('qa-failed.txt','joined-ready.txt'):
    (RUN/role/marker).unlink(missing_ok=True)
  current=clients(restart=True);wait_clients(current,server,restart=True)
 images=RUN/'screenshots';images.mkdir()
 for role in ('alpha','beta'):
  for image in (RUN/role/'screenshots').glob('KapKan*.png'):shutil.copy2(image,images/image.name)
 (RUN/'PASS.txt').write_text(('One actual Minecraft '+MODE+' client verified' if MODE in ('features','smoke','ui') else 'Two actual independent Minecraft clients and actual process restarts verified')+' on Minecraft '+VERSION+' Fabric against dedicated localhost server. See logs for assertion scope.\n')
 print('ACTUAL_MINECRAFT_ACCEPTANCE_PASS',RUN,flush=True)
finally:
 for p in reversed(processes):
  if p.poll() is None:p.terminate()
 for p in reversed(processes):
  try:p.wait(timeout=5)
  except subprocess.TimeoutExpired:p.kill()
 for log in logs:log.close()
