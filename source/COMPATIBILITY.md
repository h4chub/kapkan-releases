# KapKan 1.0.6 compatibility

| Minecraft | Loader | Java | Build | Deterministic checks | Native game check |
| --- | --- | --- | --- | --- | --- |
| 26.1 | Fabric 0.19.5+ | 25 | KapKan-1.0.6-mc26.1-fabric.jar | 1,209 passed | Native macOS world entry with bundled modules |
| 26.1.1 | Fabric 0.19.5+ | 25 | KapKan-1.0.6-mc26.1.1-fabric.jar | 1,209 passed | Native macOS world entry with bundled modules |
| 26.1.2 | Fabric 0.19.5+ | 25 | KapKan-1.0.6-mc26.1.2-fabric.jar | 1,209 passed | Native macOS world entry with bundled modules |

The three outputs are independently compiled against each exact official Minecraft release. Other game versions and Forge/NeoForge/Quilt are unsupported by this update. The retained 1.21.11 companion is a legacy binary. Current code directly targets the 26.1 rendering, input, avatar and mixin APIs; older versions require an actual port and verification.

The launcher UI requires Java 21. Minecraft 26.1/.1/.2 require Java 25. The native launcher manages these separately. On macOS it uses checksum-verified, vendor-signed Azul Zulu JREs with microphone capability, preserving old Temurin caches. Windows continues using Temurin. The Mac launcher is universal arm64/x86_64; native execution checks were on arm64 only. Windows binaries target x86_64; native Windows installation is untested.

## Dependencies and optional modules

[companion/dependencies.lock.json](companion/dependencies.lock.json) pins official game metadata/client hashes, Fabric 0.19.5, Mixin and each target's API modules. Only API Base, Networking API and Lifecycle Events are embedded in the companion, with original licenses. Minecraft classes, visual assets, native libraries and Fabric Loader are build inputs and are not redistributed inside the mod.

The desktop launcher separately includes unmodified ImmediatelyFast 1.15.3, Lithium 0.24.7 and FerriteCore 9.0.0. Their official metadata lists all three exact game targets. [performance/manifest.json](performance/manifest.json) pins binary hashes and corresponding source commits. Binaries, complete sources and licenses travel together in Resources/performance.

Bundled modules are enabled by default for these exact Fabric targets. **Use bundled performance modules** in Game performance disables them on the next game start. Compatible existing direct/nested/provided copies take precedence; declared/known conflicts skip the corresponding module. Uninspectable active mods cause safe omission of the bundle. No user mods directory, graphics options, controls, world or JVM memory setting is changed by this integration. Unknown modpack interactions still need testing.

Automatic cosmetic sharing requires the matching KapKan JAR on the Fabric server and clients. No public cosmetic service is deployed.

## Reproduce

Use JDK 25 with `companion/build.py`, then `companion/test.py --minecraft VERSION` for each supported target. `companion/check_compatibility.py` audits version metadata, Java level, entrypoints, mixins, embedded modules and dependency closure. Desktop/full-package instructions are in [README.md](README.md). See [QA-REPORT.md](QA-REPORT.md) for actual run scope. Historical 1.0.5 Linux runs remain in [history/1.0.5](history/1.0.5/) and are not new 1.0.6 native results.
