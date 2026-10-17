# KapKan 1.0.5 build status

KapKan 1.0.5 is [publicly released](https://github.com/h4chub/kapkan-releases/releases/tag/v1.0.5-beta.1), and the [website](https://kapkan-website.vercel.app) serves the verified public downloads. All seven asset sizes and SHA-256 digests match the original package bytes. The live release feed passes update discovery checks on Windows and macOS. See [PUBLICATION.json](PUBLICATION.json) and [delivery metadata](../releases/1.0.5/Delivery.json).

Inherited final QA records PASS for exact Minecraft 26.1, 26.1.1 and 26.1.2 builds: each ran two real clients on Linux x86_64 with Java 25, Fabric 0.19.5, Xvfb and Mesa llvmpipe, including cosmetics/lifecycle/restarts and 35 gameplay feature checks per target. Separate 26.1.2 UI tests passed 62 interaction and 5 restart checks. The import verifies artifact hashes; it does not rerun those game tests.

Native macOS/Windows installation and gameplay were not run for this release. Cross-built desktop packages are unsigned. Linux Go payload fixtures passed, including the final built-payload upgrade fixture. See [QA-REPORT.md](QA-REPORT.md) for full scope and limits; older publication/access statements in retained reports are historical.
