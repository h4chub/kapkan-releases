# KapKan 1.0.5

A Fabric client mod with a compact menu, configurable gameplay HUD and optional server-assisted cosmetics. The existing desktop launcher keeps its Fabric dependency-recovery fixes and selects the exact matching client build.

## Install the mod

Use the JAR matching **26.1**, **26.1.1** or **26.1.2**, Fabric Loader **0.19.5 or newer**, and **Java 25**. Put it in the game's `mods` folder, replacing the previous KapKan companion. Required Fabric API modules are bundled. Keep your existing `.kapkan` data and preferences. No new 1.0.5 build is supplied for other Minecraft versions or loaders.

The launcher packages include all three client builds. On macOS, extract the ZIP and move KapKan.app into Applications. On Windows, run Setup, or extract the entire portable ZIP and open KapKan.exe. Keep KapKan.exe beside Resources. Existing worlds, mods, accounts and settings stay in `.kapkan`. Packages are unsigned.

## Controls

- **J** opens the menu. **Escape** closes it. Rebind KapKan controls in Minecraft's Controls menu or KapKan's Controls page. Existing bindings stay saved.
- The introduction appears once and stays dismissed. Help can open it again.
- Enable widgets in Features, then open the HUD editor to move, scale, align and reset them. Save separate HUD profiles.
- **C** is the default smooth zoom key. Toggle sprint uses **R** after enabling it; both keys can be rebound.
- Capes → **No Cape** removes the custom KapKan cape. Minecraft's own cape setting remains separate.

## Multiplayer cosmetics

For automatic synchronization, put the **same Minecraft-version KapKan JAR on the Fabric server and both clients**, then restart the server. No HTTP service is required for this relay. Players without KapKan can still join the server, but cannot see these custom cosmetics. Servers without the relay cannot provide automatic sharing.

The server assigns cosmetic records to the connected player's UUID; clients do not choose another player's identity. Badge/cape selections and No Cape changes propagate to compatible clients. PNG decoding and network work use bounded background queues. Your imported skins remain local.

The earlier HTTP presence service remains an optional, separately configured fallback. It is not a deployed public service or an account-ownership verification service.

## Build and verification

`KAPKAN_JDK=/path/to/jdk25 python3 companion/build.py` builds each exact target against pinned, checksum-verified official inputs. `companion/test.py --minecraft VERSION` runs companion tests. The desktop build uses JDK21; full packaging also requires Go and Python/Pillow. Set `KAPKAN_CLIENT_JDK` to JDK25 and `KAPKAN_JDK` to JDK21 for build-release.sh.

See COMPATIBILITY.md and QA-REPORT.md for the exact results and remaining device acceptance. Minecraft and Mojang libraries are build inputs, not included in source or mod deliverables.
