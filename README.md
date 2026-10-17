# KapKan

KapKan is a Fabric client mod and desktop launcher with a searchable menu, configurable HUD, smooth zoom, toggle sprint and optional server-assisted cosmetics.

The current public release is [1.0.6](https://github.com/h4chub/kapkan-releases/releases/tag/v1.0.6-beta.1): macOS microphone capability/runtime fixes, reduced HUD/render overhead and optional ImmediatelyFast rendering optimizations. No FPS gain is claimed. All seven public downloads and live updater discovery are verified. The [public website](https://kapkan-website.vercel.app/#download) serves this release. See [release files and receipts](releases/1.0.6/README.md).

- [Install, controls and build](source/README.md)
- [Compatibility](source/COMPATIBILITY.md)
- [Current verification and limits](source/QA-REPORT.md)
- [Module licenses and corresponding sources](source/performance/NOTICE.txt)
- [Historical 1.0.5 release](releases/1.0.5/README.md)

Use the exact Minecraft 26.1, 26.1.1 or 26.1.2 build, Java 25 and Fabric 0.19.5+. Mac/Windows packages include all three builds. Existing worlds, mods, controls and settings are preserved. Native macOS arm64 game smoke checks pass for all three targets; native Windows installation and interactive microphone consent/capture remain untested. The Mac package has a verified local capability signature and is not Developer ID notarized; Windows is unsigned.
