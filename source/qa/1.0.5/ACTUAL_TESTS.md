# KapKan 1.0.5 actual-game verification

All advertised Minecraft targets were compiled separately against their exact official game JAR and then executed in real graphical Minecraft clients. The dedicated server and clients used isolated local development identities. These are actual game screenshots and test-only automated input drivers, not UI previews or simulated players.

| Minecraft | Loader tested | Java | Build | Actual in-game verification |
|---|---|---|---|---|
| 26.1 | Fabric 0.19.5 | 25 | `KapKan-1.0.5-mc26.1-fabric.jar` | Two separate clients: cosmetics, No Cape, lifecycle and process restart; 35 gameplay feature checks |
| 26.1.1 | Fabric 0.19.5 | 25 | `KapKan-1.0.5-mc26.1.1-fabric.jar` | Two separate clients: cosmetics, No Cape, lifecycle and process restart; 35 gameplay feature checks |
| 26.1.2 | Fabric 0.19.5 | 25 | `KapKan-1.0.5-mc26.1.2-fabric.jar` | Two separate clients: cosmetics, No Cape, lifecycle and process restart; 35 gameplay feature checks |

## Verified in the game

- First introduction is committed to preferences before display, then stays dismissed through actual joins, menu closure, Nether transitions, death/respawn, disconnect/reconnect and both client process restarts. Help can open it again.
- Two independent clients receive badges and cape images for the server-provided player UUID. Original screenshots show the same remote player from behind before and after No Cape, with the badge retained.
- Menus render at 1280 × 800 with GUI scales 1, 2 and 3, plus 854 × 480 at GUI scale 2. Escape works and HUD profiles restore positions.
- Real FPS, coordinates, armor durability and active-effect data; actual sprint eligibility, explicit sneak stop, smooth zoom/release; draggable/scalable HUD with keyboard movement and saved profiles.
- Real X11 left/right mouse and W key events reach the click/keystroke indicators.
- Search/filter/scroll, immediate saved toggles and actual Controls rebinding are exercised separately. The native Minecraft options key binding is then verified after terminating and restarting the actual client process.

## Evidence

- 26.1 acceptance: `qa/1.0.5/actual-acceptance-26.1-20261016T141013Z` (`PASS.txt`, `run.json`, assertion markers, original game logs and PNG screenshots).
- 26.1 features: `qa/1.0.5/actual-features-26.1-20261016T141255Z` (`PASS.txt`, `run.json`, assertion markers, original game logs and PNG screenshots).
- 26.1 smoke: `qa/1.0.5/actual-smoke-26.1-20261016T140717Z` (`PASS.txt`, `run.json`, assertion markers, original game logs and PNG screenshots).
- 26.1.1 acceptance: `qa/1.0.5/actual-acceptance-26.1.1-20261016T141025Z` (`PASS.txt`, `run.json`, assertion markers, original game logs and PNG screenshots).
- 26.1.1 features: `qa/1.0.5/actual-features-26.1.1-20261016T141312Z` (`PASS.txt`, `run.json`, assertion markers, original game logs and PNG screenshots).
- 26.1.1 smoke: `qa/1.0.5/actual-smoke-26.1.1-20261016T140717Z` (`PASS.txt`, `run.json`, assertion markers, original game logs and PNG screenshots).
- 26.1.2 acceptance: `qa/1.0.5/actual-acceptance-26.1.2-20261016T140433Z` (`PASS.txt`, `run.json`, assertion markers, original game logs and PNG screenshots).
- 26.1.2 features: `qa/1.0.5/actual-features-26.1.2-20261016T140333Z` (`PASS.txt`, `run.json`, assertion markers, original game logs and PNG screenshots).
- 26.1.2 ui: `qa/1.0.5/actual-ui-26.1.2-20261016T141413Z` (`PASS.txt`, `run.json`, assertion markers, original game logs and PNG screenshots).

`final-results.json` records exact production JAR SHA-256 values and selected passing run paths. Earlier preliminary runs and superseded artifact hashes are excluded from the final evidence set.

## Limits

- Windows and macOS native Minecraft runtime testing was not performed.
- Additional rendering mods, Microsoft account sign-in and external public servers were not tested.
- No performance improvement or FPS boost is claimed; software rendering was used for correctness.
- Minecraft versions outside 26.1, 26.1.1 and 26.1.2 and loaders other than Fabric are not verified.

Custom badges/capes require compatible KapKan clients and the exact-version KapKan JAR on the Fabric server for the server relay path. Unmodified clients do not gain cosmetic rendering automatically. Public relay infrastructure was not deployed by these tests.

Do not distribute `qa/1.0.5/runtime`, local test server/world directories, compiled probe classes or test-only mod JARs. Minecraft itself is a verified test input, not part of the release.
