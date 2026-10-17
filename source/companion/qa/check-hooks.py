#!/usr/bin/env python3
"""Apply the shipped badge mixins in Fabric's real class loader, without claiming gameplay."""
from pathlib import Path
import concurrent.futures,hashlib,json,os,subprocess,tempfile,urllib.request,zipfile
R=Path(__file__).resolve().parents[2];D=Path(os.getenv('KAPKAN_DEPS',R/'companion/build/deps'));Q=Path(os.getenv('KAPKAN_QA_OUT',R/'qa-build8'));Q.mkdir(exist_ok=True)
with zipfile.ZipFile(D/'fabric-loader.jar') as z: loader=json.loads(z.read('fabric-installer.json'))
def fetch(item):
 group,name,version=item['name'].split(':');relative=f'{group.replace(".","/")}/{name}/{version}/{name}-{version}.jar';p=D/'fabric-libs'/Path(relative).name
 if not p.exists() or hashlib.sha256(p.read_bytes()).hexdigest()!=item['sha256']:
  data=urllib.request.urlopen(item['url']+relative,timeout=45).read();assert hashlib.sha256(data).hexdigest()==item['sha256'];p.parent.mkdir(exist_ok=True);p.write_bytes(data)
 return p
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:libs=list(pool.map(fetch,loader['libraries']['common']))
cp=os.pathsep.join(p for p in (R/'companion/build/classpath.txt').read_text().split(os.pathsep) if not p.endswith('/mixin-current.jar'))+os.pathsep+os.pathsep.join(map(str,libs))
with tempfile.TemporaryDirectory(prefix='kapkan-hooks-') as temporary:
 t=Path(temporary);c=t/'classes';c.mkdir();(t/'game').mkdir()
 subprocess.run(['javac','--release','25','-proc:none','-cp',cp,'-d',str(c),str(R/'companion/qa/src/gg/kapkan/qa/LoadHooks.java')],check=True)
 mod=t/'hooks-only.jar'
 with zipfile.ZipFile(mod,'w') as z:
  for f in c.rglob('*.class'):z.write(f,f.relative_to(c))
  z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'kapkan-hooks-qa','version':'1.0.0','environment':'client','entrypoints':{'preLaunch':['gg.kapkan.qaloader.LoadHooks']}}))
 command=['java','-Xmx700m','-Dkapkan.qa.hooksOnly=true','-Dfabric.addMods='+str(R/'app/KapKan-Presence-26.1.2.jar')+os.pathsep+str(mod),'-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotClient','--version','26.1.2','--gameDir',str(t/'game')]
 result=subprocess.run(command,cwd=t,capture_output=True,text=True,timeout=45)
 (Q/'real-library-hooks.log').write_text(result.stdout+result.stderr);print(result.stdout+result.stderr);result.check_returncode()
