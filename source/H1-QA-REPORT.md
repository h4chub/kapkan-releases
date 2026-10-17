# KapKan 1.0.0 Hardening H1: tests and release limits

Publication: **HOLD**. Application version stays 1.0.0. H1 identifies this candidate
so it cannot be confused with earlier differently-built 1.0.0 installers. No
GitHub/Vercel action or live download-link change is made.

## Environment

Linux x86-64, OpenJDK 21.0.11, Go 1.23.2, Python 3. The official vendor binaries
and live Minecraft CDN services were not accessible through container outbound
DNS. There is no native Windows or macOS session. Package compilation and local
fixture testing are not native device acceptance tests.

## Reproduced in the previous actual FabricFix production JAR

1. A base game containing its client JAR/JSON but a missing required library was
   considered prepared even with no incomplete-install marker.
2. A 1.21.11 Forge profile was accepted when requesting 1.21.1 due to a
   substring fallback in the version matcher.
3. A dummy access token appeared in the command diagnostic logger.
4. An inherited child dependency did not replace the same parent's library
   coordinate; the old version remained ahead of the new one.

The repaired full-chain test also exposed that LaunchPlan.baseVersion recorded
an incoming Fabric profile ID rather than its resolved Minecraft version.
H1 corrects that record, and retains Forge/NeoForge labels rather than reporting
those plans as Vanilla. Actual command argument contents are preserved.

## Implemented fixes and their scope

- Read-only base readiness checks for required artifacts, hashes supplied by
  metadata, native library selectors, asset index/object sizes, and logging file.
  Existing download/import repair is invoked for incomplete base installs.
- Bounded retry and atomic publication for Fabric and Forge/NeoForge metadata
  and managed JARs. Archive/entry CRC validation rejects nonempty corrupt caches
  and HTTP-200 error pages; Fabric loader JARs must contain KnotClient.
- Exact inherited base matching and child-library replacement with separate
  classifiers and OS selectors preserved.
- Managed Java preparation for official Forge/NeoForge installer processes,
  the existing vendor flags, argument-array paths with spaces, no overwrite of
  existing launcher profiles, a 20-minute production timeout, and cleanup of a
  hung installer process.
- Redaction of token/password-like command options before diagnostic logging.
  Only synthetic dummy credentials were used in tests; auth behavior is unchanged.
- The old Windows classpath separator fix remains inside the package.
- No mods, worlds, user options or approved image assets are rewritten by these
  repair helpers. Updating the managed app remains separate from the game root.

Archive CRC validation is corruption detection, not vendor signature verification.
When loader metadata has no checksum, H1 does not invent one. HTTPS verification
is retained, and base-game SHA-1/runtime SHA-256 checks remain in place.

## Java checks against the repaired application JAR

- ReleaseSelfTest: **33 assertions**, existing hardware/runtime selection policy.
- DownloadRecoveryTest: **46 assertions**, existing actual TCP interruption,
  transient HTTP, bounded attempts, hashes, temp files, cache reuse, local errors,
  cancellation and data-preservation tests.
- WindowsFabricClasspathTest: **7 assertions**, actual packaged private verifier
  with Windows backslash and Unix forward-slash strings and wrong-version cases.
- FirstInstallTest: actual base installer with local HTTP fixture data; failed
  library after client download, repair, SHA-1 cache reuse, offline ready check,
  and unchanged user-mod sentinel.
- HardeningSelfTest: **47 assertions**. Missing/truncated libraries/assets,
  malformed metadata, hash mismatch, pending flags, path guards, exact Forge
  version matching, inherited dependencies/selectors, null optional logger,
  command log redaction, real patched Fabric/Forge transport methods with local
  HTTP 503/invalid-JAR recovery, preserved old files on failure, managed Java
  selection, vendor installer flags, real Java subprocess success/nonzero exit,
  and a killed short-timeout hung subprocess.
- FabricFlowTest: **53 assertions** through the real packaged
  ModLoaderInstallerService -> FabricInstallerService -> VersionProfileLoader ->
  LaunchPlanBuilder -> MinecraftProcessLauncher -> real Java subprocess chain.
  Isolated fixtures labeled 26.1.2 (no intermediary in metadata) and 1.21.11
  (intermediary included) cover fresh Fabric setup, missing/corrupt library and
  profile repair, base/profile identity, final classpath, successful synthetic
  KnotClient execution, cached second planning with HTTP server stopped, and
  byte-identical mods/config/world/options/servers/packs sentinels.

**Critical fixture distinction:** FabricFlowTest uses a synthetic KnotClient, not
real Fabric Loader or Minecraft; its base metadata explicitly requests Java 21
for the fixture executable. It is a launch-pipeline regression check, not a
26.1.2 game test. The installer test separately verifies a Java 25 request, but
its fixture helper returns the local Java 21 executable to run a synthetic
Java-21 installer. It does not prove Java 25 was downloaded or executed. All
fixtures are test-only and excluded from application payloads.

## Native, packaging and UI checks

- Existing Go runtime-manager and installer tests run locally, including archive
  size/path/checksum checks, cached-runtime reuse, installation receipts, managed
  upgrades, and unchanged game-data sentinels. Runtime test executables report
  fixture properties; they are not actual downloaded JREs.
- Eight existing universal-Mach-O layout regression tests, including no bytes
  trailing the final architecture slice.
- Cross-compile Darwin ARM64/AMD64 and Windows AMD64 native launcher binaries.
  Combine exact Mac slices, validate layout; check PE x64/GUI subsystem.
- Package integrity, manifest version/build ID, unchanged approved image assets,
  and exact allowlist of modified baseline classes. No Minecraft runtime,
  personal account database, test fixture class or font file is distributed.
- Recheck the application JAR extracted from the installer payload; compare its
  hash to the repaired JAR, then rerun new hardening/Fabric-flow tests and the
  Windows classpath regression against the extracted JAR.
- Real Swing launcher opened under Xvfb/Linux. The smoke harness dismisses the
  existing intro through its finish method because Xvfb lacks window-manager
  focus. This is not native Mac/Windows startup or Minecraft gameplay.

Exact build logs and artifact hashes accompany the source bundle. Assertions do
not establish compatibility for combinations not exercised by a test.

## Outstanding release blockers and known limits

- Physical Windows installer, shortcuts, upgrade/uninstall, credential storage,
  and Minecraft Vanilla/Fabric/Forge/NeoForge title-screen/world tests.
- Native macOS startup on Neo/Air/Pro/Intel, codesign verification, actual DMG
  creation/mount/installation and Apple Developer ID notarization.
- Live official Java, Mojang, Fabric, Forge and NeoForge download/installer tests.
- Microsoft sign-in with an owned/configured KapKan client ID, token refresh,
  and Minecraft account entitlement. This build does not configure the missing ID.
- Supported loader-by-game-version matrix, third-party mod compatibility,
  graphics drivers, FPS/frame-time/thermal/battery results. No zero-stutter or
  universal compatibility claim has been measured.
- The Go runtime network fetcher is unchanged. Some network failures can still
  require retrying launch. H1 is not an exhaustive review of every UI/launcher
  path, every OS state, or every possible corrupt cache.
- Assets are checked for presence/size during readiness rather than rehashing all
  textures/sounds on every Play. The download transport verifies metadata hashes.

## Acceptance and publication

Test the new installer on an existing account without removing .kapkan. Confirm
Minecraft/Fabric setup reaches a world and succeeds on the second launch.
Run fresh-install tests separately in an isolated user/root, not by clearing real
game data. Test Forge/NeoForge only with their supported game versions; record
exact versions and mods. Mark failures honestly.

Do not advertise all loaders, no errors, every device, Microsoft sign-in ready or
no FPS spikes without evidence. RELEASE-GATE.json remains false until native
acceptance is satisfied and publication is explicitly authorized. The version
number 1.0.0 alone is not proof of readiness.
