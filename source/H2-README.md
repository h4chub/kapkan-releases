# KapKan 1.0.0 | Private H2 build

Build ID: **ui-presence-h2**. Public version: **1.0.0**. Publication: **HOLD**.

This package upgrades the actual Swing desktop launcher and includes a new Fabric 26.1.2 cosmetic-presence companion. It uses the supplied H1 launcher engine, state model, native bootstrap, accounts and game-directory paths. There is no Electron, browser-shell migration, website change or auto-update publication.

## macOS

Extract `KapKan-macOS-1.0.0-H2.zip`. It contains a precompiled universal `KapKan.app` with Intel and Apple Silicon bootstrap slices and the real Java application, plus `Make-DMG.command`.

Close KapKan and Minecraft before replacing the application. Keep the previous app until you have tested H2. Your game data stays at the existing H1 path (normally `~/.kapkan/game` from the native bootstrap). The launcher still prepares managed Java on first use; internet is required.

Run the included `Make-DMG.command` on macOS if you want a DMG. It signs a temporary copy, validates it, and creates `dist/KapKan-macOS-1.0.0-H2.dmg` only when validation succeeds. No DMG was built on this Linux host. The app is not Developer-ID notarized. MacBook Neo/Air/Pro and Intel Mac are design targets; native acceptance testing remains pending.

## Windows

Run `KapKan-1.0.0-H2-Setup.exe` in the same Windows account. It is a genuinely compiled Windows x64 installer. It updates the managed application at `%LOCALAPPDATA%\Programs\KapKan`, retains its backup behavior, and preserves game data at the existing H1 path (normally `%USERPROFILE%\.kapkan\game`). A portable ZIP is also supplied.

The installer is unsigned and has not been executed on native Windows here. Do not delete `.kapkan`, reinstall Fabric or reorganize your user mods to apply this update.

## Badges

Target: **Minecraft 26.1.2, Fabric Loader 0.19.5, Java 25**. Later loader versions are allowed by dependency constraints but were not exercised. Older loaders and other game versions still use their normal launch flow, without automatically adding this new companion.

In Settings, enter your separately hosted presence-service URL and save. The default URL is empty: **no service is deployed and no presence is announced out of the box**. Both players need a compatible companion and the same endpoint and Minecraft server address.

- Share presence: opt in/out of all announcements and lookups.
- Show my own badge: optional own Tab badge and third-person self-name view.
- Guest/offline game profiles can participate. Microsoft OAuth configuration is unrelated to badges.

The white official double-K is supplied as a font glyph. The code prefixes the original Minecraft name component, preserving its existing formatting and layout. Remote names only receive a badge when the normal name tag already exists. Self view explicitly checks camera mode, invisibility, spectator state, team rules, HUD/name visibility and distance.

The companion is passed through Fabric's per-process `fabric.addMods` setting. It is not copied into the shared mods folder. The migration only retires two known legacy filenames when their SHA-256 exactly matches the bundled H1 Core; their bytes are retained in `.kapkan-managed/retired`. Other mods and modified files are untouched. The old 1.21.11 menu Core remains available only to its matching Fabric version.

The standalone module is `KapKan-Presence-26.1.2-H2.jar`. Normal users should let this launcher load it. Manual development launches need `-Dkapkan.launchSession=<unique-session-id>` and `-Dkapkan.presence.config=<path-to-presence.properties>`; without a launcher association it stays inactive. These are cosmetic association markers, not credentials or anti-spoofing guarantees.

See `presence-service/README.md` for server setup, privacy, capacity, expiry and limits. The backend is included separately and was not deployed.

## Important validation limit

The real launcher ran on a Linux virtual display and its screenshots are included. All five Minecraft target classes accepted the actual mixins in Fabric's real class loader. The attempted live three-client session failed to create an OpenGL context before clients could enter the world. **Overhead/Tab appearance and third-person self view are implemented, but successful multiplayer gameplay is not verified.** Read `QA-REPORT.md` before treating this as a release candidate.

## Build

Requirements: JDK 25, Go 1.23.2, Python 3.10+ with Pillow. Put `java`, `javac`, `go` and `python3` in PATH.

```sh
bash build-release.sh
```

Launcher code targets Java 21. Companion code targets Java 25 and compiles against the actual checksum-verified 26.1.2 client and pinned Fabric dependencies. The first companion build fetches real game libraries into `companion/build/deps`; set `KAPKAN_DEPS` to reuse an existing cache. No stub Minecraft API is used. Minecraft library jars are not included in the companion or source deliverable.

H1 supplied repair sources plus a compiled production baseline, rather than all original application sources. That baseline remains included. All new H2 UI, presence, integration and backend sources are included, with the exact original H1 tests. `H1-README.md`, `H1-QA-REPORT.md` and `H1-BUILD-INFO.json` retain the historical baseline information.

Core-development time: the supplied bundle stores the marker `unchanged`, but contains no numeric core-development timestamp to reproduce. No clock was introduced or incremented. Existing stored property values, including unknown keys, remain preserved by the retained state store.
