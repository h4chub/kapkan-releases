# Final selected renderer subset

PASS on Minecraft 26.1, 26.1.1 and 26.1.2 with Fabric 0.19.5 and the verified unmodified Zulu 25.0.4.1 JRE on macOS arm64. Each fresh isolated single-player world stayed ready for six consecutive seconds with actual rendered frames observed. The probe checked that KapKan presence and ImmediatelyFast were loaded and Lithium/FerriteCore were absent. These are compatibility checks, with no FPS measurement.

Per-target `PASS.json`, `run.json`, `world-smoke.json` and native process logs retain exact companion/IF/runtime hashes and results. `../final-selected-subset.json` summarizes the final selection separately from historical full-bundle tests. The QA-only source and runner are preserved for review; compiled probes, game binaries, libraries, visual assets and worlds are excluded. No installed app, user profile or existing world was changed.
