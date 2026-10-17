# KapKan 1.0.5

- Fixed onboarding: one saved first-start introduction, no join/menu/respawn/dimension repetition; replay from Help. Existing keybinds remain saved, with J as the default.
- Replaced the oversized menu with a centered, scale-aware panel, categories, feature search, short descriptions, clear toggle states and responsive native input. Removed implicit background blur and fixed narrow scroll icons after actual-game visual review.
- Added shared HUD layout controls: draggable widgets, scaling, alignment, reset and saved profiles. Existing FPS/coordinates are reused alongside keystrokes, mouse-button indicators, armor durability, active effects and sprint status.
- Added configurable smooth zoom and toggle sprint using native key mappings and vanilla sprint eligibility.
- Added a Fabric-server cosmetic relay: connected player identities, live cape/badge updates, bounded caches and off-thread image work. The matching server and clients need KapKan. Existing optional HTTP sharing remains available; no public service is deployed.
- Added No Cape with immediate custom-cape removal, persisted selection and remote removal. It does not hide an existing Minecraft cape.
- Built separate exact Minecraft 26.1, 26.1.1 and 26.1.2 JARs against their own pinned dependencies. No new build is advertised for other versions/loaders.
- Preserved the 1.0.4 Fabric dependency-recovery fixes and existing preferences. The launcher selects the matching client build and safely backs up older KapKan companions, including custom mods-folder profiles.

See COMPATIBILITY.md and QA-REPORT.md for measured verification and remaining device limitations.
