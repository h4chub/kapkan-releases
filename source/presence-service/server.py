"""KapKan cosmetic presence. Single-host service with durable shared SQLite state.
Only the Python standard library is required. Put HTTPS in front of this service.
"""
import hashlib
import json
import os
import re
import sqlite3
import threading
import time
import uuid
import base64
import struct
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TTL = 75
MAX_BATCH = 64
MAX_BODY = 12288
MAX_PNG = 768 * 1024
MAX_CAPE_STORAGE = 64 * 1024 * 1024


class Store:
    def __init__(self, path, clock=time.time, limit=10000):
        self.path, self.clock, self.limit = str(path), clock, limit
        with self.connect() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.executescript("""CREATE TABLE IF NOT EXISTS sessions (
                token TEXT PRIMARY KEY, server TEXT NOT NULL, uuid TEXT NOT NULL,
                name TEXT NOT NULL, expires REAL NOT NULL);
                CREATE INDEX IF NOT EXISTS lookup ON sessions(server,uuid,expires);
                CREATE INDEX IF NOT EXISTS expiry ON sessions(expires);
                CREATE TABLE IF NOT EXISTS appearances (token TEXT PRIMARY KEY, hash TEXT NOT NULL, material TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS capes (hash TEXT PRIMARY KEY, png BLOB NOT NULL, touched REAL NOT NULL);""")

    def connect(self):
        return sqlite3.connect(self.path, timeout=4)

    @staticmethod
    def token(value):
        if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]{43}", value):
            raise ValueError("Invalid session")
        return hashlib.sha256(value.encode()).hexdigest()

    @staticmethod
    def context(value):
        if not isinstance(value, str) or not re.fullmatch(r"[a-f0-9]{64}", value):
            raise ValueError("Invalid server context")
        return value

    @staticmethod
    def player(value):
        if not isinstance(value, dict):
            raise ValueError("Invalid player")
        ident = str(uuid.UUID(value.get("uuid", "")))
        name = value.get("name", "")
        if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9_]{1,16}", name):
            raise ValueError("Invalid name")
        return ident, name

    def heartbeat(self, body):
        token = self.token(body.get("session"))
        context = self.context(body.get("server"))
        ident, name = self.player(body.get("player"))
        now = self.clock()
        with self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("DELETE FROM sessions WHERE expires <= ?", (now,))
            exists = db.execute("SELECT 1 FROM sessions WHERE token=?", (token,)).fetchone()
            if not exists and db.execute("SELECT count(*) FROM sessions").fetchone()[0] >= self.limit:
                raise OverflowError("Service is at capacity")
            db.execute("INSERT INTO sessions VALUES(?,?,?,?,?) ON CONFLICT(token) DO UPDATE SET "
                       "server=excluded.server,uuid=excluded.uuid,name=excluded.name,expires=excluded.expires",
                       (token, context, ident, name, now + TTL))
        return {"ttl": TTL}

    def query(self, body):
        context = self.context(body.get("server"))
        players = body.get("players")
        if not isinstance(players, list) or len(players) > MAX_BATCH:
            raise ValueError("At most 64 players per batch")
        players = [self.player(p) for p in players]
        now = self.clock()
        with self.connect() as db:
            db.execute("DELETE FROM sessions WHERE expires <= ?", (now,))
            found = []
            for ident, name in players:
                expiry = db.execute("SELECT MAX(expires) FROM sessions WHERE server=? AND uuid=? "
                                    "AND lower(name)=lower(?) AND expires>?", (context, ident, name, now)).fetchone()[0]
                if expiry:
                    found.append({"uuid": ident, "ttl": min(TTL, max(0, int(expiry - now)))})
        return {"present": found}

    def leave(self, body):
        with self.connect() as db:
            token = self.token(body.get("session"))
            db.execute("DELETE FROM sessions WHERE token=?", (token,))
            db.execute("DELETE FROM appearances WHERE token=?", (token,))
        return {"ok": True}

    @staticmethod
    def cape_hash(value):
        if not isinstance(value, str) or not re.fullmatch(r"[a-f0-9]{64}", value):
            raise ValueError("Invalid cape hash")
        return value

    def active(self, db, body):
        token, context = self.token(body.get("session")), self.context(body.get("server"))
        if not db.execute("SELECT 1 FROM sessions WHERE token=? AND server=? AND expires>?",
                          (token, context, self.clock())).fetchone():
            raise ValueError("Inactive session")
        return token, context

    def heartbeat_v2(self, body):
        cape = body.get("cape")
        if cape is not None:
            if not isinstance(cape, dict) or cape.get("material") not in ("Flow", "Silk", "Heavy"):
                raise ValueError("Invalid cape")
            self.cape_hash(cape.get("hash"))
        reply = self.heartbeat(body)
        token = self.token(body.get("session"))
        with self.connect() as db:
            db.execute("DELETE FROM appearances WHERE token NOT IN (SELECT token FROM sessions WHERE expires>?)", (self.clock(),))
            if cape is None:
                db.execute("DELETE FROM appearances WHERE token=?", (token,))
            else:
                db.execute("INSERT INTO appearances VALUES(?,?,?) ON CONFLICT(token) DO UPDATE SET hash=excluded.hash,material=excluded.material",
                           (token, cape["hash"], cape["material"]))
                db.execute("UPDATE capes SET touched=? WHERE hash=?", (self.clock(), cape["hash"]))
                reply["needCape"] = not bool(db.execute("SELECT 1 FROM capes WHERE hash=?", (cape["hash"],)).fetchone())
            db.execute("DELETE FROM capes WHERE touched<? AND hash NOT IN (SELECT hash FROM appearances)", (self.clock()-3600,))
        return reply

    def query_v2(self, body):
        with self.connect() as db:
            self.active(db, body)
        reply = self.query(body)
        names = {str(uuid.UUID(player["uuid"])): player["name"] for player in body["players"]}
        with self.connect() as db:
            for item in reply["present"]:
                row = db.execute("SELECT a.hash,a.material FROM sessions s JOIN appearances a ON s.token=a.token "
                                 "JOIN capes c ON c.hash=a.hash WHERE s.server=? AND s.uuid=? AND lower(s.name)=lower(?) AND s.expires>? "
                                 "ORDER BY s.expires DESC,s.token LIMIT 1", (body["server"],item["uuid"],names[item["uuid"]],self.clock())).fetchone()
                if row:
                    item["cape"] = {"hash": row[0], "material": row[1]}
        return reply

    @staticmethod
    def validate_png(data):
        # Only normalized static RGBA PNGs produced by the client; no metadata or remote URLs.
        if not 45 <= len(data) <= MAX_PNG or data[:8] != b"\x89PNG\r\n\x1a\n":
            raise ValueError("Invalid PNG")
        pos, compressed, header, ended = 8, bytearray(), False, False
        while pos+12 <= len(data):
            size = int.from_bytes(data[pos:pos+4], "big")
            kind, payload = data[pos+4:pos+8], data[pos+8:pos+8+size]
            if pos+12+size > len(data) or zlib.crc32(kind+payload) != int.from_bytes(data[pos+8+size:pos+12+size], "big"):
                raise ValueError("Invalid PNG chunk")
            if kind == b"IHDR" and pos == 8 and size == 13:
                if struct.unpack(">IIBBBBB", payload) != (320,512,8,6,0,0,0):
                    raise ValueError("Expected 320x512 RGBA")
                header = True
            elif kind == b"IDAT" and header:
                compressed.extend(payload)
            elif kind == b"IEND" and size == 0 and compressed:
                ended = True
            else:
                raise ValueError("Unsupported PNG chunk")
            pos += 12+size
            if ended:
                break
        decoder = zlib.decompressobj()
        pixels = decoder.decompress(bytes(compressed), (320*4+1)*512+1)
        if not ended or pos != len(data) or len(pixels) != (320*4+1)*512 or not decoder.eof or decoder.unused_data:
            raise ValueError("Invalid PNG pixels")
        if any(pixels[row*(320*4+1)] > 4 for row in range(512)):
            raise ValueError("Invalid PNG filter")

    def upload_cape(self, body):
        key = self.cape_hash(body.get("hash"))
        raw = body.get("png")
        if not isinstance(raw, str) or len(raw) > MAX_PNG*4//3+4:
            raise ValueError("Cape too large")
        data = base64.b64decode(raw, validate=True)
        if hashlib.sha256(data).hexdigest() != key:
            raise ValueError("Cape hash mismatch")
        self.validate_png(data)
        with self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            token, _ = self.active(db, body)
            if not db.execute("SELECT 1 FROM appearances WHERE token=? AND hash=?", (token,key)).fetchone():
                raise ValueError("Cape is not equipped")
            if not db.execute("SELECT 1 FROM capes WHERE hash=?", (key,)).fetchone():
                db.execute("DELETE FROM capes WHERE hash NOT IN (SELECT a.hash FROM appearances a JOIN sessions s ON s.token=a.token WHERE s.expires>?)", (self.clock(),))
                total = db.execute("SELECT COALESCE(SUM(length(png)),0) FROM capes").fetchone()[0]
                if total+len(data) > MAX_CAPE_STORAGE:
                    raise OverflowError("Cape storage full")
                db.execute("INSERT INTO capes VALUES(?,?,?)", (key,data,self.clock()))
        return {"ok": True}

    def read_cape(self, body):
        key = self.cape_hash(body.get("hash"))
        with self.connect() as db:
            _, context = self.active(db, body)
            row = db.execute("SELECT c.png FROM capes c JOIN appearances a ON c.hash=a.hash JOIN sessions s ON a.token=s.token "
                             "WHERE c.hash=? AND s.server=? AND s.expires>? LIMIT 1", (key,context,self.clock())).fetchone()
        if row is None:
            raise ValueError("Cape no longer present")
        return {"hash": key, "png": base64.b64encode(row[0]).decode("ascii")}


class PresenceServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, store):
        super().__init__(address, Handler)
        self.store = store
        self.slots = threading.BoundedSemaphore(24)
        self.rate_lock, self.rates = threading.Lock(), {}

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.slots.release()

    def allowed(self, ip):
        now = int(time.monotonic() // 60)
        key = hashlib.sha256(ip.encode()).digest()
        with self.rate_lock:
            if len(self.rates) > 4096:
                self.rates = {k: v for k, v in self.rates.items() if v[0] == now}
                if len(self.rates) > 4096:
                    return False
            epoch, count = self.rates.get(key, (now, 0))
            count = count + 1 if epoch == now else 1
            self.rates[key] = (now, count)
            return count <= int(os.getenv("KAPKAN_REQUESTS_PER_MINUTE", "300"))


class Handler(BaseHTTPRequestHandler):
    server_version = "KapKanPresence/2"

    def setup(self):
        super().setup()
        self.connection.settimeout(6)

    def log_message(self, fmt, *args):
        pass  # No player, server, session, IP or query logging by default.

    def reply(self, status, value):
        data = json.dumps(value, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        if status in (429, 503):
            self.send_header("Retry-After", "30")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.reply(200 if self.path == "/health" else 404,
                   {"service": "kapkan-presence", "protocol": 2} if self.path == "/health" else {"error": "Not found"})

    def do_POST(self):
        if not self.server.allowed(self.client_address[0]):
            self.reply(429, {"error": "Rate limited"})
            return
        try:
            length = int(self.headers.get("Content-Length", "-1"))
            limit = MAX_PNG*4//3+2048 if self.path == "/v2/cape/upload" else MAX_BODY
            if length < 0 or length > limit:
                self.reply(413, {"error": "Body too large or missing length"})
                return
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                raise ValueError("Expected object")
            action = {"/v1/heartbeat": self.server.store.heartbeat, "/v1/query": self.server.store.query,
                      "/v1/leave": self.server.store.leave, "/v2/heartbeat": self.server.store.heartbeat_v2,
                      "/v2/query": self.server.store.query_v2, "/v2/cape/upload": self.server.store.upload_cape,
                      "/v2/cape/read": self.server.store.read_cape}.get(self.path)
            if action is None:
                self.reply(404, {"error": "Not found"})
                return
            self.reply(200, action(body))
        except (ValueError, TypeError, AttributeError, zlib.error):
            self.reply(400, {"error": "Invalid request"})
        except (sqlite3.Error, OverflowError):
            self.reply(503, {"error": "Temporarily unavailable"})


if __name__ == "__main__":
    database = os.getenv("KAPKAN_DATABASE", "presence.sqlite3")
    address = (os.getenv("KAPKAN_BIND", "127.0.0.1"), int(os.getenv("PORT", "8087")))
    PresenceServer(address, Store(database)).serve_forever()
