#!/usr/bin/env python3
"""Audit exact-version outputs and their embedded Fabric dependency closure.

This verifies release packaging and classfile levels. It does not claim gameplay
or mixin execution; actual game results are recorded separately in qa/1.0.5.
"""
from pathlib import Path
import hashlib
from io import BytesIO
import json
import zipfile

root = Path(__file__).resolve().parents[1]
lock_bytes = (root / 'companion/dependencies.lock.json').read_bytes()
lock = json.loads(lock_bytes)
rows = []
for version, target in lock['targets'].items():
    path = root / f'app/KapKan-{lock["release"]}-mc{version}-fabric.jar'
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None, 'Corrupt archive: ' + path.name
        names = set(jar.namelist())
        metadata = json.loads(jar.read('fabric.mod.json'))
        assert metadata['version'] == lock['release']
        assert metadata['depends']['minecraft'] == '=' + version
        assert metadata['depends']['java'] == '>=' + str(target['java'])
        assert metadata['depends']['fabricloader'] == '>=' + lock['fabric_loader']
        assert metadata['custom']['kapkan:dependency-lock-sha256'] == hashlib.sha256(lock_bytes).hexdigest()
        assert metadata['environment'] == '*'
        assert metadata['entrypoints']['main'] == ['gg.kapkan.presence.relay.CosmeticRelay']
        assert metadata['entrypoints']['client'] == ['gg.kapkan.presence.BadgeClient']
        for group in metadata['entrypoints'].values():
            for entry in group:
                assert entry.replace('.', '/') + '.class' in names, 'Missing entrypoint: ' + entry
        config = json.loads(jar.read('kapkan-presence.mixins.json'))
        assert config['compatibilityLevel'] == 'JAVA_25'
        for mixin in config['client']:
            assert config['package'].replace('.', '/') + '/' + mixin + '.class' in names, 'Missing client mixin: ' + mixin
        assert not any(name.startswith(('net/minecraft/', 'com/mojang/', 'org/lwjgl/')) for name in names), 'Bundled game/library classes'
        classes = [name for name in names if name.endswith('.class')]
        for name in classes:
            data = jar.read(name)
            assert data[:4] == b'\xca\xfe\xba\xbe'
            assert int.from_bytes(data[6:8], 'big') == target['java'] + 44, 'Unexpected Java class level: ' + name
        actual_modules = {}
        declared = {entry['file'] for entry in metadata['jars']}
        for module in target['fabric_api']['modules']:
            nested_path = 'META-INF/jars/' + Path(module['path']).name
            assert nested_path in declared
            data = jar.read(nested_path)
            assert hashlib.sha256(data).hexdigest() == module['sha256']
            with zipfile.ZipFile(BytesIO(data)) as dependency:
                details = json.loads(dependency.read('fabric.mod.json'))
            assert details['id'] == module['id'] and details['version'] == module['version']
            actual_modules[details['id']] = details
        assert len(actual_modules) == 3 and len(declared) == 3
        available = set(actual_modules) | {'minecraft', 'java', 'fabricloader'}
        for details in actual_modules.values():
            assert set(details.get('depends', {})) <= available, 'Missing transitive module: ' + details['id']
    rows.append({'minecraft': version, 'loader': 'Fabric >= ' + lock['fabric_loader'], 'java': target['java'],
                 'build': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                 'compiled': True, 'packaging_audit': True, 'in_game': 'See separate actual game QA report'})
output = root / 'companion/build/compatibility-builds.json'
output.write_text(json.dumps(rows, indent=2) + '\n')
for row in rows:
    print(row['minecraft'] + ': exact Minecraft metadata, Java25 bytecode, entrypoints, mixins and nested module closure verified')
