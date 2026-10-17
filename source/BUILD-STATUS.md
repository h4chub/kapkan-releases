# KapKan 1.0.6 build status

The selected rendering-only build is ready for publication after final package and delivery verification. The public release remains [1.0.5](https://github.com/h4chub/kapkan-releases/releases/tag/v1.0.5-beta.1) until PUBLICATION.json records the new release.

All three exact Minecraft targets compile and pass 1,209 deterministic checks each. Final native macOS arm64 smoke runs load KapKan + ImmediatelyFast, exclude the two removed candidate modules, and enter fresh single-player worlds on 26.1, 26.1.1 and 26.1.2 using verified Zulu25. The final integration passes 56 checks. Launcher/Fabric/recovery and actual 1.0.5 migration checks pass. Actual app signing, archive and built Windows payload checks are recorded under qa/1.0.6. Clean official Mac Java 21/25 runtime installation and cache migration pass; Zulu21 opens the final launcher UI.

This build reduces repeated HUD measurements and disabled-widget work, simplifies cape vertex preparation and adds optional ImmediatelyFast without changing graphics settings. The initial three-module candidate and all valid/rejected frame measurements remain in QA history. Control drift and uninstrumented OS/background activity prevent a causal FPS claim.

Microphone support includes app metadata/signing and a capable managed Mac runtime. Interactive microphone consent/capture, native Windows installation, Intel Mac execution and macOS 12 device acceptance remain untested. Mac local signing is separate from Developer ID notarization.

See [QA-REPORT.md](QA-REPORT.md) for scope and [history/1.0.5](history/1.0.5/) for historical records.
