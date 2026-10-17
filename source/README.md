# KapKan 1.0.6

A Fabric client mod and desktop launcher with a searchable menu, configurable gameplay HUD, smooth zoom, toggle sprint and optional server-assisted cosmetics.

## Install

Use the exact Minecraft **26.1**, **26.1.1** or **26.1.2** JAR with Fabric Loader **0.19.5+** and **Java 25**. The desktop packages include all three client builds. Other versions/loaders remain unsupported by this update.

On macOS, extract the ZIP and move KapKan.app into Applications. The Mac app uses a local capability signature; it is not Developer ID notarized. Microphone access requires macOS approval when voice chat first requests input. On Windows, run Setup or extract the complete portable ZIP and keep KapKan.exe beside Resources. Existing accounts, worlds, controls, mods and preferences stay in `.kapkan`.

## Controls and settings

Press **J** for KapKan; rebind in Minecraft Controls or KapKan Controls. Enable individual widgets in Features and position them in the HUD editor. **C** defaults to zoom; **R** defaults to toggle sprint after enabling it. Help replays the introduction. Capes → No Cape removes the custom KapKan cape.

The launcher's Game performance page includes **Use rendering optimizations**. A change applies at the next game start. Existing copies and declared conflicts take precedence. The launcher does not change your graphics options. Module licenses and exact corresponding sources are included in Resources/performance; see [performance/NOTICE.txt](performance/NOTICE.txt).

## Cosmetics

Automatic badge/cape sharing requires matching KapKan JARs on the Fabric server and clients. The server assigns identities; clients cannot publish for another UUID. Players without KapKan can join but do not render these custom cosmetics. The legacy HTTP fallback is optional; no public cosmetic service is provided.

## Build and verification

Use JDK 25 for the companion and JDK 21 for the desktop. Set `KAPKAN_CLIENT_JDK` and `KAPKAN_JDK`, with Go and Python/Pillow available, then run `build-release.sh`. Inputs are pinned and checksum verified. macOS packaging signs and verifies the actual app before creating its ZIP. `KAPKAN_SIGN_IDENTITY` selects an existing Developer ID; otherwise local ad-hoc signing is used. Signing identity setup and notarization are separate.

See [QA-REPORT.md](QA-REPORT.md) for current verification scope and [history/1.0.5](history/1.0.5/) for historical records. No general FPS gain is promised.
