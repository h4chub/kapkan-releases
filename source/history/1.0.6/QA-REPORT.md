# KapKan 1.0.6 verification

This report covers checks rerun on 1.0.6. Historical 1.0.5 Linux gameplay records remain in history/1.0.5 and qa/1.0.5; they are not new native 1.0.6 acceptance.

## Core and launcher

All three exact Minecraft 26.1, 26.1.1 and 26.1.2 companion builds compile against pinned official inputs and pass 1,209 deterministic checks each. This includes 103 new render-cache/settings checks per target. Pixel/metrics checks cover scale, text, color, bounded eviction and immutable sprite keys. A warmed Java text-width microbenchmark measured 63.043→15.339 ns/lookup and 68.5→0 bytes/lookup on this Mac. Those method measurements are not Minecraft FPS.

Launcher release/recovery, first-install/download recovery, user mod profiles and Windows classpath fixtures pass. Hardening passes 47 assertions, including actual bounded process termination. Maintenance passes 22, including a Mac path-alias/future game-directory protection regression. Fabric preflight plus fresh/restored/linked-cache flows pass across 34 version IDs. Real 1.0.5→1.0.6 mod migration passes all three targets with backups and user-data sentinels unchanged (103 integration checks total). Menu layout passes 432 checks.

## Default rendering addition

The final engine addition is unmodified ImmediatelyFast 1.15.3. Its checksum, LGPL/GPL terms, nested Reflect MIT notice and exact corresponding source archive accompany both desktop packages. The final integration preserves existing direct/nested/provided copies, declared/known conflicts, opt-outs, game options and user mods. **Use rendering optimizations** applies on the next Minecraft start.

The initial three-module candidate loaded and entered real isolated worlds on all three exact targets. A local frame comparison then raised a regression concern. Rejected attempts and raw results are retained; no gain was inferred from them. Corrected ablations required a canonical 200-stand scene, matching camera/options/entity positions/time/weather, stable unfocused visible rendering, 30-second warmup and 30-second sampling. Controls were repeated at both ends. A narrowly reviewed prep-only camera correction retained the unchanged measurement protocol and original valid controls' provenance.

All seven accepted runs passed scene/focus/render guards. Opening/closing baseline drift was 16.76%; bare-client drift was 17.97%. ImmediatelyFast alone had the strongest raw result, while Lithium+FerriteCore showed no demonstrated advantage and broadened scope. The latter two are omitted from the shipped default. These are directional local measurements on one Apple M4 with native macOS OpenGL; OS occlusion and background activity were uninstrumented. They do not prove a causal FPS increase or percentage. Clouds animate, so images are visually consistent rather than pixel-identical. See qa/1.0.6/game for raw data, screenshots and provenance.

Final IF-only integration passes 56 checks. Native macOS arm64 worlds on all three exact targets load KapKan + ImmediatelyFast, exclude Lithium/FerriteCore, and render for six consecutive clear-world seconds using the verified managed Zulu25 runtime. These acceptance runs do not time FPS. Integration, game and package receipts are recorded under qa/1.0.6. No render distance, graphics quality, frame cap, JVM memory setting, control, world or user mod is changed by the integration.

## macOS microphone capability

The 1.0.5 app omitted microphone purpose metadata, and its official Temurin runtime lacked signed audio-input capability. The fix adds app metadata and a verified app capability signature, and uses separately cached, checksum-verified vendor-signed Azul Zulu Java 21/25 runtimes on Mac. Windows remains Temurin. Exact archive hash, runtime major/architecture, Apple signature seal and embedded signed audio capability are checked. Existing runtime caches are preserved.

Clean Zulu 21 opens the real 1.0.6 launcher UI/settings; clean Zulu 25 enters a real isolated game world. Missing/false/altered signed-entitlement cases are rejected. Packaging/MachO tests, actual app signing, built Windows payload upgrade and archive audits pass. Detailed evidence is in qa/1.0.6/macos-permissions and final package logs.

Interactive microphone capture after macOS consent remains untested. No microphone request, recording or OS privacy-database change was performed by QA. Consent remains under the player's control when a voice mod opens input.

## Limits

Native Windows installation, Intel Mac execution, macOS 12 device acceptance, Microsoft sign-in, arbitrary GPUs/modpacks and external public servers are not established by these fixtures. The Mac app has a local ad-hoc capability signature and is not Developer ID notarized; Windows is unsigned. No public cosmetics service is deployed. Final release status is recorded in PUBLICATION.json; public asset delivery is checked separately after publication.
