# KapKan 1.0.0 | Private H4 account-menu build

Build ID: **ui-accounts-h4**. Public version: **1.0.0**. Publication: **HOLD**.

H4 refines the account menu shown in the owner's H3 screenshot. The header becomes a single-line name and chevron, with a quiet hover state. The popup has rounded corners, a soft shadow, a clear current-account summary, compact alternative profiles and a separate Manage accounts action. Guest/Microsoft labels identify profile type; no avatars or online-status claims are introduced.

The rest of the approved H3 composition stays in place. Original artwork, double-K geometry, launch engine, H1 repairs, data paths, account storage and H2 companion/service sources are preserved. There are no animation loops or new background requests.

## Install

**Mac:** extract `KapKan-macOS-1.0.0-H4.zip`. It includes a compiled universal `KapKan.app` for Intel and Apple Silicon, plus `Make-DMG.command`. Close KapKan and Minecraft before replacing the app. Keep the previous app as a backup if desired. No DMG was built on Linux; the script creates one on macOS after validation. The app has not been Developer ID notarized or executed on a native Mac in this pass.

**Windows:** run `KapKan-1.0.0-H4-Setup.exe` under the same Windows account. The compiled x64 installer retains the per-user installation and backup flow. It is unsigned and has not been executed on native Windows in this pass.

Game data stays at the existing H1/H2 path, normally `~/.kapkan/game`. Do not delete `.kapkan` or move mods/worlds to install this build. Managed Java preparation uses the retained bootstrap.

## Account control

Click the name at the top right, or focus it and press Down, Space or Enter. Choose a saved account with the mouse or arrow keys and Enter. Escape or a click outside dismisses the menu. Keyboard navigation has explicit focus rings. The current account appears first with a checkmark; other profiles appear under Switch account. A maximum of six profiles appear in the compact popup, with all profiles available through Manage accounts. Switching remains disabled during launch/game activity.

The popup fits within the visible launcher frame where possible and stays within the usable display area. Its normal Swing popup composites over the real artwork. A native-window fallback restores its transparency settings after dismissal so another menu cannot inherit them.

## Badges

H4 retains the exact `KapKan-Presence-26.1.2-H2.jar`, SHA-256 `14fd51171053b9c8d576205ae9c317d4515788bd407ab304b5b1dde1c4e27c8c`. Its `1.0.0+h2` module version is intentional. Target: Minecraft 26.1.2, Fabric Loader 0.19.5 and Java 25. The service is not deployed and its default endpoint remains empty. Existing configured preferences are preserved. See `presence-service/README.md` and `H2-README.md` for the retained feature details.

H2's real-client attempt was blocked before world entry by OpenGL. Live badge rendering remains unverified. This menu refinement makes no game-rendering, FPS or hardware compatibility claims.

## Build and verification

The desktop build uses JDK 21+, Go 1.23.2 and Python 3.10+ with Pillow:

    KAPKAN_REUSE_COMPANION=1 bash build-release.sh

That command verifies and retains the exact H2 module and supplied resources. The default full build additionally requires JDK 25 and downloads the pinned Minecraft/Fabric dependencies to rebuild and test the companion.

The real Swing checks require Linux/Xvfb and a JDK:

    python3 tools/run-ui-qa.py --build h4

The optional `--jar /absolute/path/to/shipped/KapKan-Launcher.jar` checks the packaged application. `JAVA_BIN`, `JAVAC_BIN` and `XVFB_BIN` can specify tools; their shared libraries must be available. Tests use temporary guest profiles and a version-list fixture, not a personal account database. The account-detail capture uses the display's native screenshot resolution.

H1 supplied a compiled production baseline plus repair sources. That baseline and every H2/H3/H4 source are included; not all original pre-H1 sources were available. Core-development time remains untouched: the supplied marker is `unchanged`, not a numeric timestamp to invent.

No GitHub push, website publication, update-channel change or service deployment was performed. See `QA-REPORT.md`, `CHANGELOG-H4.md` and `qa-h4/` for this pass. Earlier numbered reports and QA folders remain historical evidence.
