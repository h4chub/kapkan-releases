# KapKan 1.0.5 actual-game verification drivers

These files are **test-only**. None is included in the production mod. They drive
real Minecraft/Fabric processes, not a UI mock-up or a replacement game engine.
Use an isolated development host and never install the probes in a normal game.

`prepare-runtime.py` obtains official Minecraft client/server metadata and bytes,
validates SHA-1 checksums, and downloads the game's Linux runtime libraries and
visual assets. Audio assets are omitted because the test disables sound. Caches
under `qa/1.0.5/runtime` contain proprietary game inputs and must not be distributed.

`run-acceptance.py` requires JDK 25 and a Linux Xvfb/Mesa/xdotool environment. Its
`KAPKAN_QA_TOOLCHAINS` path should contain `jdk25/bin/{java,javac}` and the graphical
runtime under `sysroot/usr`. It starts its own TCP-only X display because UNIX
sockets are unavailable on this host. `KAPKAN_QA_DISPLAY` selects the display.
`KAPKAN_QA_PORT` selects its loopback server port. `KAPKAN_QA_CLIENT_HEAP` and
`KAPKAN_QA_SERVER_HEAP` cap test-process heaps when running isolated versions in
parallel. Each run creates an isolated game directory and a private loopback Fabric server.
The local server uses offline development identities exclusively for these tests.
No Microsoft account or public server authentication is bypassed.

Build first with `KAPKAN_JDK=/path/to/jdk25 python3 companion/build.py`.
Prepare each exact runtime with:

```sh
KAPKAN_QA_VERSION=26.1 python3 companion/qa105/prepare-runtime.py
KAPKAN_QA_VERSION=26.1.1 python3 companion/qa105/prepare-runtime.py
KAPKAN_QA_VERSION=26.1.2 python3 companion/qa105/prepare-runtime.py
```

Run full two-client multiplayer/lifecycle/restart verification:

```sh
python3 companion/qa105/run-acceptance.py
```

Run the actual-input/HUD/zoom/sprint verification:

```sh
KAPKAN_QA_MODE=features python3 companion/qa105/run-acceptance.py
```

Run exact-version world/HUD/menu/relay startup smoke verification:

```sh
KAPKAN_QA_MODE=smoke KAPKAN_QA_VERSION=26.1 python3 companion/qa105/run-acceptance.py
KAPKAN_QA_MODE=smoke KAPKAN_QA_VERSION=26.1.1 python3 companion/qa105/run-acceptance.py
```

Run native search, category, scrolling, toggle and key-binding UI interactions,
including an actual process restart to verify a J-to-K rebind persisted:

```sh
KAPKAN_QA_MODE=ui python3 companion/qa105/run-acceptance.py
```

Generate the release report only after every required exact-hash test passes:

```sh
python3 companion/qa105/report-results.py
```

The final two-client run omits launcher-only JVM flags, validating the standalone
Fabric mod path. Two independent clients each own their preferences. Server-side
cosmetic support is the same exact-version production JAR and its nested required
Fabric API modules. Record `run.json`, `PASS.txt`, assertion markers, game logs and
original screenshots when packaging evidence. Exclude runtime caches, test worlds,
compiled probe classes, and test-only JARs from end-user downloads.

A successful automated run on Linux Mesa software rendering does not establish
Windows/macOS native testing, performance gains, compatibility with unrelated
rendering mods, public-server behavior or Microsoft sign-in. The final report records full two-client acceptance and feature checks for all three targets, plus additional smoke runs and 26.1.2 UI interactions.
