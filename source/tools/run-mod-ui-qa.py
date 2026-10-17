#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,tempfile,time
root=Path(__file__).resolve().parents[1];out=root/'qa-mod-folders';out.mkdir(exist_ok=True)
env=os.environ.copy();env['DISPLAY']=':91'
with (out/'xvfb.log').open('w') as log:
 display=subprocess.Popen([env['XVFB_BIN'],':91','-screen','0','2560x1800x24','-ac','-nolisten','tcp','-fp',env['KAPKAN_XFONTS']],env=env,stdout=log,stderr=subprocess.STDOUT)
 try:
  time.sleep(1)
  for scale in ['1','2']:
   folder=out/f'scale-{scale}';folder.mkdir(exist_ok=True)
   with tempfile.TemporaryDirectory(prefix='kapkan-mod-ui-') as home,(folder/'test.log').open('w') as testlog:
    cmd=[str(Path(env['KAPKAN_JDK'])/'bin/java'),'-Xmx512m',f'-Duser.home={home}',f'-Dsun.java2d.uiScale={scale}',f'-Dkapkan.qa.out={folder}','-cp',str(root/'build/testclasses')+os.pathsep+str(root/'app/KapKan-Launcher.jar'),'gg.kapkan.v23.ModFoldersUiTest']
    result=subprocess.run(cmd,cwd=root,env=env,stdout=testlog,stderr=subprocess.STDOUT,timeout=60)
   print((folder/'test.log').read_text(),flush=True)
   if result.returncode:raise SystemExit(result.returncode)
 finally:
  display.terminate();display.wait(timeout=5)
