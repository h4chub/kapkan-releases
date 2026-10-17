# Fabric installation-folder hotfix 2 for KapKan 1.0.3

The published launcher successfully installs Fabric in the shared installation
but then checks its isolated mod-profile working directory for metadata and
libraries. Restoring the JSON there exposes the next missing-libraries error.
This failure was reproduced against the unchanged published JAR.

LaunchPlan now carries installationDirectory independently of workingDirectory.
Its existing seven-argument constructor remains available to the engine.
Mod-profile routing, companion injection and profile normalization preserve the
installation root. Both final Fabric verifier lookups use that root; the game
process still starts in its selected mod profile. The build asserts that exactly
two verifier root lookups were patched. Existing manually restored directories
and old hotfix cache links are left intact and do not control installation lookup.

Validation performed on Linux using the packaged launcher and synthetic game
and loader fixtures, without an official Minecraft installation or sign-in:

- Published build: reproduced the reported missing-libraries error after restoring only JSON.
- Fresh isolated profiles: 125 assertions passed.
- JSON-only manual repair: 128 assertions passed.
- Earlier cache-link hotfix: 125 assertions passed.
- Each flow covers profile IDs 26.1, 26.1.2 and 1.21.11, the seven reported
  Fabric libraries, individual corruption/missing-file repair, deleting all
  Fabric libraries and repairing them, custom mods folders with spaces and a
  quote, and cached relaunch with the metadata server shut down.
- Mod profile isolation and data preservation: 67 checks passed.
- Windows classpath matching fixture: 7 assertions passed.
- Companion integration and data preservation: 20 checks passed.
- Existing installer/download hardening: 47 assertions passed.
- Existing memory and architecture policy: 33 assertions passed.
- Packaged JAR comparison: only the manifest and five intended classes changed;
  no entries added or removed. Native bootstrap and companion bytes retained.

Real Minecraft gameplay, macOS installation and Windows installation were not
run for this hotfix. The Mac package remains unsigned/unnotarized as before.
This is a private hotfix; no public release assets were changed.

Run KAPKAN_JDK=/path/to/jdk21 python3 tools/build-desktop.py and then
KAPKAN_JDK=/path/to/jdk21 bash tools/test-fabric-profile-fix.sh to repeat the
specific regression tests. The source archive contains the updated code and logs.

## Windows installer delivery

The Windows x64 bootstrap and installer were rebuilt with Go 1.23.2. The EXE
contains the complete current payload with its matching SHA-256. Every entry
in the bundled launcher JAR matches the tested Mac hotfix; only ZIP timestamps
differ. Fabric fixtures passed 125 assertions, Windows classpath matching passed
7 assertions, and the real payload upgrade/data-preservation test passed on
Linux. Native Windows installation, junction creation, shortcuts and Minecraft
gameplay were not run. The public website remains blocked by Vercel team access.

Build Windows only using KAPKAN_JDK=/path/to/jdk21 bash build-windows.sh with
Go 1.23.2 on PATH. The installer installs over a managed existing application
and retains a dated application backup without deleting .kapkan game data.
