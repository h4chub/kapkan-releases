# H2 changes

Public version remains 1.0.0; private build identifier is ui-presence-h2.

- Replaced the accumulated UI styling layers with a coherent Swing interface on the existing H1 engine. Kept Play, Mods, Accounts, Explore, Settings, existing artwork, bottom launch controls and names-only account selection.
- Added dark custom dropdowns, keyboard navigation/focus states, resizable desktop windows, scrollable Settings, and explicit ready/preparing/running/error states.
- Added measured per-file download-byte progress through the H1 retry transport. Unknown totals do not get invented percentages. Errors preserve retry access and the launch log.
- Retained H1 repair logic, managed Java setup, loader resolution, download recovery, Windows Fabric classpath correction, credential handling and data paths.
- Preserved existing options until the user explicitly applies suggested performance settings. Unused H1 artwork preloads are released; no animation loop runs while Minecraft is active.
- Added a Fabric 26.1.2 companion for white double-K Tab and overhead badges, optional third-person self view, and cosmetic session presence independent of Microsoft ownership/sign-in.
- Added a small private Python/SQLite presence backend, expiring per-session records, scoped batch queries, bounded client cache, backoff and opt-out.
- Replaced filename-prefix Core deletion in the active launcher path with per-process mod injection and a hash-verified backup of only the known H1 Core.
- Produced new Windows x64 and universal macOS packages with H2 filenames and matching embedded artifacts. Added setup instructions, test source, logs and actual launcher screenshots.

No website, GitHub, Vercel, automatic update channel or presence service was published.
