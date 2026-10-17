import base64
import hashlib
import json
import secrets
import struct
import zlib
import test_service as fixture
import unittest
from test_service import A, B, C, S, OTHER
from server import Store, TTL


def png(color=b'\xff\xff\xff\xff', width=320, height=512):
    def chunk(kind, data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,6,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+color*width)*height))+chunk(b'IEND',b'')


class CapeTests(unittest.TestCase):
    setUp = fixture.ServiceTests.setUp
    tearDown = fixture.ServiceTests.tearDown
    join = fixture.ServiceTests.join
    present = fixture.ServiceTests.present
    def equipped(self, player=A, server=S, image=None):
        image = image or png()
        token = secrets.token_urlsafe(32)
        key = hashlib.sha256(image).hexdigest()
        body = dict(session=token,server=server,player=player,cape=dict(hash=key,material='Flow'))
        self.assertTrue(self.store.heartbeat_v2(body).get('needCape',False))
        self.store.upload_cape(dict(session=token,server=server,hash=key,png=base64.b64encode(image).decode()))
        return body

    def test_custom_cape_roundtrip_and_no_reupload(self):
        a = self.equipped()
        token = self.join(B)
        result = self.store.query_v2(dict(session=token,server=S,players=[A,B,C]))['present']
        self.assertEqual(result[0]['cape'], a['cape'])
        self.assertNotIn('cape',result[1])
        data = self.store.read_cape(dict(session=token,server=S,hash=a['cape']['hash']))
        self.assertEqual(base64.b64decode(data['png']),png())
        self.assertFalse(self.store.heartbeat_v2(a)['needCape'])

    def test_new_selection_replaces_old_cape(self):
        a = self.equipped()
        image = png(b'\x88\x22\x33\xff');key=hashlib.sha256(image).hexdigest()
        a['cape']=dict(hash=key,material='Heavy')
        self.assertTrue(self.store.heartbeat_v2(a)['needCape'])
        self.store.upload_cape(dict(session=a['session'],server=S,hash=key,png=base64.b64encode(image).decode()))
        self.assertEqual(self.store.query_v2(dict(session=a['session'],server=S,players=[A]))['present'][0]['cape'],a['cape'])

    def test_disable_and_leave_stop_sharing(self):
        a = self.equipped();token=self.join(B);key=a['cape']['hash']
        a['cape']=None;self.store.heartbeat_v2(a)
        self.assertNotIn('cape',self.store.query_v2(dict(session=token,server=S,players=[A]))['present'][0])
        with self.assertRaises(ValueError):self.store.read_cape(dict(session=token,server=S,hash=key))
        self.store.leave(a)
        self.assertEqual(self.store.query_v2(dict(session=token,server=S,players=[A]))['present'],[])

    def test_session_and_context_required(self):
        a=self.equipped();other=self.join(B,OTHER)
        for token,context in [(secrets.token_urlsafe(32),S),(other,OTHER),(other,S)]:
            with self.assertRaises(ValueError):self.store.read_cape(dict(session=token,server=context,hash=a['cape']['hash']))
        with self.assertRaises(ValueError):self.store.query_v2(dict(server=S,players=[A]))

    def test_invalid_images_and_hashes(self):
        for image in [png(width=1024),png()[:-5],png()+b'extra',b'\x89PNG\r\n\x1a\n'+b'bad',png()[:45]+b'bad'+png()[48:]]:
            with self.assertRaises(ValueError):Store.validate_png(image)
        a=self.equipped()
        with self.assertRaises(ValueError):self.store.upload_cape(dict(session=a['session'],server=S,hash='a'*64,png=base64.b64encode(png()).decode()))

    def test_upload_must_match_equipped_selection(self):
        token=self.join();data=png();key=hashlib.sha256(data).hexdigest()
        with self.assertRaises(ValueError):self.store.upload_cape(dict(session=token,server=S,hash=key,png=base64.b64encode(data).decode()))

    def test_cape_expiry_and_storage_cleanup(self):
        a=self.equipped();key=a['cape']['hash'];self.now+=TTL+1
        token=self.join(B)
        self.assertEqual(self.store.query_v2(dict(session=token,server=S,players=[A]))['present'],[])
        with self.assertRaises(ValueError):self.store.read_cape(dict(session=token,server=S,hash=key))
        self.now+=3601;self.store.heartbeat_v2(dict(session=token,server=S,player=B))
        with self.store.connect() as db:self.assertEqual(db.execute('SELECT count(*) FROM capes').fetchone()[0],0)

    def test_name_matching_and_v1_compatibility(self):
        a=self.equipped()
        self.assertEqual(self.present(),[A['uuid']])
        result=self.store.query_v2(dict(session=a['session'],server=S,players=[dict(A,name='OtherName')]))['present']
        self.assertEqual(result,[])
