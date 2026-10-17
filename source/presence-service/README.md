# KapKan cape sharing and presence, protocol 2

This is the optional HTTP fallback service. KapKan 1.0.5 also bundles a Fabric server relay that does not require this service or a hosted endpoint. Prefer the server relay when you control the Minecraft server; see [the relay setup and visibility limits](../companion/COSMETIC-SYNC.md). Nothing in this package deploys a public service. The launcher ships with an empty service URL and sends no HTTP presence requests unless one is configured.

## Run locally

Python 3.10 or newer is sufficient. No pip packages, subscriptions, Minecraft server plugins, Microsoft client IDs or ownership checks are required.

```sh
python3 -m unittest -v test_service.py
python3 server.py
```

In KapKan Settings, set the service URL to `http://127.0.0.1:8087` for a game on the same computer. Enable presence and save. Use the exact matching KapKan 1.0.5 Fabric build for **26.1**, **26.1.1** or **26.1.2**. For the real badge between different computers, both must use the same externally reachable HTTPS endpoint.

## Private hosting, when you choose to deploy

Use one small Linux machine with persistent storage, Python 3.10+, and an HTTPS reverse proxy. A machine you already own works. A hosting provider's free allocation might work for a small group; availability, quotas and uptime depend on that provider. No provider account or paid resource has been created.

1. Copy this folder to your host, for example `/opt/kapkan-presence`.
2. Create a restricted service user and a persistent writable directory, for example `/var/lib/kapkan-presence`.
3. Set `KAPKAN_DATABASE=/var/lib/kapkan-presence/presence.sqlite3`. Run `server.py` under a process supervisor.
4. Leave `KAPKAN_BIND=127.0.0.1` and `PORT=8087`; the public connection must terminate at your HTTPS proxy.
5. Point your own DNS name at that host. With Caddy, the minimal site configuration is:

```caddyfile
presence.YOUR-DOMAIN.example {
    reverse_proxy 127.0.0.1:8087
}
```

Replace the example name before using it. Apply your normal proxy request limits. Do not put secrets in the URL. The desktop module deliberately rejects non-HTTPS remote URLs and redirects.

6. Set `https://presence.YOUR-DOMAIN.example` in each launcher's Settings → Badges & capes → Multiplayer sharing. Save. Existing running compatible games reload preferences within five seconds; an already in-flight request may finish before cleanup.
7. Verify `/health`, then use two participating game clients on the same Minecraft server.

Do not run this on an ephemeral serverless filesystem. SQLite is real shared persistent storage for this single-host deployment, including across worker processes using the same database. It is not a multi-region database. For horizontal/cloud-serverless hosting, supply a real shared TTL-capable database or a durable actor service first.

## Equipped capes

Protocol 2 adds equipped-cape sharing, including imported/custom PNGs and cloth weight. Both clients need this update and the same service URL. The launcher ships with no shared URL. A single-player world does not announce presence; this service does not host Minecraft worlds.

The client normalizes the selected cape to a static 320 × 512 RGBA PNG, strips metadata, hashes it with SHA-256 and uploads it only when the service does not already have those bytes. No local file paths, skin files, or other files are sent. Changing or hiding the equipped cape updates the announcement. Readers need a live temporary service session and the same hashed server context. Session tokens control requests, but do not prove a Minecraft account’s ownership. Guest profiles remain supported.

Downloads are bounded, CRC/hash/dimensions/pixel data are validated, and the client caches up to 32 images. Remote cloth simulation and GPU textures are released when no longer used. The image service stores at most 64 MiB of cape data and prunes unreferenced images during later requests; idle retained images expire after an hour on the next heartbeat. Appearance records expire with the 75-second presence TTL.

The original `/v1` endpoints stay available for older badge clients. New clients fall back to badge-only operation against a protocol-1 service. Upgrade `server.py` for cape sharing. SQLite schema creation is additive.

## What is shared

The game announces its actual in-game profile UUID and name, SHA-256 of a normalized server host/port with protocol namespace, and a cryptographically random temporary session token. Query batches contain UUIDs and names from the relevant multiplayer player list. Protocol 2 also sends the selected cape PNG/hash and cloth weight. No chat, inventory, Microsoft passwords, access tokens or refresh tokens are sent. The service naturally receives a network IP; this implementation does not log it. A hash is not encryption: common server addresses can be guessed.

The launcher opening alone never announces presence. The 1.0.5 companion works when installed separately from the launcher and no longer requires a launcher-session marker. A marker in older clients was an association, not a security proof. Guest/offline profiles are supported. A live session can be self-reported dishonestly, so this badge does not verify identity, ownership, trust, anti-cheat or client integrity.

UUID and name must both match within the same server context. There is no permanent username allowlist and no cross-server username fallback. Different DNS aliases or ports for the same server will produce separate contexts; use the same server address on participating clients.

## Bounds and expiry

- Heartbeats and batched queries: about every 15 seconds, only while connected.
- Database TTL: 75 seconds. Stale records are pruned during requests.
- Local positive cache: no more than 60 seconds, bounded to 256 player entries.
- Queries: at most 64 profiles per request; up to four batches per cycle.
- Presence body: 12 KiB and response: 64 KiB. Cape PNG: at most 768 KiB; the upload/read JSON allows the corresponding base64 size. Only static 320 × 512 RGBA PNGs with valid CRCs and bounded decoded pixels are accepted.
- Connect timeout: 3 seconds; request timeout: 5 seconds, with a 6-second overall wait bound.
- Outage backoff: grows to 120 seconds plus small jitter. Cached badges expire without popups.
- Every connection gets its own random token. Disconnecting one instance deletes only that token.
- At most 8 new cape downloads per cycle, with 5-second catch-up cycles when necessary.
- Default service limit: 10,000 simultaneous records, 24 request threads, 300 requests/minute per direct peer IP.

Behind a reverse proxy, the app sees the proxy IP. Configure per-client throttling at the proxy and adjust `KAPKAN_REQUESTS_PER_MINUTE` for the expected group; do not blindly trust arbitrary X-Forwarded-For values. Cape downloads add requests during initial loading. Adjust the shared proxy limit for your group; capacity and bandwidth have not been load-benchmarked.

Turning presence off removes all local badges and received capes immediately when the setting reloads, stops future announcements and lookups, and schedules a best-effort leave. A crash or unreachable service is handled by expiry. Presence/appearance rows are short-lived; content-addressed cape bytes are bounded separately as described above. This is not an install history.

## Restore or remove

Stop the service before moving its SQLite database and WAL files. Existing sessions will expire naturally. Deleting the database resets cosmetic presence only; it has no effect on Minecraft accounts, worlds or servers.
