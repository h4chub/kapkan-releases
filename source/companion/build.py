#!/usr/bin/env python3
"""Build exact-version Fabric mods against pinned official Minecraft client jars.

Requires JDK 25, Python 3.10+ and Pillow. First build needs network access.
The three required Fabric API modules are bundled as Fabric nested jars.
Minecraft and game-library jars are build inputs only and are never distributed.
"""
from pathlib import Path
import argparse
import concurrent.futures
import hashlib
from io import BytesIO
import json
import os
import shutil
import subprocess
import urllib.request
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
HERE = ROOT / "companion"
LOCK = json.loads((HERE / "dependencies.lock.json").read_text())
CACHE = Path(os.getenv("KAPKAN_DEPS", str(HERE / "build/deps"))).resolve()
STAMP = (2026, 10, 16, 0, 0, 0)


def fetch(url, path, expected, algorithm="sha1", fallback=None, size=None):
    """Validate cached and downloaded bytes; publish complete files atomically."""
    path = Path(path)
    def valid(data):
        return (size is None or len(data) == size) and hashlib.new(algorithm, data).hexdigest() == expected
    if path.exists():
        data = path.read_bytes()
        if valid(data):
            return data
    path.parent.mkdir(parents=True, exist_ok=True)
    error = None
    for candidate in [url] + ([fallback] if fallback else []):
        for attempt in range(3):
            try:
                request = urllib.request.Request(candidate, headers={"User-Agent": "KapKan-build/1.0.6"})
                with urllib.request.urlopen(request, timeout=90) as response:
                    data = response.read()
                if not valid(data):
                    raise ValueError("Checksum/size mismatch for " + path.name)
                temporary = path.with_suffix(path.suffix + ".part")
                temporary.write_bytes(data)
                temporary.replace(path)
                return data
            except Exception as exc:
                error = exc
    raise RuntimeError(f"Could not fetch verified dependency {url}: {error}") from error


def java_tool(name):
    jdk = os.getenv("KAPKAN_JDK")
    return str(Path(jdk) / "bin" / name) if jdk else name


def prepare(version):
    target = LOCK["targets"][version]
    cache = CACHE / version
    cache.mkdir(parents=True, exist_ok=True)
    metadata = json.loads(fetch(target["metadata_url"], cache / f"{version}.json", target["metadata_sha1"]))
    if metadata["id"] != version or metadata["javaVersion"]["majorVersion"] != target["java"]:
        raise ValueError("Locked game metadata does not match target " + version)
    client = target["client"]
    game = cache / f"minecraft-{version}.jar"
    fetch(client["url"], game, client["sha1"], size=client["size"])
    loader = CACHE / "fabric-loader.jar"
    loader_url = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/{0}/fabric-loader-{0}.jar".format(LOCK["fabric_loader"])
    fetch(loader_url, loader, LOCK["loader_sha256"], "sha256")
    mixin = CACHE / "mixin-current.jar"
    mixin_url = "https://maven.fabricmc.net/net/fabricmc/sponge-mixin/{0}/sponge-mixin-{0}.jar".format(LOCK["mixin"])
    fetch(mixin_url, mixin, LOCK["mixin_sha256"], "sha256")

    def library(item):
        entry = item.get("downloads", {}).get("artifact")
        if not entry or "natives-" in entry["path"]:
            return None
        destination = CACHE / "game-libs" / entry["path"]
        fetch(entry["url"], destination, entry["sha1"], size=entry.get("size"),
              fallback="https://repo.maven.apache.org/maven2/" + entry["path"])
        return destination

    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        libraries = [path for path in pool.map(library, metadata["libraries"]) if path]
    api = target["fabric_api"]
    data = fetch(api["url"], cache / f"fabric-api-{api['version']}.jar", api["sha256"], "sha256")
    modules = []
    with zipfile.ZipFile(BytesIO(data)) as source:
        for module in api["modules"]:
            content = source.read(module["path"])
            if hashlib.sha256(content).hexdigest() != module["sha256"]:
                raise ValueError("Nested Fabric API checksum mismatch: " + module["id"])
            nested_metadata = json.loads(zipfile.ZipFile(BytesIO(content)).read("fabric.mod.json"))
            if (nested_metadata["id"], nested_metadata["version"]) != (module["id"], module["version"]):
                raise ValueError("Nested Fabric API metadata does not match lock")
            path = cache / Path(module["path"]).name
            path.write_bytes(content)
            modules.append(path)
    classpath = os.pathsep.join(map(str, [game, loader, mixin, *modules, *libraries]))
    build = HERE / "build" / version
    build.mkdir(parents=True, exist_ok=True)
    (build / "classpath.txt").write_text(classpath)
    # Retain the historical default test driver interface without mixing targets.
    if version == "26.1.2":
        (HERE / "build/classpath.txt").write_text(classpath)
    print("Prepared", version, "Java", target["java"], flush=True)
    return target, modules, classpath, build


def entry(archive, name, content):
    info = zipfile.ZipInfo(name, STAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(info, content)


def build(version, prepare_only=False):
    target, modules, classpath, destination = prepare(version)
    if prepare_only:
        return
    classes = destination / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    sources = sorted((HERE / "src/main/java").rglob("*.java"))
    # Optional target-owned adapters can override a shared source by relative path.
    adapters = HERE / "src/compat" / version / "java"
    if adapters.exists():
        overrides = {path.relative_to(adapters): path for path in adapters.rglob("*.java")}
        sources = [path for path in sources if path.relative_to(HERE / "src/main/java") not in overrides]
        sources += sorted(overrides.values())
    subprocess.run([java_tool("javac"), "--release", str(target["java"]), "-proc:none", "-cp", classpath,
                    "-d", str(classes), *map(str, sources)], check=True)
    metadata = json.loads((HERE / "src/main/resources/fabric.mod.json").read_text())
    metadata["version"] = LOCK["release"]
    metadata["depends"]["minecraft"] = "=" + version
    metadata["depends"]["java"] = ">=" + str(target["java"])
    metadata["depends"]["fabricloader"] = ">=" + LOCK["fabric_loader"]
    metadata["jars"] = [{"file": "META-INF/jars/" + path.name} for path in modules]
    metadata["custom"]["kapkan:minecraft-target"] = version
    metadata["custom"]["kapkan:dependency-lock-sha256"] = hashlib.sha256((HERE / "dependencies.lock.json").read_bytes()).hexdigest()
    output = ROOT / f"app/KapKan-{LOCK['release']}-mc{version}-fabric.jar"
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(".jar.part")
    with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED) as archive:
        for folder in [classes, HERE / "src/main/resources"]:
            for path in sorted(folder.rglob("*")):
                if path.is_file() and path.relative_to(folder).as_posix() != "fabric.mod.json":
                    entry(archive, path.relative_to(folder).as_posix(), path.read_bytes())
        entry(archive, "fabric.mod.json", (json.dumps(metadata, indent=2) + "\n").encode())
        for path in modules:
            entry(archive, "META-INF/jars/" + path.name, path.read_bytes())
        # Preserve provenance texture; generate only the font-atlas derivative.
        with Image.open(HERE / "src/main/resources/assets/kapkan/textures/font/double_k.png") as glyph:
            glyph.thumbnail((128, 128), Image.Resampling.LANCZOS)
            pixels = BytesIO()
            glyph.save(pixels, format="PNG")
        entry(archive, "assets/kapkan/textures/font/double_k_glyph.png", pixels.getvalue())
    temporary.replace(output)
    if version == "26.1.2":
        alias = HERE / "build/classes"
        if alias.is_symlink():
            alias.unlink()
        elif alias.exists():
            shutil.rmtree(alias)
        alias.symlink_to(classes, target_is_directory=True)
    print("Built", output.name, hashlib.sha256(output.read_bytes()).hexdigest(), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft", action="append", choices=list(LOCK["targets"]), help="repeat to select exact targets; default all")
    parser.add_argument("--prepare-only", action="store_true", help="fetch verified build inputs without compiling")
    args = parser.parse_args()
    for version in args.minecraft or list(LOCK["targets"]):
        build(version, args.prepare_only)


if __name__ == "__main__":
    main()
