# KapKan 1.0.6 verification

This report describes checks rerun on the 1.0.6 code. Historical 1.0.5 gameplay results are retained in history/1.0.5 and qa/1.0.5; they do not establish new native acceptance for this update.

## Current checks

- Each exact Minecraft 26.1, 26.1.1 and 26.1.2 companion compiles with JDK 25 against pinned official inputs. Each passes the companion deterministic suite, including 103 new render-cache/settings assertions per target.
- Render-cache pixel/metrics checks cover scale, text, color, bounded eviction and sprite keys. A warmed text-width microbenchmark on this Mac reduced repeated lookup CPU time from 63.043 to 15.339 ns/lookup and measured allocation from 68.5 to 0 bytes/lookup. These are Java method measurements, not Minecraft FPS.
- The optional bundle integration passes 51 filesystem/argument checks: exact resource lookup, direct/nested/provided IDs, conflicts, opt-outs, binary tampering and user data preservation. Official binary checksums and exact corresponding source archives accompany all three modules.
- Launcher release, first-install recovery, downloader recovery, mod profiles and Windows Fabric classpath fixtures passed. Hardening passes 47 assertions, including actual timed subprocess termination. Maintenance passes 22 checks, including symlinked parent/future game-directory protection; Fabric preflight and fresh/restored/linked profile flows pass across 34 version IDs. Real published 1.0.5 to 1.0.6 migration passes for all three exact builds (103 integration checks total), with backups and user-data sentinels unchanged.
- Native macOS arm64 game checks pass for all three exact targets: all bundled modules load and enter fresh single-player worlds; 26.1.2 renders a fixed 200-entity scene. The initial timings overlapped compilation and are explicitly excluded. No general FPS conclusion is drawn from these samples; a controlled-scene comparison, if available, is recorded separately under qa/1.0.6/game. Native runtime/payload tests, Windows installer compilation and the final archive/signature/entitlement audit pass.

## Scope and limits

No graphics options are lowered, no FPS marketing message is added, and no general performance gain is claimed. Frame times depend on scene, hardware, user mods and settings. Bundled modules can be disabled on the next launch in Game performance settings; existing user copies and declared conflicts take precedence.

The screenshot's Simple Voice Chat warning requires both the Mac app's microphone capability and a compatible managed Java runtime. Static metadata/signature checks do not prove interactive microphone capture. macOS consent remains under the user's control; no privacy permission database is changed.

Native Windows installation, Microsoft sign-in, voice capture after OS consent, other GPUs and arbitrary modpacks are not covered by deterministic fixtures. Mac local capability signing is separate from Developer ID notarization. No public cosmetics service is deployed.
