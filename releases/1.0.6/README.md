# KapKan 1.0.6 Public Beta

Release files are prepared and checksum verified. Publication will use [v1.0.6-beta.1](https://github.com/h4chub/kapkan-releases/releases/tag/v1.0.6-beta.1). [Delivery.json](Delivery.json) records exact names, sizes, hashes and intended download links; [SHA256SUMS.txt](SHA256SUMS.txt) covers every binary.

This update repairs macOS app/runtime microphone capability, reduces repeated HUD/render work and adds optional ImmediatelyFast rendering optimizations. Game performance → Use rendering optimizations applies at the next game start. Existing user mods, worlds, controls and graphics options are preserved. Third-party binary licenses and exact corresponding sources are included in both desktop packages.

The three exact Minecraft 26.1/.1/.2 builds pass deterministic tests and native macOS arm64 world-entry checks. See [the QA report](../../source/QA-REPORT.md) for results and limits. Native Windows installation and interactive voice capture after OS consent remain untested. The Mac app is locally capability signed, not Developer ID notarized; Windows is unsigned. There is no general FPS claim.
