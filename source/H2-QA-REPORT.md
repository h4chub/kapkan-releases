# KapKan 1.0.0 H2 QA report

Build: **ui-presence-h2**. Publication: **HOLD**. This is a private development build, not a verified native/gameplay release.

## What actually ran

| Check | Evidence and result |
|---|---|
| H1 release policy and hardware/runtime selection | Passed, 33 assertions |
| H1 download recovery | Passed, 46 assertions including real local TCP truncation, retries, hashes, cache preservation and timeout cases |
| H1 Windows Fabric path correction | Passed, 7 assertions; Windows path fixtures on Linux |
| H1 hardening suite | Passed, 47 assertions |
| H1 Fabric flow fixtures | Passed, 53 assertions across 26.1.2 and 1.21.11. Synthetic libraries, not real Minecraft execution |
| H1 first-install fixture | Passed: interrupted install, repair, reuse, offline cached check and preservation |
| H1 Go installer/runtime tests | Passed on Linux; native filesystem/runtime fixtures, not a Windows installer execution |
| H1 Mach-O layout tests | Passed, 8 regression tests |
| New companion integration | Passed, 20 assertions covering process-specific injection, old-loader exclusion, separate launch associations, unsupported combinations and file preservation |
| Presence transport | Passed, 20 assertions with a local HTTP fixture: participation, nonparticipation, expiry, reconnect, server switch, failure, disabled requests, self opt-out, bounded response and context separation |
| Backend | Passed, 9 Python tests, including a real local HTTP round trip, concurrent sessions, independent leave, shared SQLite state and TTL expiry |
| Text and visibility policy | Passed, 17 assertions using real Minecraft Component classes. Formatting and long-name preservation, camera/invisibility/team/HUD/range/self-opt-out decisions; no rendered frame claim |
| Real Fabric target transformation | Passed for Minecraft, PlayerTabOverlay, EntityRenderer, LivingEntityRenderer and AvatarRenderer in Fabric 0.19.5's actual class loader with real 26.1.2 classes |
| Real desktop UI | Passed, 18 checks at each of 1× and 2× scaling, in the running packaged Swing implementation on a Linux virtual display, at 1180×760 and 820×560; actual screenshots included |
| Packaged artifact audit | Passed archive integrity, version/build metadata, retained artwork, architecture headers and byte-for-byte shipped launcher/companion comparisons |

The full logs are in `qa-h2/`. Historical H1 evidence remains in `qa-logs/`; it is not presented as new H2 execution.

## Actual Minecraft attempt

An unmodified 26.1.2 dedicated server started in an isolated local QA directory. Three real Fabric client processes were attempted: two presence-enabled guest test profiles and one opted-out profile. Fabric discovered `kapkan-presence 1.0.0+h2` and its dependencies. Clients then reported:

`BackendCreationException: Driver does not support OpenGL`

No client reached a world. No live player badge, multiplayer recognition, third-person view, gameplay screenshot or native performance measurement is claimed. The test driver timed out and shut down its test processes. The original failure logs are included. Public account-service DNS also failed in this restricted host; the test server used local offline test identities and did not require Microsoft tokens.

A separate test-only prelaunch driver subsequently loaded every targeted real Minecraft class through Fabric and successfully applied the mixins. This establishes valid actual runtime injection targets, not successful rendered gameplay.

## Requested rendering and behavior coverage

| Requirement | Verified here | Still requires a real graphical game session |
|---|---|---|
| Two participants, one nonparticipant | Backend and transport fixtures | Two-way live appearance |
| Tab and overhead badge | Actual mixin application; text component preservation | Visual placement, glyph sharpness, scale and depth in game |
| Optional self badge | Preference, cache and visibility-policy tests; local-player renderer inspected | Real third-person camera view |
| Rank prefixes, team colours, long names | Original component retained intact in real Minecraft class tests | Visual clipping/sorting/head/ping interactions with server plugins |
| Hidden/invisible/sneaking names | Remote render only decorates an existing name tag; self policy fixtures | Walls, teams, spectators and third-party rendering-mod combinations |
| Expiry, reconnect, server switching | HTTP/SQLite/client fixtures; connection-generation invalidation | Actual reconnect/server-change lifecycle |
| Multiple local sessions | Independent service tokens and separate launch markers | Two simultaneous graphical instances |
| Network failure and opt-out | Bounded transport, backoff/expiry, no additional disabled requests | Outage during a graphical game session |
| Small windows and scaling | Running Swing desktop checks; no clipped launch/account controls at 820×560 | Native macOS/Windows scaling and accessibility readers |
| Existing user files | Sentinel comparisons, exact-hash legacy migration, original H1 installer tests | User's real machine update |

## Native packages

- Windows x64 GUI bootstrap and installer were cross-compiled with Go 1.23.2. PE architecture/subsystem and payload integrity were checked. The installer was not run on Windows and is not Authenticode-signed.
- The Mac app contains compiled x86_64 and arm64 Mach-O slices. Layout and bundle metadata were checked. It has not run on an actual MacBook Neo/Air/Pro or Intel Mac.
- No DMG exists from this Linux build. The included `Make-DMG.command` requires macOS, performs signing validation, and only then creates an H2-named DMG. No Developer ID notarization was performed.
- Managed Java still comes from H1's bootstrap. Live native Java setup, Microsoft OAuth and every loader/version combination remain unverified.

## Data and identity checks

The active H2 path uses the same state/account store and game directory as H1. No user file reorganization occurs. The new companion loads via `fabric.addMods`; unrelated mod files are never replaced. Only two known H1 Core filenames with an exact bundled-content SHA-256 match can be moved to a retained backup. Modified or unknown files remain unchanged. Existing game options are not automatically retuned.

All original PNG artwork inside the launcher baseline remains byte-identical. H1 provides an opaque RGB double-K, not an original transparent mask. The white glyph is derived without tracing: original luminance is used as alpha and RGB becomes white. Its sampled silhouette/aspect ratio is preserved; preservation of a nonexistent original alpha channel cannot be claimed.

No numeric core-development timestamp is present in the supplied source bundle. The existing `core_development_clock: unchanged` marker is retained, no clock increment was implemented, and unknown stored launcher properties remain in the retained state store.

## Service and scope limits

The service is source plus tested local implementation, not deployed infrastructure. The shipped endpoint is empty, so the UI shows **Service not configured** and no presence requests are sent. Its database is persistent SQLite on one host, not a process-local presence dictionary or a claimed serverless deployment. HTTPS and persistent storage are required for real external use.

The indicator is self-reported cosmetic presence. Spoofing is possible. It does not attest Minecraft ownership, identity, trust, client integrity or anti-cheat. No Microsoft secrets, chat or inventory are sent. UUID/name, hashed server context, temporary session identifier and batched player queries are described in the settings and service README.

No GitHub push, website/Vercel deployment, public release, paid service or automatic update was performed. FPS and frame-time gains were not measured or advertised.
