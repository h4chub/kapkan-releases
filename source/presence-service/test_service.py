import concurrent.futures
import json
import secrets
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from server import Store, PresenceServer, TTL

A = {"uuid": "00000000-0000-4000-8000-000000000001", "name": "Alpha"}
B = {"uuid": "00000000-0000-4000-8000-000000000002", "name": "Beta"}
C = {"uuid": "00000000-0000-4000-8000-000000000003", "name": "PlainPlayer"}
S, OTHER = "a"*64, "b"*64

class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.now = 1000.
        self.path = Path(self.tmp.name)/"presence.db"
        self.store = Store(self.path, lambda: self.now)

    def tearDown(self):
        self.tmp.cleanup()

    def join(self, player=A, server=S, session=None):
        session = session or secrets.token_urlsafe(32)
        self.store.heartbeat({"session": session, "server": server, "player": player})
        return session

    def present(self, server=S):
        return [v["uuid"] for v in self.store.query({"server": server, "players": [A,B,C]})["present"]]

    def test_two_participants_one_nonparticipant(self):
        self.join(A); self.join(B)
        self.assertEqual(self.present(), [A["uuid"], B["uuid"]])

    def test_expiry_and_reconnect(self):
        self.join(); self.now += TTL + 1
        self.assertEqual(self.present(), [])
        self.join(); self.assertEqual(self.present(), [A["uuid"]])

    def test_server_switch(self):
        token = self.join(); self.join(server=OTHER, session=token)
        self.assertEqual(self.present(), [])
        self.assertEqual(self.present(OTHER), [A["uuid"]])

    def test_instances_are_independent(self):
        first, second = self.join(), self.join()
        self.store.leave({"session": first})
        self.assertEqual(self.present(), [A["uuid"]])
        self.store.leave({"session": second})
        self.assertEqual(self.present(), [])

    def test_processes_share_database(self):
        self.join(); other = Store(self.path, lambda:self.now)
        self.assertEqual(other.query({"server":S,"players":[A]})["present"][0]["uuid"],A["uuid"])

    def test_names_do_not_cross_uuid_or_context(self):
        self.join(); duplicate = dict(A,uuid=C["uuid"])
        self.assertEqual(self.store.query({"server":S,"players":[duplicate]})["present"],[])
        self.assertEqual(self.present(OTHER),[])

    def test_invalid_and_batch_limit(self):
        for body in [{"server":S,"players":[A]*65},{"server":"server.example","players":[A]},
                     {"server":S,"players":[dict(A,uuid="wrong")]}]:
            with self.assertRaises(ValueError):self.store.query(body)

    def test_concurrent_sessions(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            sessions = list(pool.map(lambda _:self.join(),range(24)))
        for token in sessions[:-1]:self.store.leave({"session":token})
        self.assertEqual(self.present(),[A["uuid"]])

    def test_http_protocol_and_disabled_cleanup(self):
        server=PresenceServer(("127.0.0.1",0),self.store)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        origin="http://127.0.0.1:"+str(server.server_port)
        def post(path,data):
            req=urllib.request.Request(origin+path,data=json.dumps(data).encode(),headers={"Content-Type":"application/json"})
            return json.load(urllib.request.urlopen(req,timeout=5))
        try:
            token=secrets.token_urlsafe(32)
            self.assertEqual(post('/v1/heartbeat',dict(session=token,server=S,player=A))['ttl'],TTL)
            self.assertEqual(len(post('/v1/query',dict(server=S,players=[A,B,C]))['present']),1)
            post('/v1/leave',dict(session=token));self.assertEqual(self.present(),[])
            with self.assertRaises(urllib.error.HTTPError) as result:post('/v1/query',dict(server=S,players=[A]*65))
            self.assertEqual(result.exception.code,400)
        finally:server.shutdown();server.server_close()

if __name__ == '__main__':unittest.main(verbosity=2)
