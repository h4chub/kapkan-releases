# KapKan 1.0.7 compatibility

| Minecraft | Loader | Java | Build | Deterministic checks | Native game check |
| --- | --- | --- | --- | --- | --- |
| 26.1 | Fabric 0.19.5+ | 25 | KapKan-1.0.7-mc26.1-fabric.jar | 1,350 passed | Not rerun for 1.0.7; historical 1.0.6 result retained |
| 26.1.1 | Fabric 0.19.5+ | 25 | KapKan-1.0.7-mc26.1.1-fabric.jar | 1,350 passed | Not rerun for 1.0.7; historical 1.0.6 result retained |
| 26.1.2 | Fabric 0.19.5+ | 25 | KapKan-1.0.7-mc26.1.2-fabric.jar | 1,350 passed | Native macOS arm64 equipment HUD: 174 checks, bright/dark visuals and inventory transitions |

The three outputs are independently compiled against each exact official Minecraft release. Other game versions and Forge/NeoForge/Quilt are unsupported by this update. The retained 1.21.11 companion is a legacy binary. Current code directly targets the 26.1 rendering, input, avatar and mixin APIs; older versions require an actual port and verification.

The launcher UI requires Java 21. Minecraft 26.1/.1/.2 require Java 25. The native launcher manages these separately. On macOS it uses checksum-verified, vendor-signed Azul Zulu JREs with microphone capability, preserving old Temurin caches. Windows continues using Temurin. The Mac launcher is universal arm64/x86_64; native execution checks were on arm64 only. Windows binaries target x86_64; native Windows installation is untested.

## Dependencies and optional modules

[companion/dependencies.lock.json](companion/dependencies.lock.json) pins official game metadata/client hashes, Fabric 0.19.5, Mixin and each target's API modules. Only API Base, Networking API and Lifecycle Events are embedded in the companion, with original licenses. Minecraft classes, visual assets, native libraries and Fabric Loader are build inputs and are not redistributed inside the mod.

The desktop launcher includes unmodified ImmediatelyFast 1.15.3 for optional rendering optimizations. Its official metadata lists all three exact targets. [performance/manifest.json](performance/manifest.json) pins the binary and source hashes; the exact source and full licenses accompany every shipped binary. The earlier three-module candidate and its measurements remain QA history; Lithium and FerriteCore are omitted from the final bundle.

**Use rendering optimizations** is enabled by default and applies on the next game start. Compatible existing direct/nested/provided copies take precedence. Declared/known conflicts skip the addition; uninspectable active mods cause safe omission. User mods, graphics, controls, worlds and JVM memory remain unchanged. Unknown modpack interactions still need testing.

Automatic cosmetic sharing requires the matching KapKan JAR on the Fabric server and clients. No public cosmetic service is deployed.

## Reproduce

Use JDK 25 with `companion/build.py`, then `companion/test.py --minecraft VERSION` for each supported target. `companion/check_compatibility.py` audits version metadata, Java level, entrypoints, mixins, embedded modules and dependency closure. Desktop/full-package instructions are in [README.md](README.md). See [QA-REPORT.md](QA-REPORT.md) for actual run scope. Historical 1.0.5 Linux runs remain in [history/1.0.5](history/1.0.5/) and are not new 1.0.7 native results.

## 1.0.7 equipment HUD

The shared renderer compiles and passes deterministic checks on all three exact targets. Native bright/dark equipment-HUD acceptance was rerun on macOS arm64 26.1.2 only. Earlier 1.0.6 native target results remain historical evidence. Existing armor HUD/profile keys are unchanged; durable items in both hands now share that widget.
