#!/usr/bin/env python3
"""Real Swing interaction/capture checks in an isolated profile on Linux/Xvfb."""
from pathlib import Path
import argparse, os, shutil, subprocess, tempfile, time

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--jar',default=str(root/'app/KapKan-Launcher.jar'))
parser.add_argument('--scales',default='1,2')
parser.add_argument('--build',default='h6',choices=['h3','h4','h6'])
args=parser.parse_args()
java=os.environ.get('JAVA_BIN') or shutil.which('java')
javac=os.environ.get('JAVAC_BIN') or shutil.which('javac')
xvfb=os.environ.get('XVFB_BIN') or shutil.which('Xvfb')
if not all((java,javac,xvfb)):raise SystemExit('JDK 21+ and Xvfb are required; JAVA_BIN, JAVAC_BIN and XVFB_BIN may specify paths.')
jar=Path(args.jar).resolve();classes=root/'build/uiqa';classes.mkdir(parents=True,exist_ok=True)
subprocess.run([javac,'--release','21','-cp',str(jar),'-d',str(classes),str(root/f'tests/{args.build.upper()}UiTest.java')],check=True)
out=root/f'qa-{args.build}';out.mkdir(exist_ok=True)
env=os.environ.copy();env['DISPLAY']='127.0.0.1:88'
with (out/'xvfb.log').open('w') as displaylog:
    display=subprocess.Popen([xvfb,':88','-screen','0','2560x1800x24','-ac','-listen','tcp','-nolisten','unix'],env=env,stdout=displaylog,stderr=subprocess.STDOUT)
    try:
        time.sleep(1)
        for scale in args.scales.split(','):
            folder=out if scale=='1' else out/f'scale-{scale}';folder.mkdir(exist_ok=True)
            with tempfile.TemporaryDirectory(prefix=f'kapkan-{args.build}-ui-') as home,(folder/'ui-test.log').open('w') as log:
                command=[java,'-Xmx512m',f'-Duser.home={home}',f'-Dsun.java2d.uiScale={scale}',f'-Dkapkan.qa.out={folder}',
                         '-cp',str(classes)+os.pathsep+str(jar),f'gg.kapkan.v23.{args.build.upper()}UiTest']
                result=subprocess.run(command,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=45)
            print(f'{scale}x: '+(folder/'ui-test.log').read_text(),flush=True)
            if result.returncode:raise SystemExit(result.returncode)
    finally:
        display.terminate();display.wait(timeout=5)
