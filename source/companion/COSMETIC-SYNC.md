# KapKan 1.0.5 multiplayer cosmetics

## Cause and repair

The previous client only populated remote badges and capes through the optional HTTP presence service. Its URL was blank by default, and no public service was deployed. Local cape selection could therefore work while every remote participant remained invisible to KapKan. That was missing infrastructure rather than a universal rendering failure. The previous launcher-session gate also excluded separately installed clients.

Version 1.0.5 includes an optional Fabric server relay in the same exact-version mod JAR. Installing that JAR on both participating clients and their server enables sharing without a separate website, account registration or HTTP host. The client is also usable outside the KapKan launcher. Saved sharing preferences remain effective.

## Install

1. Use Fabric Loader 0.19.5+ and Java 25+ for the matching supported Minecraft version.
2. Put the matching `KapKan-1.0.5-mc<version>-fabric.jar` in each participating client's `mods` folder.
3. Put that same exact-version JAR in the Fabric server's `mods` folder and restart it. This release includes its three required Fabric API modules as nested dependencies. It can coexist with a matching full Fabric API installation.
4. Enable cosmetic sharing in KapKan. Select a cape and join the server. When connected, the cosmetic status reads **Server cosmetic relay connected**.

A vanilla server can still accept the client mod; ordinary play, local HUD and local cosmetics do not require the relay. Automatic remote sharing does require the relay, or the explicitly configured HTTP fallback described below. No public endpoint was created by this update.

## Who can see what

Only supported KapKan clients render KapKan badges and custom capes. Players using an ordinary unmodified client, Feather, Lunar or another client without this protocol/renderer do not automatically see them. The optional server JAR relays cosmetics but does not make other clients capable of drawing them. Skins imported in KapKan remain local and are not included in this cosmetic protocol.

The badge identifies a participating KapKan instance. It does not prove account ownership, trust, anti-cheat status or client integrity. The server assigns the transmitting player's UUID from its current connected `ServerPlayer`; clients cannot submit a UUID for another participant. On an online-mode Minecraft server, account authentication remains Minecraft's responsibility. On an offline-mode server, this is the server-assigned offline identity, not a verified Microsoft identity.

All compatible participants on one server receive badge/cape selection metadata. Cape image bytes are requested only for loaded players and must currently belong to a connected participant on that same server. Requests cannot read arbitrary URLs, server files or assets left in the cache by departed users. The Minecraft server can naturally observe relayed cape content.

## No Kapkan Cape

**No Kapkan Cape** sets the existing saved `cape=false` preference. It removes the local KapKan override and broadcasts its removal to connected supported clients on the next game tick. The badge remains enabled. A late image decode or upload cannot re-equip the removed cape.

This option does not hide a player's existing Minecraft account cape. Vanilla cape visibility rules and the Skin Customization cape setting still apply. The saved previous KapKan design remains available for later re-selection.

## Lifecycle and bounds

- Reconnection starts a new client relay view. The server supplies currently connected peers again. The same game connection keeps metadata through respawn and dimension changes; per-world motion and GPU textures are reset and recreated as needed.
- Selecting a replacement removes the old remote cape before validated replacement artwork is published. Disconnecting removes the badge and cape immediately; incomplete asset transfers time out and can retry.
- Each image is a static normalized 320 × 512 RGBA PNG of at most 768 KiB, validated with SHA-256, PNG chunk CRCs, dimensions and bounded decompression.
- Wire protocol 1 uses pieces of at most 24 KiB, below Minecraft's ordinary custom payload limit. Client uploads send at most two pieces per tick. The server drains at most 32 packets per tick and four per recipient per pass.
- Server metadata is limited to 256 participants. Incomplete uploads share a 16 MiB cap and expire after 15 seconds. Server and client image caches hold at most 32 images; the server cache also has a 24 MiB byte limit.
- At most four client images are assembling at a time. The PNG validation queues and remote pixel decoder queue are bounded. Cache eviction or interrupted transfers can briefly show no KapKan cape while an image is fetched again; gameplay continues.
- Outgoing queues have per-client and server-wide limits. Metadata is prioritized over image pieces so removal is responsive. Updates are rate limited; repeatedly changing a cape very quickly may coalesce or be ignored until a later refresh.
- Asset decoding never runs inside the render loop. GPU uploads happen on the client thread after decoding, and unused world textures are released.

These are resource bounds, not a claim that a 256-player cosmetics load was benchmarked. The relay is an optional cosmetic channel and does not affect Minecraft accounts, inventory, chat, worlds or server permissions.

## Optional HTTP fallback

When the server does not advertise the relay, an already configured HTTPS protocol-2 presence endpoint remains supported. Both clients must use the same reachable service and matching server-address context. It is unset by default. See `presence-service/README.md` for private hosting and tests.

That older HTTP protocol uses self-reported UUID/name pairs and temporary sessions. It does not authenticate Minecraft ownership and can be impersonated by a dishonest client. It should not be used as an authorization or trust service. A relay-capable server takes priority over this fallback; an explicit No Cape received from the relay cannot be replaced by an older HTTP cached cape.

## Verification

`RelaySyncTest` exercises the actual serialized relay protocol, server hub and two independent client cache instances. It verifies bidirectional selections, material changes, immediate None, opted-out badges, disconnect/reconnect, stale-worker cancellation, a multi-piece imported image, packet limits, interrupted-upload expiry and bounded state. The legacy `PresenceEngineTest`, `CapeSyncTest` and Python HTTP service tests are retained.

Those deterministic transport/cache tests are separate from actual Minecraft verification. Consult the release's `qa/1.0.5` report for which exact game versions were launched, whether two game processes were connected, rendered evidence and any unverified platform/input cases.
