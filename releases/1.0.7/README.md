# KapKan 1.0.7 Public Beta

This update makes the equipment HUD transparent and compact: icons and colored remaining/max counters replace the panel, slot labels and empty rows. Values fade smoothly from dark green near full, through yellow at half, to red near zero. Durable items in both hands are included; existing HUD position, scale and profiles are retained. Enable Equipment durability in Features.

[Delivery.json](Delivery.json) records exact filenames, sizes, SHA256 hashes and download URLs; [SHA256SUMS.txt](SHA256SUMS.txt) covers every binary. Publication uses [v1.0.7-beta.1](https://github.com/h4chub/kapkan-releases/releases/tag/v1.0.7-beta.1); status and public delivery verification are recorded in [PUBLICATION.json](../../source/PUBLICATION.json).

All three exact Minecraft 26.1, 26.1.1 and 26.1.2 companions build and pass 1,350 deterministic checks each. Native macOS arm64 26.1.2 bright/dark rendering and inventory transitions pass 174 checks. Desktop/native builds, real previous-version companion migration, final app signing/archive audits and built Windows payload checks pass. See [QA-REPORT.md](../../source/QA-REPORT.md) for evidence and scope; native gameplay on the other two targets was not rerun for this patch.

Close Minecraft and KapKan completely before replacing the full app. On Mac, reopen KapKan from Applications. Keep the complete portable Windows folder together. Existing worlds, mods and settings are preserved. Mac is locally signed and not Developer ID notarized; Windows is unsigned. Native Windows installation and interactive microphone capture remain untested.
