# KapKan 1.0.0 | Private H5 Mac title-bar update

Build ID: **ui-titlebar-h5**. Public version: **1.0.0**. Publication: **HOLD**.

H5 changes the macOS window appearance to dark before the launcher creates its UI. It applies to the main window and the launch-log dialog shown in the supplied screenshots. Native window titles, traffic-light controls, dragging and resizing stay native. The H4 profile menu, layout, artwork, logo, game engine, account store and user-data paths are retained. Only the private build labels advance to H5.

## Try on Mac

Download and extract `KapKan-macOS-1.0.0-H5.zip`. Close KapKan and Minecraft, then replace the old app in Applications with the included `KapKan.app`. Open it normally. It includes native Apple Silicon and Intel launcher slices. The first app launch supplies the appearance setting; launching the internal JAR directly does not apply this native-bootstrap update.

The package remains unsigned/not notarized. No DMG was produced here; `Make-DMG.command` remains available for macOS. H4's actual Mac startup is visible in the owner's screenshots. H5's title-bar rendering still needs a native Mac check, because this build environment is Linux. No simulated Mac screenshot is presented as a verified result.

Game data remains at the existing location, normally `~/.kapkan/game`. Do not remove that folder when replacing the app.

## Do other Minecraft versions work?

The earlier download, installation and launch fixes are shared launcher code, not a patch restricted to 26.1.2. They are unchanged in H5. That does not establish that every Minecraft version and every loader combination works.

Existing Fabric pipeline checks cover synthetic fixtures for **26.1.2 and 1.21.11**. Forge/NeoForge installer and profile checks also use fixtures. These checks do not prove a real game reaches a world. The earlier actual 26.1.2 Fabric-client attempt stopped before world entry because this host lacked a usable OpenGL context. Other version/loader combinations remain unverified unless separately tested on a device.

The current H2 presence/badge companion specifically targets Minecraft 26.1.2 / Fabric 0.19.5 / Java 25 and is retained byte-for-byte. This module's target is separate from the launcher's broader version selection. Its service remains undeployed with an empty default endpoint. No gameplay, badge-rendering, FPS or universal compatibility claim is added by H5.

## Build

Desktop/native packaging with the exact existing companion:

    KAPKAN_REUSE_COMPANION=1 bash build-release.sh

Requires JDK 21+, Go 1.23.2 and Python with Pillow. A full companion rebuild additionally requires JDK 25 and the pinned Minecraft/Fabric dependencies. The existing H4 UI suite and historical records remain in the source bundle; no new UI behavior was introduced or test suite duplicated for this one-setting Mac change.

See `CHANGELOG-H5.md`, `QA-REPORT.md` and `qa-h5/` for this update. Earlier H1-H4 documents remain historical. The source archive retains the supplied compiled production baseline; original pre-H1 sources are not all available. Core-development properties remain untouched. Nothing was published or deployed.
