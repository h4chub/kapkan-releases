# KapKan 1.0.5 verification

All three shipped mod JARs were independently compiled against their exact official Minecraft release, then tested in actual graphical game processes with a matching dedicated Fabric server. Final evidence records each shipped JAR SHA-256.

| Minecraft | Loader tested | Java | Built | In-game |
| --- | --- | --- | --- | --- |
| 26.1 | Fabric 0.19.5 | 25 | PASS | Two independent clients, lifecycle/restarts, 35 feature checks |
| 26.1.1 | Fabric 0.19.5 | 25 | PASS | Two independent clients, lifecycle/restarts, 35 feature checks |
| 26.1.2 | Fabric 0.19.5 | 25 | PASS | Two independent clients, lifecycle/restarts, 35 feature checks |

## Actual game checks

The first-start introduction stays dismissed through menu closure, joins, Nether transitions, death/respawn, disconnect/reconnect and complete client restarts. Help replays it explicitly. Native J is the default; a separate actual UI test rebound it to K, verified the old key stopped opening the menu, and verified K survived a real process restart.

Each target passed bidirectional badge/cape reception for real server player identities, actual remote cape rendering, No Cape selected through the real menu, immediate local and remote removal, and persisted cosmetic/settings state after restarting both clients. Same-view screenshots show the remote player before and after removal. Tests omit the launcher-only association property, so standalone mod installation is covered.

Each target passed 35 actual gameplay feature assertions: FPS/coordinates, equipped armor and effects, forward/sneak/hunger/resume/off sprint behavior, smooth zoom/release/menu gating, HUD drag/wheel/arrow input, saved profiles and native X11 mouse/keyboard indicators. Actual UI tests added 62 interaction assertions and 5 process-restart assertions for search, filters, scrolling, toggles and native keybind persistence.

Actual menu screenshots cover 1280 × 800 at GUI scales 1/2/3 and 854 × 480 at GUI scale 2. A further 432 geometry/input-mapping assertions cover 48 resolution/scale combinations including 4K and ultrawide.

## Retained and deterministic checks

Each target passed 1,106 deterministic checks across cosmetics, settings migration/unknown preferences, HUD layout/profiles, onboarding, zoom, relay identity/No Cape/recovery and render policies. The optional HTTP service passed its retained Python tests. These are separate from the actual game checks above.

The launcher's release, interrupted first-install recovery, downloader recovery, isolated mod profiles, Windows classpath handling, hardening and maintenance regression suites passed. Fabric launch fixtures passed fresh, restored-JSON and linked-cache paths for 34 version IDs. Those fixtures are not Minecraft gameplay tests. Companion migration passed 82 fixture checks, including exact-version selection, old-mod backups, collisions, directory links and preservation of unrelated data. Go native installer/runtime fixture tests passed on Linux.

## Environment and limits

Actual gameplay ran on Linux x86_64, Java 25, Fabric Loader 0.19.5, Mesa llvmpipe software OpenGL and Xvfb. Two clients were separate real processes on one test host, using isolated local development identities. Automated test-only drivers are excluded from production JARs.

Native macOS/Windows installation and gameplay were not executed for 1.0.5. The desktop packages are cross-compiled and unsigned. Other GPU drivers, rendering mods/modpacks, Microsoft sign-in and external public servers are untested. There is no FPS/performance improvement claim; software-rendered test frame rates are not consumer-device benchmarks.

Only Minecraft 26.1, 26.1.1 and 26.1.2 on Fabric have 1.0.5 builds. Older versions need separate mapping/render/input adapters; Forge, NeoForge, Quilt, snapshots and other Minecraft versions are unsupported by this release. The launcher retains its older 1.21.11 Beta 1 companion, clearly marked as legacy.

Automatic cosmetics require matching KapKan clients and the matching mod JAR installed on the Fabric server. Unmodified clients do not render custom cosmetics. An optional legacy HTTP service remains available; no public service was deployed.

## Evidence

See qa/1.0.5/final-results.json and ACTUAL_TESTS.md, selected passing run directories, original game logs and 51 original screenshots. Per-target build/dependency and deterministic evidence is in companion/verification/1.0.5. The delivery QA archive contains the selected evidence without game binaries, assets, test worlds or compiled test mods.

Publication is not complete: the connected account has read-only access to h4chub/kapkan-releases.
