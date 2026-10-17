# KapKan 1.0.6 Public Beta

Release files are public and delivery verified at [v1.0.6-beta.1](https://github.com/h4chub/kapkan-releases/releases/tag/v1.0.6-beta.1). [Delivery.json](Delivery.json) records exact names, sizes, hashes and official download links; [SHA256SUMS.txt](SHA256SUMS.txt) covers every binary.

This update repairs macOS app/runtime microphone capability, reduces repeated HUD/render work and adds optional ImmediatelyFast rendering optimizations. Game performance → Use rendering optimizations applies at the next game start. Existing user mods, worlds, controls and graphics options are preserved. Third-party binary licenses and exact corresponding sources are included in both desktop packages.

The three exact Minecraft 26.1/.1/.2 builds pass deterministic tests and native macOS arm64 world-entry checks. See [the QA report](../../source/QA-REPORT.md) for results and limits. Native Windows installation and interactive voice capture after OS consent remain untested. The Mac app is locally capability signed, not Developer ID notarized; Windows is unsigned. There is no general FPS claim.

Public delivery verification passes for all seven asset digests/sizes and unauthenticated prefix/suffix ranges. The actual published launcher updater passes ten installed-tag/platform cases against the live release feed. These simulate installed tags using the current published updater; historical binaries were not rerun. See [verification-summary.json](verification/verification-summary.json). The release tag targets source commit `e4eea6d5f2d825f5be5d4e861e1155a4f555a66d`; later branch documentation commits do not change the tested release.

The [public website](https://kapkan-website.vercel.app/#download) serves this release. Exact live HTML/script/config bytes, all seven download sizes/digests and five desktop/tablet/mobile views pass; see the website verification receipts in [verification/](verification/).
