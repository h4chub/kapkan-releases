#!/usr/bin/env python3
"""Download checksum-verified official Minecraft runtime for isolated, local QA.

Does not redistribute Minecraft, accept an external server's policies or touch any
normal user game directory. The QA output/cache is deliberately unshipped.
"""
from pathlib import Path
import concurrent.futures, hashlib, json, os, time, urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'qa/1.0.5/runtime'
OUT.mkdir(parents=True, exist_ok=True)
VERSION = os.getenv('KAPKAN_QA_VERSION', '26.1.2')

def fetch(url, path, expected=None, algorithm='sha1'):
    if path.exists() and (expected is None or hashlib.new(algorithm, path.read_bytes()).hexdigest() == expected):
        return path.read_bytes()
    error = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'KapKan/1.0.5 development QA'})
            data = urllib.request.urlopen(req, timeout=90).read()
            if expected and hashlib.new(algorithm, data).hexdigest() != expected:
                raise ValueError('Checksum mismatch: ' + str(path))
            path.parent.mkdir(parents=True, exist_ok=True)
            tmp = path.with_suffix(path.suffix + '.part')
            tmp.write_bytes(data)
            tmp.replace(path)
            return data
        except Exception as exc:
            error = exc
            if attempt < 2:
                time.sleep(attempt + 1)
    raise error

manifest = json.loads(fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', OUT/'version_manifest_v2.json'))
entry = next(v for v in manifest['versions'] if v['id'] == VERSION)
meta = json.loads(fetch(entry['url'], OUT/(VERSION+'.json'), entry['sha1']))
for name in ('client', 'server'):
    item = meta['downloads'][name]
    fetch(item['url'], OUT/(VERSION+'-'+name+'.jar'), item['sha1'])
index = meta['assetIndex']
assets = json.loads(fetch(index['url'], OUT/'assets/indexes'/(index['id']+'.json'), index['sha1']))

def asset(item):
    name, descriptor = item
    # Audio disabled by QA; skip 100s MB of audio but retain all visual/font data.
    if '/sounds/' in name:
        return
    h = descriptor['hash']
    fetch('https://resources.download.minecraft.net/'+h[:2]+'/'+h, OUT/'assets/objects'/h[:2]/h, h)

def library(lib):
    allowed = 'rules' not in lib
    for rule in lib.get('rules', []):
        os_rule = rule.get('os', {})
        if os_rule.get('name', 'linux') == 'linux' and 'features' not in rule:
            allowed = rule['action'] == 'allow'
    if allowed and (item := lib.get('downloads', {}).get('artifact')):
        p = OUT/'game-libs'/item['path']
        fetch(item['url'], p, item['sha1'])
        return str(p)

with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
    list(pool.map(asset, assets['objects'].items()))
    libs = [v for v in pool.map(library, meta['libraries']) if v]

loader_version = '0.19.5'
fabric = json.loads(fetch('https://meta.fabricmc.net/v2/versions/loader/'+VERSION+'/'+loader_version+'/profile/json', OUT/(VERSION+'-fabric-profile.json')))
fabric_libs = []
for lib in fabric['libraries']:
    group, artifact, version = lib['name'].split(':')
    relative = group.replace('.', '/')+'/'+artifact+'/'+version+'/'+artifact+'-'+version+'.jar'
    p = OUT/'fabric-libs'/relative
    # Fabric provides checksum sidecars for exact Maven artifacts.
    url = lib.get('url', 'https://maven.fabricmc.net/') + relative
    checksum = urllib.request.urlopen(url+'.sha1', timeout=60).read().decode().split()[0]
    fetch(url, p, checksum)
    fabric_libs.append(str(p))

(OUT/(VERSION+'-classpath.txt')).write_text(os.pathsep.join(fabric_libs+[str(OUT/(VERSION+'-client.jar'))]+libs))
(OUT/(VERSION+'-runtime-manifest.json')).write_text(json.dumps({'minecraft': VERSION, 'assetIndex': index['id'], 'java': meta['javaVersion'], 'clientSha1': meta['downloads']['client']['sha1'], 'serverSha1': meta['downloads']['server']['sha1'], 'visualAssets': 'checksum verified; audio disabled and not downloaded', 'fabric': loader_version}, indent=2)+'\n')
print('QA runtime ready:', OUT, flush=True)
