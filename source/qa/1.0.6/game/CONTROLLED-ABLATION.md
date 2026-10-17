# Native Mac controlled module ablation

The seven samples below passed continuous focus/rendering/camera checks and exact start/end and cross-run canonical state comparison. They do **not** establish a causal FPS gain: the repeated baseline drifted by 16.76% and repeated bare 1.0.6 by 17.97%. The user continued using the computer, and macOS window occlusion/background activity was not fully instrumented.

| Order | Configuration | FPS | Mean ms | p95 ms | p99 ms |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | 1.0.5 control | 125.776 | 7.9506 | 13.2808 | 17.1158 |
| 2 | 1.0.6 bare | 144.149 | 6.9372 | 11.5478 | 16.0887 |
| 3 | 1.0.6 + ImmediatelyFast | 267.357 | 3.7403 | 6.9150 | 8.4574 |
| 4 | 1.0.6 + all three modules | 170.874 | 5.8523 | 10.3270 | 12.9694 |
| 5 | 1.0.6 + Lithium/FerriteCore | 140.071 | 7.1393 | 12.3797 | 16.3731 |
| 6 | 1.0.6 bare repeat | 170.052 | 5.8806 | 9.8482 | 12.4868 |
| 7 | 1.0.5 control repeat | 146.851 | 6.8096 | 12.0422 | 15.2938 |

Raw values and exact hashes are in `native-mac-controlled-ablation.json` and `controlled-ablation/`. The source records the official upstream module SHA512 values; `run.json` records each exact injected binary. All samples used the same Java 25.0.2, Fabric 0.19.5 and Minecraft 26.1.2 runtime on an Apple M4, Darwin 24.6.0 arm64, native macOS OpenGL. The requested 1280×720 window rendered to a 2560×1440 Retina framebuffer, with render distance 6, simulation distance 5, VSync off, and configured 260 FPS soft cap. Each sample followed 30 continuous eligible warmup seconds and measured 30 seconds. All sampled frames were unfocused, visible according to GLFW, non-minimized and free of UI/overlay interruptions.

A fresh seed-1234 single-player world used a fixed platform and 200 fixed armor stands, fixed camera/player pose, frozen daylight/weather, ambient spawning off and random ticks zero. Camera/player/FOV and graphics/framebuffer settings were checked continuously. Armor-stand positions were checked every second and at sample end. Final screenshots were visually consistent across all seven runs: no obvious model, texture or image-quality loss was found with ImmediatelyFast. Clouds still animate; the screenshots are not pixel-identical. Non-armor-stand ambient entities and OS occlusion were not exhaustively instrumented.

## Preparation provenance

Controls 1–2 use original probe `edd1ca94196e6a6663b6cf841d9075a7895c145c172721a051380866301e090d`. Runs 3–7 use `35cf0e9b6e4f0b635cfc529686f3597977945ff312c99e107afca8ac06bba526`. The sole behavioral correction releases the QA game's mouse after setup and restores current/prior yaw/pitch to the canonical pose **before** restarting the full warmup, only while unfocused. It never moves the camera during sampling. Independent review approved retaining the original controls: eligibility, timing/statistics and state helpers are unchanged, and all canonical state bytes match. The review receipt and exact source snapshots are retained. Per-run original probe hashes remain distinct.

A preceding IF-only attempt produced 197.709 FPS but failed cross-run camera matching (-184.6499°/5.3000° versus -180°/8°). It remains in `rejected-camera-shift/` and is excluded from this table. The original negative 31.6% pair also remains in the separate historical receipt/report; neither unfavorable nor rejected data was erased. Earlier timeout/occluded-scene attempts produced no accepted comparison.

## Default recommendation and limits

ImmediatelyFast alone is the smallest targeted rendering addition and the strongest raw observation. Lithium/FerriteCore and the full bundle have no demonstrated advantage from this session sufficient to justify adding them to the default. An IF-only default with opt-out and existing-user-mod precedence is the conservative candidate recommendation; this is an engineering choice, **not proof of a percentage gain**. No performance percentage should appear in product UI, website or release claims. Broad hardware, Windows runtime performance, modpack compatibility and long gameplay sessions are outside this test.

Only small QA records, source snapshots and game screenshots are included here. Game binaries, visual assets, worlds, runtime libraries, compiled probes and QA-only mod JARs are excluded. No user profile, installed application, existing world or saved graphics option was changed.
