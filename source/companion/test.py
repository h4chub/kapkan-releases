#!/usr/bin/env python3
"""Run deterministic companion tests against one exact compiled game target."""
from pathlib import Path
import argparse
import os
import subprocess

root = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--minecraft', choices=['26.1', '26.1.1', '26.1.2'], default='26.1.2')
args = parser.parse_args()
build = root / 'build' / args.minecraft
cp = (build / 'classpath.txt').read_text() + os.pathsep + str(build / 'classes') + os.pathsep + str(root / 'src/main/resources')
classes = build / 'testclasses'
classes.mkdir(parents=True, exist_ok=True)
jdk = os.getenv('KAPKAN_JDK')
def tool(name):
    return str(Path(jdk) / 'bin' / name) if jdk else name
subprocess.run([tool('javac'), '--release', '25', '-proc:none', '-cp', cp, '-d', str(classes),
                *map(str, sorted((root / 'src/test').rglob('*.java')))], check=True)
for source in sorted((root / 'src/test').rglob('*Test.java')):
    test = '.'.join(source.relative_to(root / 'src/test/java').with_suffix('').parts)
    subprocess.run([tool('java'), '-Dkapkan.test.service=' + str(root.parent / 'presence-service'),
                    '-cp', cp + os.pathsep + str(classes), test], check=True)
