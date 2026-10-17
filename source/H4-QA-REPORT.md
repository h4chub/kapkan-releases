# KapKan H4 account-menu QA

Version **1.0.0**. Private desktop build **ui-accounts-h4**. Publication **HOLD**.

## Actual desktop execution

The real Swing launcher was checked on Linux/Xvfb at 1180×760 and 820×560, with 1× and 2× UI scaling. Each scale passed **45 checks**. The final pass uses the launcher JAR inside the compiled Mac app package. Captures and logs are in `qa-h4/` and `qa-h4/scale-2/`. `KapKan-H4-Account-Detail.png` captures the actual display at native resolution.

Checks cover the retained five pages, accessible account label, single Play handler, usable controls at minimum window size, version selection visibility and the unconfigured-service state. Robot interaction opens the account menu with Down and mouse click, dismisses with Escape/outside click, and switches saved profiles with arrows/Enter. The checks confirm account persistence and matching names in the header, Play selector and launch summary; expanded/collapsed accessibility state; disabled switching during launch; Manage accounts; long names; and all six visible profile rows plus the shadow fitting in the smallest frame.

H4 also checks a one-profile menu, neutral pointer-open rows, keyboard-only focus styling and a forced native-popup fallback. The latter verifies transparency handling or opaque fallback and restoration of cached-window appearance after dismissal. During development this check exposed an ordering issue: setting a native window's background also updates ancestor opacity. Capturing opacity before the change and restoring it after the background fixed the issue.

Tests use isolated temporary guest profiles and a version-list fixture. Images show the implemented desktop app, not generated mockups. Linux/Xvfb checks do not establish native macOS/Windows rendering or gameplay; native fonts and chrome can differ.

## Build and regression evidence

`qa-h4/build.log` records the retained release-build regression suites: 33 hardware/runtime policy assertions, first-install recovery/preservation, 46 download assertions, 7 Windows path fixtures, 47 hardening assertions, 53 Fabric-flow fixtures, 20 companion-integration assertions, 9 backend cases, Go tests and 8 Mach-O layout tests. These run on Linux; path and loader fixtures are not native gameplay acceptance.

`qa-h4/ARTIFACT-AUDIT.json` records archive integrity, exact packaged launcher/module JAR bytes, public/private versions, original images and native binary layout. `FINAL-INSTALLER-AUDIT.json` checks the Windows installer's exact embedded payload and checksum, packaged metadata, JARs and documentation. `H3-PRESERVATION-AUDIT.json` records unchanged engine sources, companion/service sources, assets and existing tests compared with the delivered H3 source archive.

Launcher: Java 21 bytecode, built with Amazon Corretto 21.0.12.12.1. Native launchers/installer: Go 1.23.2. Companion: byte-identical H2 JAR, SHA-256 `14fd51171053b9c8d576205ae9c317d4515788bd407ab304b5b1dde1c4e27c8c`. H2's companion transport/render-policy and real Fabric-hook checks remain historical evidence; the companion was not rebuilt for this menu pass.

## Scope and preservation

Changes are limited to the desktop account trigger/popup, its checks, build markers, packaging and documentation. The rest of H3's approved interface is retained. Engine/account-store methods and user-data paths are unchanged. The test did not use personal worlds, mods or accounts. Core-development properties retain the existing marker.

Current profile and alternative profiles use the real retained account store. Guest/Microsoft describes profile type only. No avatars, ownership requirement, online-status indicator, animation loop or new polling was introduced. Original artwork and official double-K geometry remain unchanged.

## Limits

- Mac app contains compiled Intel and Apple Silicon slices; the Windows installer is a compiled x64 PE. Neither was run on a native Mac/Windows device in this pass.
- No DMG was created on Linux. The Mac package includes the app and DMG creation script. Developer ID notarization and Windows Authenticode signing were not performed.
- Minecraft was not rerun. H2's actual-client attempt stopped before world entry because of OpenGL. Live overhead/Tab/self badge rendering remains unverified; see `H2-QA-REPORT.md`.
- Presence service remains undeployed and its default endpoint empty. Existing configured preferences are preserved. No FPS or hardware compatibility gains are claimed.
- Nothing was pushed to GitHub, a website, Vercel, a public release or an update channel.
