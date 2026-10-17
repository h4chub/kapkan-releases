# KapKan H3 polish QA

Version **1.0.0**. Private desktop build **ui-polish-h3**. Publication **HOLD**.

## Actual desktop execution

The running Swing application was checked on Linux/Xvfb at 1180×760 and 820×560, with 1× and 2× UI scaling. Each scale passed **35 checks**. The final pass uses the JAR extracted into the compiled Mac app package, not a replacement loose-source build. Actual captures are in `qa-h3/` and `qa-h3/scale-2/`.

Checks cover the retained five pages, accessible account label, Play handler, all bottom controls inside the smallest window, full Play-label space, dropdown selection visibility and the honest unconfigured-service state. New Robot keyboard checks open the account menu with Down, dismiss with Escape, and switch with arrows/Enter. They verify the real stored selection, matching header/dropdown/launch-summary names, expanded/collapsed accessibility state, disabled switching during launch, Manage accounts, long names and a bounded menu.

The test uses isolated temporary guest profiles and a version-list fixture. Screenshots are from the implemented desktop application; they are not generated mockups. Native window chrome and fonts can differ on macOS/Windows.

During development the minimum-width Play label was found to clip after adding its icon; its field now reserves 126 pixels and the regression check covers that condition. A persistence fixture initially used an invalid guest-profile identifier; it was corrected to create profiles through the real H1/H2 account-store method.

## Build and regression evidence

`qa-h3/build.log` records the current release build and retained regression suites: 33 hardware/runtime policy assertions, first-install recovery/preservation, 46 download assertions, 7 Windows path fixtures, 47 hardening assertions, 53 Fabric-flow fixtures, 20 companion-integration assertions, 9 backend cases, Go tests and 8 Mach-O layout tests. These are automated checks on Linux. Windows-path and Fabric-flow fixtures do not establish successful native gameplay.

`dist/ARTIFACT-AUDIT.json` and the copied audit in `qa-h3/` record archive integrity, byte-identical packaged launcher/module JARs, public/private versions, retained original images and native binary layout. The Windows installer's embedded payload is also compared with the exact newly packaged payload, preventing an old installer from being relabelled.

Launcher: Java 21 bytecode, built with Amazon Corretto 21.0.12.12.1. Native launchers/installer: Go 1.23.2. Companion: the exact H2 JAR, SHA-256 `14fd51171053b9c8d576205ae9c317d4515788bd407ab304b5b1dde1c4e27c8c`, retained without rebuilding. H2's companion transport/render-policy and real Fabric-hook results are historical and were not rerun for this UI-only pass.

## Preserved behavior and assets

Only desktop UI, its tests, build markers, packaging and documentation change. The H1/H2 engine source, badge companion/service source, original artwork and generated white logo bytes are compared against H2. Core-development properties and existing user data paths are retained. No user worlds/mods/accounts were accessed by the test; temporary test stores were used.

The new account menu uses the retained account store. Unknown names are not invented as live users. Guest/Microsoft text describes the profile type only. No avatars, ownership requirement, online-status claim, continuous animation or additional polling were introduced.

## Limits

- Mac app contains compiled Intel and Apple Silicon slices; Windows installer is a compiled x64 PE. Neither was executed on a native Mac/Windows device in this pass.
- No DMG was created on Linux. The Mac package includes the real app and Mac DMG script. No Developer ID notarization or Windows Authenticode signing was performed.
- Minecraft was not rerun. H2's actual-client attempt was blocked before world entry by OpenGL. Live overhead/Tab/self badge rendering remains unverified. Read `H2-QA-REPORT.md` for the original scope.
- The presence service remains undeployed with the default endpoint empty. Existing configured preferences are preserved. No FPS or hardware compatibility gains are claimed.
- Nothing was pushed to GitHub, the website, Vercel, a public release or an update channel.
