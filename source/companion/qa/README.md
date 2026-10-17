# Test-only game drivers

`src/gg/kapkan/qa/GameProbe.java` is an automated actual-client test driver. `LoadHooks.java` lives in the distinct `gg.kapkan.qaloader` package so it can execute outside the Mixin package. Neither driver nor its compiled classes are present in the shipped companion or launcher.

The local three-client attempt hit a host OpenGL limitation before world entry. The driver's gameplay assertions did not execute. `qa-h2/live/` contains the startup evidence. The independent `LoadHooks` prelaunch path successfully transformed the five real target classes and then exited without opening a game window.

To retry gameplay, use an environment with working OpenGL, prepare the real game assets/server/libraries, compile `src` plus `resources` into a separate test-only mod, and configure an isolated local presence service and Minecraft test server. `run-live-host.py` records the host orchestration used here and needs host-specific display/runtime paths; it is not an end-user launcher. Do not put the QA mod in normal installations.

Expected manual acceptance: two H2 clients and one nonparticipant on the same server, Tab/overhead appearance, prefixes/colours/long names, F5 own view, F1/invisibility/sneaking/team rules, disconnect/reconnect, server switch, two local sessions, service interruption, and presence/self switches. Confirm no newly visible name through a wall. Repeat at multiple GUI scales and with the user's actual rendering mods.

`CapeWorldProbe` starts a loopback instance of the shipped presence service and an independent presence sender, then verifies received PNGs on a real Minecraft RemotePlayer fixture. It is not two Minecraft processes. Both probes use a unique game directory and record the tested companion hash. Build 9 evidence is under `qa-build9/`.
