# KapKan 1.0.6 build status

Publication is held while a local frame-time regression with the bundled module set is investigated. Package and compatibility checks pass; they do not establish a performance benefit. The current public release remains [1.0.5](https://github.com/h4chub/kapkan-releases/releases/tag/v1.0.5-beta.1) until publication is verified.

All three exact Minecraft targets compile and pass 1,209 deterministic checks each. Native macOS arm64 smoke runs load all bundled modules and enter fresh single-player worlds on 26.1, 26.1.1 and 26.1.2. Launcher/Fabric/recovery, actual 1.0.5 migration, built installer payload and archive checks pass. The Mac app signature seal and audio-input entitlement are verified; clean official Java 21/25 runtime installation and cache migration pass.

The update reduces repeated HUD measurements and disabled-widget work, simplifies cape vertex preparation, and adds optional compatible modules without changing graphics settings. Microphone support includes app metadata/signing and a capable managed Mac runtime. Interactive microphone consent/capture, native Windows installation, Intel Mac execution and macOS 12 device acceptance remain untested. Mac local signing is separate from Developer ID notarization.

See [QA-REPORT.md](QA-REPORT.md) for scope, and [history/1.0.5](history/1.0.5/) for historical records. No general FPS claim is made.
