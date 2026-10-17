Final shipped subset: ImmediatelyFast only. Its separate managed-Zulu25 native world smoke passed all three exact targets; see [final-selected-subset/README.md](final-selected-subset/README.md) and `final-selected-subset.json`. The three-module results below describe the earlier candidate.

The corrected seven-run follow-up and current interpretation are in [CONTROLLED-ABLATION.md](CONTROLLED-ABLATION.md). The original pair below is retained as historical evidence.

# KapKan 1.0.6 native game verification

All three exact Minecraft targets started on this Mac, loaded the three verified optimization mods with KapKan 1.0.6, and entered fresh isolated single-player worlds. The 26.1 and 26.1.1 runs checked six seconds of world rendering and exact loaded mod IDs; 26.1.2 additionally completed the fixed-scene comparison below. These are game tests, separate from launcher packaging and microphone/privacy acceptance.

| Minecraft | Exact KapKan module SHA256 | Native world result |
| --- | --- | --- |
| 26.1 | `c01579a31379f567eb19afb03969a888367bb91517d9652e9622851c1f194210` | PASS |
| 26.1.1 | `3d447a99b1c3bf190e9af3864f1d82a3c3978e141fa16a47fa64193d9f2c75da` | PASS |
| 26.1.2 | `a747bff50be125397a77fc4be7155f98485a049676c2a8616d8ce03aeb2f5f41` | PASS |

## Local frame-time comparison

Environment: Apple M4, Darwin 24.6.0 arm64; Oracle Java 25.0.2, Fabric Loader 0.19.5, native macOS LWJGL OpenGL. Requested 1280×720 windows used 2560×1440 Retina framebuffers. Both windows were predominantly unfocused (baseline: 0 active/8,198 inactive frames; candidate: 578 active/5,030 inactive frames); the QA-only inactivity setting was MINIMIZED and focus counts are recorded.

One sequential baseline/candidate pair ran after compiler, compression and other heavy QA jobs were paused. Each fresh normal world used seed 1234, loaded the scene chunks before reading terrain height, cleared camera space, and placed 200 armor stands on a raised platform. An early screenshot visibility check passed before sampling; final screenshots are retained. Each run used render distance 6, simulation distance 5, VSync off, a 260 FPS cap, 30 seconds warmup and 45 seconds measurement. These settings existed only in the isolated QA profiles.

| Metric | 1.0.5 baseline | 1.0.6 plus bundle |
| --- | ---: | ---: |
| Recorded frame intervals | 8198 | 5608 |
| Average FPS | 182.148 | 124.618 |
| Mean frame time, ms | 5.49 | 8.0245 |
| Median frame time, ms | 4.7427 | 7.0783 |
| 95th percentile frame time, ms | 9.3772 | 13.8281 |
| 99th percentile frame time, ms | 15.6174 | 19.3572 |

The average-FPS difference for this one local pair was -31.58%. This is a directional measurement of this scene on this Mac, not an FPS guarantee or a general performance claim. The comparison changes both the KapKan version and optimization bundle, so it does not isolate the contribution of each individual change.

## Evidence and limits

- `native-mac-world-results.json` records exact candidate hashes and scope; per-run logs record the actual loaded modules.
- `native-mac-frame-comparison.json` and `frame-baseline/` / `frame-candidate/` retain raw metrics, settings, camera values and original screenshots.
- Official binary SHA512/SHA1, license texts and exact tagged corresponding sources are in `performance/manifest.json` and adjacent folders.
- Earlier attempts with concurrent build load, an underground camera, or an unfinished screenshot were excluded. Their timing numbers are not used here.
- No user profile, installed app, existing world or saved graphics option was changed. Only fresh QA profiles were written.
- This does not establish Windows runtime performance, additional GPU/driver or modpack compatibility, Microsoft sign-in, public-server behavior, or complete gameplay acceptance. The exact vendor-signed managed-JRE smoke check is a separate acceptance result.
- Game binaries, proprietary visual assets, runtime libraries, worlds, compiled probes and QA-only mod JARs are excluded from this evidence folder.


## Original comparison limitation

The observed 31.6% slowdown remains recorded. Causal attribution is inconclusive: focus was unequal (baseline 0 active and 8,198 inactive frames; candidate 578 active and 5,030 inactive), the original sampler did not validate `runTick` rendering eligibility or reset intervals on UI/world interruptions, camera and scene were not continuously checked, and the single baseline-first pair confounded order. The result is an unresolved negative observation, not evidence of a benefit. Corrected controls and module ablations determine the default before publication.
