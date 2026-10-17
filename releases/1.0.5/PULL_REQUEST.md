# Prepare KapKan 1.0.5 source and delivery evidence

The completed 1.0.5 build was left outside the release repository after the previous publication attempt stopped. This change imports the recovered source, exact shipped mod JARs and selected QA evidence into a review branch, and records the original package checksums. It updates active publication/build status to 1.0.5 while preserving the older metadata as history.

The mod adds a searchable, scale-aware menu, HUD editing/profiles, smooth zoom, toggle sprint, server-assisted cosmetics and persistent No Cape. It fixes repeated onboarding and retains the launcher Fabric recovery and matching-version companion selection.

Validation: the original source archive SHA-256 was verified before extraction, and all three included 1.0.5 mod JAR hashes match final QA evidence. Inherited tests cover real Linux Minecraft 26.1/26.1.1/26.1.2 clients with Fabric 0.19.5 and Java 25, including two-client lifecycle/cosmetic acceptance and 35 gameplay feature checks per target; 26.1.2 also has 62 UI and 5 restart checks. Deterministic companion, launcher, migration and Linux native-payload fixtures passed in the prior build session. These game tests were not rerun during import.

Native macOS/Windows installation and gameplay remain untested for 1.0.5; desktop packages are cross-built and unsigned. No public release, website deployment or public relay deployment is included.
