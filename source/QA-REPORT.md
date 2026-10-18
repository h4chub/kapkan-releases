# KapKan 1.0.7 verification

This release changes the existing equipment HUD: transparent icons, compact occupied rows, colored remaining/max values, and durable held items in either hand. The existing armor setting and saved HUD/profile placement keys are retained. Empty equipment does not draw in gameplay; the editor retains a selectable hint.

## Companion and native visual acceptance

All exact Minecraft 26.1, 26.1.1 and 26.1.2 companions build against unchanged pinned official game/loader/API inputs and pass 1,350 deterministic checks each, including 141 new durability color/label checks. Those cover the dark-green 498/500 case, the yellow midpoint, near-zero red, interpolation/clamping and integer limits. Initial sandbox runs could not bind the isolated local test server; reruns with local-server permission pass. The original sandbox errors remain recorded separately.

Native macOS arm64 Minecraft 26.1.2 uses the verified official Zulu25 runtime and unchanged ImmediatelyFast module in a fresh private world. Real equipped helmet/chest/legs/boots, main-hand mace and offhand shield are compared with the actual HUD rows. Bright and dark wall screenshots verify transparent rendering, order, readable colored counters and the one-pixel text shadow. The mace reads 498/500 in dark green; a half-worn chest/shield is yellow and nearly broken leggings are red. See qa/1.0.7/game for exact source, artifact hash, assertions and screenshots. Native gameplay on 26.1/26.1.1 was not rerun for this patch.

## Desktop packaging and migration

The desktop JAR, both Mac native binaries, Windows binary/resources and native Go units build/pass. Release policy passes 33 assertions, real companion migration passes 126 checks, unchanged rendering integration passes 56 checks and updater fixtures pass 14 synthetic version/platform cases. These are recorded under qa/1.0.7. Real previous 1.0.6 companion hashes are pinned for safe migration, while earlier 1.0.5 migration coverage is retained. Package/archive/signature and built Windows installer payload results are checked before publication; see final package receipts. Current public delivery verification is recorded separately in PUBLICATION.json and releases/1.0.7/verification.

The optional rendering module, upstream binary/source archives and licenses are unchanged from 1.0.6. Current integration fixtures are rerun; earlier all-three-target native runtime and FPS experiments remain historical evidence under qa/1.0.6. They are not relabeled as new 1.0.7 acceptance. No new FPS measurement or gain is claimed.

## Limits

Native Windows installation, Intel Mac execution, macOS 12 device acceptance, Microsoft sign-in, arbitrary modpacks/GPUs, public servers and interactive microphone capture are not established by these tests. The Mac app remains locally capability-signed and is not Developer ID notarized; Windows is unsigned. Existing Mac runtime/signing behavior is retained. Historical 1.0.6 results are in history/1.0.6 and qa/1.0.6.
