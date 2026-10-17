import os,sys,json,time,pathlib,subprocess,uuid,shutil
P=pathlib.Path(os.environ['KAPKAN_DEPS']);R=pathlib.Path(__file__).resolve().parents[2];Q=R/'qa-h2/live';Q.mkdir(parents=True,exist_ok=True)
java=str(next(P.glob('zulu*/bin/java')));env=os.environ.copy();env.update(DISPLAY=':99',LD_LIBRARY_PATH=str(P/'xserver/usr/lib/x86_64-linux-gnu'),LIBGL_DRIVERS_PATH=str(P/'xserver/usr/lib/x86_64-linux-gnu/dri'),LIBGL_ALWAYS_SOFTWARE='1',ALSOFT_DRIVERS='null')
processes=[]
def start(args,name,cwd,stdin=False,extra=None):
 e=env.copy();e.update(extra or {});log=open(Q/(name+'.log'),'w');proc=subprocess.Popen(args,cwd=cwd,env=e,stdin=subprocess.PIPE if stdin else subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,text=True);processes.append(proc);return proc
try:
 x=start([str(P/'xserver/usr/bin/Xvfb'),':99','-screen','0','1600x1000x24','-ac','-nolisten','tcp','-listen','unix','-nolisten','local'],'display',Q)
 presence=start([sys.executable,str(R/'presence-service/server.py')],'presence',Q,extra={'PORT':'18087','KAPKAN_DATABASE':str(Q/'presence.sqlite3')})
 serverdir=Q/'server';serverdir.mkdir(exist_ok=True);(serverdir/'eula.txt').write_text('eula=true\n');(serverdir/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25578\nonline-mode=false\nenforce-secure-profile=false\nview-distance=3\nsimulation-distance=3\nlevel-type=minecraft\\:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngamemode=creative\ndifficulty=peaceful\nspawn-protection=0\nmax-players=4\nmotd=KapKan private automated QA\n')
 server=start([java,'-Xms256m','-Xmx1200m','-jar',str(P/'minecraft-server.jar'),'nogui'],'server',serverdir,stdin=True)
 end=time.time()+160
 while time.time()<end:
  if 'Done (' in (Q/'server.log').read_text():break
  if server.poll() is not None:raise RuntimeError('Server failed')
  time.sleep(1)
 else:raise RuntimeError('Server startup timed out')
 print('Server ready; launching actual Fabric clients',flush=True)
 meta=json.loads((P/'26.1.2.json').read_text());libs=[]
 for l in meta['libraries']:
  allow=True
  if 'rules' in l:
   allow=False
   for rule in l['rules']:
    if rule.get('os',{}).get('name','linux')=='linux' and 'features' not in rule:allow=rule['action']=='allow'
  if allow:libs.append(P/'game-libs'/l['downloads']['artifact']['path'])
 cp=os.pathsep.join(map(str,[P/'fabric-loader.jar',*sorted((P/'fabric-libs').glob('*.jar')),P/'minecraft-26.1.2.jar',*libs]))
 clients=[]
 for role,name in [('alpha','QA_Alpha'),('beta','QA_Beta'),('plain','PlainPlayer')]:
  d=Q/role;d.mkdir(exist_ok=True);cfg=d/'presence.properties';cfg.write_text('enabled='+('false' if role=='plain' else 'true')+'\nshowOwn=true\nendpoint=http://127.0.0.1:18087\n')
  for flag in ['qa-passed.txt','qa-failed.txt']:
   f=d/flag
   if f.exists():f.unlink()
  (d/'options.txt').write_text('version:4772\nonboardAccessibility:false\nrenderDistance:3\nsimulationDistance:3\nmaxFps:20\nenableVsync:false\nguiScale:2\npauseOnLostFocus:false\nfullscreen:false\nsoundCategory_master:0.0\n')
  raw=bytearray(__import__('hashlib').md5(('OfflinePlayer:'+name).encode()).digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128;ident=str(uuid.UUID(bytes=bytes(raw)))
  args=[java,'-Xms256m','-Xmx1300m','-Dfabric.addMods='+str(R/'app/KapKan-Presence-26.1.2-H2.jar')+os.pathsep+str(R/'companion/qa/test-only.jar'),'-Dkapkan.launchSession='+str(uuid.uuid4()),'-Dkapkan.presence.config='+str(cfg),'-Dkapkan.qa.role='+role,'-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotClient','--username',name,'--uuid',ident,'--accessToken','0','--version','26.1.2','--versionType','release','--gameDir',str(d),'--assetsDir',str(P/'assets'),'--assetIndex','30','--width','800','--height','520','--quickPlayMultiplayer','127.0.0.1:25578']
  clients.append((role,start(args,role,d)))
 print('Three client processes started, two presence-enabled and one opted out',flush=True)
 prepared=False;end=time.time()+420
 while time.time()<end:
  if (Q/'stop').exists():raise RuntimeError('Stopped to correct display setup')
  servertext=(Q/'server.log').read_text()
  if not prepared and all(n+' joined the game' in servertext for n in ['QA_Alpha','QA_Beta','PlainPlayer']):
   commands=['fill -10 60 -10 10 60 10 minecraft:stone','tp QA_Alpha 0 61 2 180 0','tp QA_Beta 0 61 -3 0 0','tp PlainPlayer 3 61 -3 0 0','time set day','weather clear','team add crimson','team modify crimson color red','team modify crimson prefix {"text":"[VIP] ","color":"gold"}','team join crimson QA_Beta']
   server.stdin.write('\n'.join(commands)+'\n');server.stdin.flush();prepared=True;print('All three joined; live team-prefix and visibility checks running',flush=True)
  if any((Q/r/'qa-failed.txt').exists() for r in ['alpha','beta']):raise RuntimeError('Game probe assertion failed')
  if all((Q/r/'qa-passed.txt').exists() for r in ['alpha','beta']):print('LIVE MINECRAFT CHECKS PASSED',flush=True);break
  for role,c in clients:
   if c.poll() is not None:raise RuntimeError(role+' client exited '+str(c.returncode))
  time.sleep(1)
 else:raise RuntimeError('Live game test timed out')
finally:
 for proc in reversed(processes):
  if proc.poll() is None:proc.terminate()
 for proc in reversed(processes):
  try:proc.wait(timeout=5)
  except subprocess.TimeoutExpired:proc.kill()
