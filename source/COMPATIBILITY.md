# KapKan 1.0.5 compatibility and build scope

Kapkan uses Fabric and exact Minecraft patch-version builds. Each target is independently compiled against that version's official, unobfuscated client JAR. The build does not expand a single JAR's Minecraft version range. The launcher may still launch other Minecraft versions; that does not make the 1.0.5 companion compatible with them.

| Minecraft | Loader | Java | Build | Built successfully | Tested in-game |
| --- | --- | --- | --- | --- | --- |
| 26.1 | Fabric Loader 0.19.5+ | 25+ | `KapKan-1.0.5-mc26.1-fabric.jar` | Yes; independently compiled, audited; 1,106 deterministic checks passed | Yes; two actual clients, cosmetics/lifecycle/restarts and 35 actual gameplay feature assertions (Linux) |
| 26.1.1 | Fabric Loader 0.19.5+ | 25+ | `KapKan-1.0.5-mc26.1.1-fabric.jar` | Yes; independently compiled, audited; 1,106 deterministic checks passed | Yes; two actual clients, cosmetics/lifecycle/restarts and 35 actual gameplay feature assertions (Linux) |
| 26.1.2 | Fabric Loader 0.19.5+ | 25+ | `KapKan-1.0.5-mc26.1.2-fabric.jar` | Yes; independently compiled, audited; 1,106 deterministic checks passed | Yes; two actual clients, cosmetics/lifecycle/restarts and 35 actual gameplay feature assertions (Linux) |
| 1.21.11 and earlier | No 1.0.5 target | Not applicable | None | No. Retained 1.21.11 Beta 1 binary is an older companion, not 1.0.5. | No |
| 26.2, 26.3 and later, snapshots | No 1.0.5 target | Not applicable | None | No | No |
| Any version on Forge, NeoForge, Quilt or another loader | No supported 1.0.5 target | Not applicable | None | No | No |

The supported loader minimum is deliberately the actual 0.19.5 build input. It is not inferred from Fabric API's less restrictive dependency declaration. Java 25 is required by all three official game-version metadata files and the compiled mod's classfile level.

Actual game verification used the exact final artifact hashes in `verification/1.0.5/runtime-report.json`, Fabric Loader 0.19.5 and JDK 25 on Linux with Mesa llvmpipe software OpenGL under Xvfb. Each target ran two independent Minecraft client processes against its own matching Fabric dedicated server. Each target also passed 35 actual gameplay feature assertions. This is not native macOS or Windows gameplay testing. Desktop package cross-build and fixture tests are recorded separately. The GUI checks cover 1280 × 800 at GUI scales 1, 2 and 3, plus 854 × 480 at GUI scale 2.

## Why older versions need a port

The production code directly uses the Minecraft 26.1 rendering and input APIs, including `GuiGraphicsExtractor`, `SubmitNodeCollector`, `AvatarRenderState` / `AvatarRenderer`, `net.minecraft.resources.Identifier`, and the current native keyboard and mouse event records. Its HUD, cape, name-tag and input mixins target concrete 26.1 method descriptors. Earlier releases use different mappings, class names, render submission paths and input signatures. Minecraft 1.21.11 also uses the older Java/toolchain baseline and obfuscated official distribution rather than these 26.1 unobfuscated development inputs. Supporting it requires mapping/remapping setup plus separate render/input/mixin adapters and gameplay verification. Editing only `fabric.mod.json` cannot provide that port.

The new build script supports source overrides under `src/compat/<exact-version>/java/` if a later target needs an adapter. The three supported 26.1 patch releases currently use shared sources, but each has its own real game and dependency classpath and independently compiled output.

## Pinned dependencies

`dependencies.lock.json` records official Mojang metadata SHA-1, client SHA-1 and size, Fabric Loader and Mixin SHA-256, and per-target Fabric API artifact and nested module SHA-256. Every cached input is checked again before use. Downloads are checksum-validated before an atomic replacement, with bounded retries and the official Maven Central fallback for game libraries.

| Minecraft target | Fabric API release used as module source | Bundled modules |
| --- | --- | --- |
| 26.1 | 0.145.1+26.1 | API Base 2.0.3+ece0632347; Networking API 6.3.0+50a808ce47; Lifecycle Events 4.0.6+a9b9c17647 |
| 26.1.1 | 0.145.4+26.1.1 | API Base 2.0.3+ece06323b3; Networking API 6.3.0+50a808ceb3; Lifecycle Events 4.0.6+a9b9c176b3 |
| 26.1.2 | 0.155.3+26.1.2 | API Base 2.0.3+ece063234c; Networking API 6.3.2+a217cccf4c; Lifecycle Events 4.1.1+df84eb3d4c |

Only these three modules are embedded as Fabric nested JARs, with Fabric's `jars` metadata, original module metadata and licenses. A separate full Fabric API installation is not required. If a modpack already includes compatible Fabric API, Fabric Loader resolves the common module IDs. Minecraft, assets, native libraries and the Fabric Loader itself are not redistributed inside the companion.

The same exact-target JAR can be installed on a matching Fabric server to enable its common cosmetic relay entrypoint. The server does not load the client entrypoint or the client-only mixin list. Clients without this compatible Kapkan mod do not render its custom badges or capes. Server relay behavior and its verification are documented with the cosmetic implementation and actual game QA.

## Reproduce

Use a JDK 25 installation and Python 3.10+ with Pillow:

```sh
KAPKAN_JDK=/path/to/jdk-25 python3 companion/build.py
KAPKAN_JDK=/path/to/jdk-25 python3 companion/test.py --minecraft 26.1
KAPKAN_JDK=/path/to/jdk-25 python3 companion/test.py --minecraft 26.1.1
KAPKAN_JDK=/path/to/jdk-25 python3 companion/test.py --minecraft 26.1.2
python3 companion/check_compatibility.py
```

Select one build with `--minecraft 26.1.2`; repeat the argument to select several. `--prepare-only` downloads and verifies inputs without compiling. Outputs are in `app/`, per-target classes and classpaths in `companion/build/<version>/`, shared download cache in `companion/build/deps/`. Set `KAPKAN_DEPS` to relocate the cache. The old default `build/classpath.txt` and `build/classes` interfaces point only to the 26.1.2 target for retained test drivers.

`check_compatibility.py` checks exact Minecraft metadata, release version, Java classfile level, real entrypoint and mixin class presence, absence of accidentally bundled game classes, nested module hashes and transitive module closure. It is a packaging check, not a claim that gameplay or mixin execution succeeded. See actual game reports and screenshots for those results.
