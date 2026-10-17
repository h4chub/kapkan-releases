# Rendering hotpath verification

`checks.log` records compilation of the changed production render classes against each exact Minecraft target (26.1, 26.1.1 and 26.1.2), plus 83 render/cache regression checks and 20 HUD settings/timer checks per target. These targeted compiles used the prepared official dependency classpaths and the unchanged classes from the corresponding recovered 1.0.5 companion JAR. They are separate from the final complete 1.0.6 build and gameplay acceptance.

The checks cover cached versus uncached font widths, identical painted pixels at framebuffer densities 1–4, bounded metrics caching, changing text and long input, immutable texture-cache entries, shape/icon/density invalidation, string hash collisions, disabled HUD work, the existing 100 ms live-value refresh cadence, enabling features, replacing the player and entering the editor. Key/mouse states are still read during every rendered frame. The editor retains hit-test geometry for all widgets; the gameplay entry point skips creating the unused bounds map.

`benchmark.json` measures the actual text-metric implementation before and after the change. The baseline calls the exact former `Font.getStringBounds(text, FRC)` operation. The optimized path calls `MenuRaster.width(text)` using the same bundled font and logical text dimensions. The workload cycles through 32 warmed menu/HUD labels. Six alternating warmup rounds precede nine measured rounds of one million lookups; recorded values are medians. CPU time and allocated bytes come from the current Java thread, rather than elapsed wall time. Measurement order alternates to reduce ordering bias.

On the recorded macOS ARM64 / JDK 25.0.2 run, the median lookup cost was 63.043 CPU ns before and 15.339 ns after; measured allocations were 68.5 bytes before and 0 bytes after per call. Absolute timings vary with the machine and competing work. Cache misses still measure the full string, and changing inputs have regression coverage. This measures the text-metrics hotpath; it is not a Minecraft FPS benchmark, a GPU measurement or native installation/gameplay acceptance. No performance message or FPS claim is added to the product.

To repeat the microprofile from the source directory:

```sh
mkdir -p /tmp/kapkan-render-benchmark
javac --release 25 -d /tmp/kapkan-render-benchmark \
  companion/src/main/java/gg/kapkan/presence/cosmetics/MenuRaster.java \
  companion/qa/performance/RenderHotpathBenchmark.java
java -Djava.awt.headless=true \
  -cp /tmp/kapkan-render-benchmark:companion/src/main/resources \
  gg.kapkan.presence.cosmetics.RenderHotpathBenchmark
```

The complete deterministic test entry points are `gg.kapkan.presence.cosmetics.RenderHotpathTest` and `gg.kapkan.presence.cosmetics.HudRenderSettingsTest`; the existing `companion/test.py --minecraft VERSION` discovers both after the final target build.
