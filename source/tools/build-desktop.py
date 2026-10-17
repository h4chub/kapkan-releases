#!/usr/bin/env python3
"""Compile the launcher layer; the version-matched client mods are packaged separately."""
from pathlib import Path
import os,shutil,subprocess,zipfile
root=Path(__file__).resolve().parents[1]
jdk=Path(os.environ['KAPKAN_JDK'])
def run(*args):subprocess.run([str(a) for a in args],cwd=root,check=True)
classes=root/'build/classes';tests=root/'build/testclasses';tools=root/'build/tools'
for folder in (classes,tests,tools):folder.mkdir(parents=True,exist_ok=True)
for p in classes.rglob('*.class'):p.unlink()
run(jdk/'bin/javac','--release','21','-cp',root/'app/KapKan-Launcher-base.jar','-d',classes,*sorted((root/'java').rglob('*.java')))
shutil.copytree(root/'java-resources',classes,dirs_exist_ok=True)
run(jdk/'bin/javac','-cp',root/'tools/asm-9.10.1.jar','-d',tools,root/'tools/PatchRelease.java')
run(jdk/'bin/java','-cp',str(tools)+os.pathsep+str(root/'tools/asm-9.10.1.jar'),'PatchRelease',root/'app/KapKan-Launcher-base.jar',classes,root/'app/KapKan-Launcher.jar')
run(jdk/'bin/javac','--release','21','-cp',root/'app/KapKan-Launcher.jar','-d',tests,*sorted((root/'tests').glob('*.java')))
print('Desktop compilation complete')
