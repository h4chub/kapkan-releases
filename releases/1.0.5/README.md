# KapKan 1.0.5

Source and QA import prepared for review on `codex/kapkan-1.0.5`. No public GitHub release, website deployment or public cosmetic service is created by this change.

## Included source and mod builds

| Minecraft | Java | Tested loader | Exact-version mod |
| --- | --- | --- | --- |
| 26.1 | 25 | Fabric 0.19.5 | [KapKan-1.0.5-mc26.1-fabric.jar](../../source/app/KapKan-1.0.5-mc26.1-fabric.jar) |
| 26.1.1 | 25 | Fabric 0.19.5 | [KapKan-1.0.5-mc26.1.1-fabric.jar](../../source/app/KapKan-1.0.5-mc26.1.1-fabric.jar) |
| 26.1.2 | 25 | Fabric 0.19.5 | [KapKan-1.0.5-mc26.1.2-fabric.jar](../../source/app/KapKan-1.0.5-mc26.1.2-fabric.jar) |

Use only the JAR matching the game version. Required Fabric API modules are bundled. Automatic multiplayer cosmetics require matching KapKan clients and the exact-version JAR on a matching Fabric server. See [installation and controls](../../source/README.md) and [compatibility](../../source/COMPATIBILITY.md).

The update fixes repeated onboarding, adds a centered searchable menu, shared HUD editing/profiles, smooth zoom, toggle sprint, server cosmetic relay and persistent No Cape. The launcher retains its Fabric repair logic and selects the matching 1.0.5 client build.

## Verification inherited from the completed build

The recovered final evidence records real graphical Minecraft clients on **Linux x86_64, Java 25, Fabric Loader 0.19.5, Xvfb and Mesa llvmpipe software OpenGL**:

- Each of 26.1, 26.1.1 and 26.1.2: two separate clients and a matching dedicated server; badge/cape and No Cape synchronization, onboarding, GUI scales, dimension changes, death/respawn, reconnect and full process restart/settings persistence.
- Each target: 35 discrete gameplay feature assertions covering live HUD data, sprint eligibility/state, zoom, editor input/profiles and actual X11 key/mouse indicators.
- 26.1.2: 62 UI interaction assertions plus 5 process-restart assertions for search, filters, scrolling, saved toggles and key rebinding. Smoke checks additionally cover 26.1 and 26.1.1.
- Each target: 1,106 deterministic companion checks. Geometry/input checks cover 432 assertions across 48 resolution/scale combinations.
- Retained launcher regression suites, 34-version fresh/restored/linked-cache Fabric fixtures, 82 companion migration fixture checks and Linux Go installer/runtime fixtures passed. The final payload fixture log records `TestBuiltPayloadUpgrade` passing.

These results were produced in the prior build session, not rerun during this source import. The three 1.0.5 mod JAR hashes were recomputed from the recovered archive and match [final-results.json](../../source/qa/1.0.5/final-results.json). See [QA-REPORT.md](../../source/QA-REPORT.md) and [ACTUAL_TESTS.md](../../source/qa/1.0.5/ACTUAL_TESTS.md) for evidence and test scope.

Native macOS/Windows installation and gameplay were not executed for this release. Desktop packages were cross-built and are unsigned. Other GPU drivers, rendering mods/modpacks, Microsoft sign-in and external public servers are untested. Versions outside 26.1/26.1.1/26.1.2 and loaders other than Fabric are not verified. No FPS/performance improvement is claimed.

## Delivery metadata

[Delivery.json](Delivery.json) and [SHA256SUMS.txt](SHA256SUMS.txt) record the original deliverables. Desktop packages, the original source ZIP and the standalone QA ZIP are not duplicated in this review tree. Their hashes are inherited delivery records; the original source ZIP was recovered and its hash verified before extraction. The source tree excludes compiled QA `.class` output and game/runtime inputs. No package was rebuilt by this import.

The recovered source ZIP remains a historical artifact. The tracked tree adds current publication metadata and preserves the earlier metadata as history, so a fresh ZIP of this tree will have a different hash. Old publication/access statements in retained evidence describe the earlier session.
