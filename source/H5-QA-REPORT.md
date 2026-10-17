# KapKan H5 Mac title-bar QA

Version **1.0.0**. Private build **ui-titlebar-h5**. Publication **HOLD**.

## Change and implementation

The user's two Mac screenshots show H4's main launcher and launch-log dialog with light native title bars over dark content. H5 adds the Java startup option `-Dapple.awt.application.appearance=NSAppearanceNameDarkAqua` only inside the native bootstrap's macOS branch, before starting the Java process. Both windows therefore inherit the requested dark native appearance from application startup. There is no replacement title bar, full-window-content setting, layout shift, operating-system preference change or added dependency. The native game-runtime preparation path and game JVM arguments are unchanged.

The supported property values and early-initialization requirement are documented by the Swing UI library maintainers: https://www.formdev.com/flatlaf/macos/#appearance-of-window-title-bars . H5 does not add FlatLaf or change Swing look and feel. The UI skill lookup did not yield a matching native-title-bar preset; this fix follows the documented Java/macOS setting and preserves native controls.

## Validation

`qa-h5/build.log` records compilation, retained Java/Go regression suites, backend cases, Mach-O layout tests and package integrity checks. Those checks are Linux-hosted fixtures and build validation, not native game tests.

`qa-h5/H4-PRESERVATION-AUDIT.json` compares this source tree and launcher JAR to the delivered H4 archive. All shared engine/account-store sources, the H4 profile-menu implementation, existing tests, companion/service sources and assets are unchanged. The main UI source changes only its private build label. The only functional source change is the macOS bootstrap argument. Core-development properties and game/account data paths are retained.

`qa-h5/MAC-PACKAGE-AUDIT.json` checks the final Mac ZIP, both architecture slices, exact dark-appearance option in each compiled slice, H5 build metadata and packaged JAR bytes. The application remains version 1.0.0 with bundle build 10005. The exact H2 companion SHA-256 is `14fd51171053b9c8d576205ae9c317d4515788bd407ab304b5b1dde1c4e27c8c`.

H4's 45 UI checks at each of 1× and 2× remain historical evidence. They are not repeated or relabelled as a native Mac title-bar test. No fabricated dark-title-bar screenshot is supplied.

## Version coverage

| Area | Evidence | Limit |
| --- | --- | --- |
| Shared download/install/launch repairs | Retained and regression checked across common launcher paths | Not restricted to 26.1.2; not proof of all versions |
| Fabric 26.1.2 and 1.21.11 | Existing 53-assertion pipeline suite, synthetic library/profile fixtures and synthetic KnotClient | No real gameplay acceptance from those fixtures |
| Forge/NeoForge | Installer/profile/version-matching regression fixtures | Every real loader/version combination remains unverified |
| Real 26.1.2 Fabric client | Historical H2 process attempt with actual classes | Blocked before world entry by host OpenGL |
| Native Mac app startup | H4 main window and launch-log dialog visible in owner's supplied screenshots | Confirms those windows opened; not gameplay or H5 title-bar appearance |
| H5 dark native title bars | Correct early startup option compiled into both Mac slices | Native visual check still pending |

## Limits

H5 was built on Linux. It has not been executed on a native Mac or Windows device here. The Mac ZIP is the requested deliverable; any Windows artifacts produced by the shared build remain internal. No DMG, Developer ID notarization or Authenticode signature was produced. Gameplay, live badge rendering, Microsoft sign-in acceptance, universal loader compatibility and FPS remain unverified. The presence service stays undeployed. Nothing was pushed to GitHub, a website, Vercel, a public release or an update channel.
