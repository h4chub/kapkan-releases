# KapKan 1.0.0 | Private H3 polish build

Build ID: **ui-polish-h3**. Public version: **1.0.0**. Publication: **HOLD**.

H3 refines the approved H2 desktop layout. The top-right account block becomes a compact name, profile type and chevron. It opens a keyboard-accessible menu for switching saved profiles and opening Accounts. The sidebar uses quiet inactive rows, a restrained crimson selection and keyboard-only focus rings. The launch controls, form fields and checkboxes share softer edges and consistent states.

The original artwork and double-K geometry are unchanged. Play, Mods, Accounts, Explore and Settings stay in place. No avatars, extra effects, animation loops or background requests were added. The launch engine, paths, H1 repairs, account storage, performance settings and H2 companion/service sources are retained.

## Install

**Mac:** extract `KapKan-macOS-1.0.0-H3.zip`. It includes a compiled universal `KapKan.app` for Intel and Apple Silicon, plus `Make-DMG.command`. Close KapKan and Minecraft before replacing the app. The previous H2 app can be kept as a backup. No DMG was built here; the script creates one on macOS after its validation steps. No Developer ID notarization was performed.

**Windows:** run `KapKan-1.0.0-H3-Setup.exe` under the same Windows account. This is a compiled x64 installer using the retained per-user installation and backup flow. The installer is unsigned and was not executed on native Windows here.

Game data stays at the existing H1/H2 path, normally `~/.kapkan/game`. Do not delete `.kapkan` or move mods/worlds to install this update. Managed Java preparation remains part of the retained bootstrap.

## Account control

Click the name at the top right, or focus it and press Down, Space or Enter. Choose an account with the mouse or arrow keys and Enter. Escape dismisses the menu. The current account has a checkmark. A maximum of six profiles appear in the compact menu; Manage accounts opens the full existing account page. The menu is disabled during launch/game activity, matching the Play account selector.

Profile type is a local account label, not an online-status or ownership claim. No profile images are loaded.

## Badges

H3 retains the exact `KapKan-Presence-26.1.2-H2.jar` from H2. Its name and `1.0.0+h2` module version are intentional; the desktop polish does not rebuild or rename the module.

Target: Minecraft 26.1.2, Fabric Loader 0.19.5 and Java 25. Presence still needs a separately configured service URL. The default endpoint is empty, the UI says Service not configured, and nothing is deployed. See `presence-service/README.md` and `H2-README.md` for setup and feature details.

Real badge rendering remains unverified from H2's OpenGL-blocked gameplay attempt. No new game-rendering or performance claims are made by this desktop polish build.

## Build

For the same desktop-only build, use JDK 21+, Go 1.23.2 and Python 3.10+ with Pillow:

```sh
KAPKAN_REUSE_COMPANION=1 bash build-release.sh
```

This verifies and retains the exact H2 module and supplied image resources. The default full build, `bash build-release.sh`, additionally requires JDK 25 and downloads the pinned actual Minecraft/Fabric dependencies to rebuild and test the companion.

For the real desktop UI checks on Linux with Xvfb and a JDK:

```sh
python3 tools/run-ui-qa.py
```

An optional `--jar /absolute/path/to/shipped/KapKan-Launcher.jar` verifies the packaged application. `JAVA_BIN`, `JAVAC_BIN` and `XVFB_BIN` can specify tools; their shared-library dependencies must be available. The test uses temporary guest profiles, not a personal account database.

H1 supplied a compiled production baseline plus repair sources. That baseline and every new H2/H3 source are included. The source bundle is not a claim that all original pre-H1 application sources were available.

Core-development time remains untouched. The supplied baseline contains the marker `unchanged`, not a numeric timestamp to invent. No GitHub push, website, update channel or service deployment is authorized or performed.

See `QA-REPORT.md`, `CHANGELOG-H3.md` and `qa-h3/` for this pass. `H2-QA-REPORT.md`, `qa-h2/` and the H1 reports remain historical evidence.
