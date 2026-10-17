# KapKan follow-up roadmap

These are recorded suggestions, not working features of the current private build.

1. **Friends and playing together.** Add persistent identities, friend requests/acceptance, removal/blocking, online status and join/invite flows. Keep social identity separate from self-reported badge presence. Requires an authenticated service and two-client tests before exposing real invites.
2. **Create and play on servers.** Define the host model, starting with explicit world sharing and server management, then hosted creation if an owner-operated server/relay is available. Remote internet access must be verified; a local LAN server is not managed internet hosting. No infrastructure was provisioned or exposed in this task.
3. **Multiplayer cosmetics deployment.** Protocol-2 sharing for equipped premades and validated custom PNGs is implemented. Local service tests and real rendering fixtures are included. Configure a shared HTTPS host and finish two-computer Minecraft acceptance before advertising online availability.
4. **Cape gallery, deferred.** Scroll vertically inside the left pane, keep the preview fixed, and persist most-recently-equipped order; never-used capes alphabetical. Keep this queued until requested after testing.
5. **Shaders.** Evaluate an explicitly supported shader-mod/version combination and test cape rendering, reloads and frame times. No shader engine or shader pack is bundled in this build.
6. **Official release.** Complete Mac/Windows installs and gameplay, Microsoft sign-in setup, signing/notarization, presence deployment and the supported version/loader matrix.

The requested personal mashup skin is excluded. The skin PNG importer is implemented instead. KapKan remains free; Premium is a Coming soon page with no checkout or entitlement enforcement.
