# 1.0.0 internal build 6

- Fix single-player snapshots and local own-badge visibility independent of the multiplayer service.
- Default own badge on, preserve explicit choices, save badge switches immediately.
- Back up exact older official badge modules only after validating the replacement.
- Move verified old Mac launcher downloads and apps to Trash; retain game/user files and modified packages.
- Polish Mods, Accounts, Explore and Settings, retaining the Play page and profile popup.
- Use plain 1.0.0 in public names; internal build identifier desktop-polish-6 and Mac CFBundleVersion 10006.
- Retain the dark macOS title-bar bootstrap and original art.

The companion also loads its original badge font and texture as a built-in client resource pack. Plain Fabric Loader does not expose mod assets by itself; the live check initially showed missing-glyph squares. The fixed module supplies its two assets without requiring Fabric API or modifying user resource packs.
