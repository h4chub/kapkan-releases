KapKan 1.0.3 adds separate mod collections for each Minecraft version and loader.

## What changed

- The Mods tab now follows the Minecraft version and loader selected on Play.
- **Open folder** opens that collection. **Choose folder** saves a custom location for that profile. **Use default** switches back without deleting custom files.
- Existing mods are copied once into your previously selected profile without overwriting files. The original collection stays in place; other versions and loaders start separately.
- Existing worlds, packs, screenshots and mod configuration remain in their original locations. Controls and game options are retained per profile.
- Missing custom folders show an error instead of silently loading another collection. Folder changes are disabled while Minecraft is preparing or running.

Use mods made for the selected Minecraft version and loader. Vanilla does not load mods. Choosing a custom folder uses its contents directly; it does not move the old collection.

## Update

**Already on 1.0.2?** Open KapKan with startup update checks enabled, or use **Settings > Check for updates**. The update notice opens the correct download in your browser. Installation remains manual. People on 1.0.0 or 1.0.1 should download this release manually.

**macOS:** download `KapKan-macOS-1.0.3.zip`, close Minecraft and KapKan, extract the ZIP and replace KapKan.app in Applications.

**Windows x64:** download `KapKan-1.0.3-Setup.exe`, close Minecraft and KapKan, then run the installer. Keep your existing game data folders. `SHA256SUMS.txt` identifies the exact downloads.

## Beta scope

Folder and launch fixtures, real Swing UI checks at 1x and 2x, regression tests, native bootstrap tests and package audits passed. The existing 1.0.2 checker selects the 1.0.3 Mac and Windows assets in release-metadata tests. Exact native Mac/Windows installation, folder links and Minecraft gameplay still need device acceptance. These packages are unsigned and macOS is not notarized.

The in-game companion remains Minecraft 26.1.2 / Fabric 0.19.5+ / Java 25. Other offered versions/loaders do not all have companion features or completed gameplay testing. Microsoft sign-in is not preconfigured with a KapKan-owned application ID. Shared capes/badges need a configured HTTPS service; none is included by default. Imported skins are local. Friends, managed servers and Premium remain unavailable.
