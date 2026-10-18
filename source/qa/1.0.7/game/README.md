# KapKan 1.0.7 native equipment HUD acceptance

PASS on a real Minecraft 26.1.2 / Fabric 0.19.5 client with native macOS arm64 OpenGL, verified unmodified Zulu 25.0.4.1 JRE and the pinned ImmediatelyFast subset. Both fresh private-world runs used companion SHA256 `2d7b9116050638da9eae0449ca46df80d3dd991b99a39f72ad68e7793e7a579e`.

The occupied-equipment run completed 147 repeated inventory/live-widget checks and captured light/dark wall screenshots. Exact visible values were helmet363/363 green, chest264/528 yellow, leggings1/495 red, boots108/429 orange, main-hand mace498/500 green and offhand shield168/336 yellow. All six occupied rows were compact and transparent, without slot names or a backplate, and text shadows remained readable on both backgrounds. Primary and independent visual reviews passed.

The focused follow-up completed 27 checks of actual item removal and swaps: no armor and only held mace; main-hand stone filtered while shield remains; an earlier widget snapshot stays unchanged; pumpkin(max0) icon-only; unbreakable chest(max>0) infinity; all-empty item/line lists. Its screenshots confirm no equipment card/hint in empty gameplay, and `Equip armor or a tool` only in the real editor. The independent follow-up visual review also passed.

`native-game-acceptance.json` summarizes scope. Each subfolder retains raw probe results, exact artifact/runtime hashes, logs, private test configuration, test source and its two screenshots. The first screenshot pair remains unchanged. Source snapshots and runners are QA-only review material, never shipped in the mod. SHA256SUMS covers this evidence.

This is a26.1.2 native visual/runtime check. Exact26.1 and26.1.1 compilation/unit coverage is separate in the parent task. No FPS claim follows from this test. No live-user app/profile/world or public release/deployment was changed. Worlds, game/runtime binaries, assets and compiled QA probes are excluded.
