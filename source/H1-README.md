# KapKan 1.0.0 | Hardening H1 test build

Application version remains **1.0.0**. H1 is an artifact identifier, not a public
version bump. Nothing in this package uploads to GitHub, changes Vercel, or
publishes website downloads. Public release is on HOLD.

## Windows

Close KapKan and Minecraft. Run the new H1 installer in the same user account as
your current installation. It updates the managed application in
`%LOCALAPPDATA%\Programs\KapKan` and keeps the previous managed application as a
backup. It does not uninstall, reset or replace game data in
`%USERPROFILE%\.kapkan\game`.

Do not delete `.kapkan`, uninstall Fabric manually or move your mods to apply this
repair. Existing mods may themselves be incompatible with a selected game or
loader; this build does not silently remove or modify them.

The Windows executable targets Intel/AMD 64-bit Windows. Windows ARM64 and
32-bit systems are not native targets of this package. Native Windows execution
and signing have not been performed in the build environment.

## macOS

This package contains a precompiled `KapKan.app` with Intel and ARM64 executable
slices, including architecture-based handling rather than an M-series name check.
That design target does not establish tested compatibility with every Mac.

Keep `Make-DMG.command` beside this new `KapKan.app`. Double-click it on macOS.
It signs a temporary copy, performs strict validation and creates
`dist/KapKan-macOS.dmg` only if those steps succeed. The log is
`dist/Make-DMG.log`. The original source app and an existing completed DMG are
preserved. This is packaging, not compiling the launcher.

No actual DMG is included because hdiutil and macOS code-sign validation are not
available on the Linux build host. Ad-hoc signing is not Developer ID notarization.
Optional `KAPKAN_SIGN_IDENTITY` and `KAPKAN_NOTARY_PROFILE` require credentials
owned by the publisher. No credentials are included or requested by this archive.

After native packaging succeeds, copy KapKan.app into Applications. Retain your
last working application until the candidate has passed your tests. Do not delete
your game folder. The app prepares its own Java on first use; internet is needed.

## Changes in H1

- Check base installation completeness beyond the client JAR and JSON, including
  required library files, metadata checksums, asset index/objects and logging file.
  An incomplete installation now reaches the existing repair flow.
- Route Fabric and Forge/NeoForge metadata and managed JAR downloads through the
  bounded retry transport. Validate JAR structure/CRC before publication; check
  KnotClient exists in the Fabric loader. Reuse valid cached files and keep prior
  bytes if a replacement download fails.
- Resolve the exact inherited Minecraft version. A 1.21.11 Forge profile cannot
  be treated as 1.21.1 just because its name contains a matching substring.
- Let child profiles override matching library coordinates without dropping
  distinct native classifiers or operating-system selectors.
- Correct the LaunchPlan base-version and Forge/NeoForge labels without rewriting
  its Java command arguments or changing the approved interface.
- Use the managed Java preparation path for Forge/NeoForge installer execution.
  Bound installer execution time and terminate a stuck subprocess rather than
  waiting forever. Preserve vendor flags and paths containing spaces.
- Redact authentication-token values from the diagnostic command line. The
  actual Java arguments and authentication behavior remain unchanged.
- Preserve the prior Windows Fabric backslash fix, base-game download recovery,
  Mac executable-layout repair, UI/branding, data locations and AutoTune policy.

## Known release limits

This is NOT a guarantee that every loader/version/mod set launches. Read
`QA-REPORT.md` and `RELEASE-GATE.json`. Native platform tests, live official
Minecraft/Fabric/Forge/NeoForge/Java downloads, game title-screen/world entry,
Microsoft sign-in, and FPS/thermal/battery testing remain outstanding.

Microsoft sign-in still requires a valid client ID registered for KapKan.
Do not enter an email/password into the Client ID box and do not borrow another
launcher's ID. This repair does not configure or claim to fix account sign-in.

The Go Java-runtime fetcher is unchanged in H1; its existing checks and cache are
retained. Network outages, disk/permission errors, unsupported game versions,
third-party mod errors and native graphics/driver faults remain possible. These
should not be marketed as solved or suppressed.

## Build sources

The source deliverable contains integration/repair sources and the exact compiled
production baseline, not a reconstruction of every original UI source file.
Build on the documented Linux toolchain using `bash build-release.sh`.
It uses Java 21, Go 1.23.2, Python 3 and the included ASM tool. No stub game APIs
are used to compile launcher replacements; fixtures are strictly test-only.

## Website policy

Do not replace public download URLs automatically. Keep the owner's 1.0.0 version
and publication hold. Run native acceptance tests before advertising any tested
platform or loader. Release only after the owner explicitly says "push update".
