# KapKan

KapKan 1.0.5 is a Fabric client mod and desktop launcher. It adds a compact searchable menu, configurable HUD, smooth zoom, toggle sprint and optional server-assisted badges/capes, while retaining the launcher's Fabric dependency-recovery fixes.

This branch prepares the recovered 1.0.5 source and delivery evidence for review in `h4chub/kapkan-releases` on `codex/kapkan-1.0.5`. A public GitHub release and website deployment are not part of this change.

- [1.0.5 release overview and checksums](releases/1.0.5/README.md)
- [Install, controls and build instructions](source/README.md)
- [Compatibility](source/COMPATIBILITY.md) and [QA report](source/QA-REPORT.md)
- [Actual game test evidence](source/qa/1.0.5/ACTUAL_TESTS.md)

Use the exact Minecraft-version JAR for **26.1**, **26.1.1** or **26.1.2**, with Java 25 and Fabric Loader 0.19.5+. The mod JARs are included in `source/app/`. Original desktop delivery packages are described by filename, size and hash in the release metadata; the packages are not duplicated in this source review.

The inherited QA records real game runs on Linux x86_64 with Java 25, Fabric 0.19.5, Xvfb and Mesa llvmpipe. Each target passed two-client acceptance checks and 35 gameplay feature checks. Native macOS/Windows installation and gameplay were not run for 1.0.5; packages are unsigned. There is no performance or FPS boost claim.

Historical [BUILD-INFO.json](source/BUILD-INFO.json), [RELEASE-GATE.json](source/RELEASE-GATE.json) and QA documents are retained as evidence, including their publication/access statements at the time they were written. Current branch-preparation scope is recorded in [PUBLICATION.json](source/PUBLICATION.json).
