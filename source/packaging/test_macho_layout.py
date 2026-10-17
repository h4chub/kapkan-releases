import struct, tempfile, unittest
from pathlib import Path
from macho_layout import universal, inspect_universal, LayoutError
class LayoutTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.inputs=[]
        for cpu in [0x01000007,0x0100000c]:
            b=bytearray(struct.pack('<8I',0xfeedfacf,cpu,3 if cpu==0x01000007 else 0,2,1,72,0,0))
            b+=struct.pack('<II16sQQQQiiII',0x19,72,b'__LINKEDIT',0,8,104,8,1,1,0,0)+b'PAYLOAD!'
            p=self.root/f'{cpu}';p.write_bytes(b);self.inputs.append(p)
        self.output=self.root/'KapKan'
    def tearDown(self):self.temp.cleanup()
    def test_no_tail_and_exact_slices(self):
        report=universal(self.inputs,self.output);data=self.output.read_bytes();self.assertEqual(report['bytes'],report['final_slice_end'])
        for rec,p in zip(report['slices'],self.inputs):self.assertEqual(data[rec['offset']:rec['end']],p.read_bytes())
        self.assertEqual(report['trailing_bytes'],0)
    def test_rc1_regression_trailing_padding_is_rejected(self):
        universal(self.inputs,self.output)
        with self.assertRaisesRegex(LayoutError,'trailing bytes'):inspect_universal(self.output.read_bytes()+bytes(10510))
    def test_out_of_range_is_rejected(self):
        universal(self.inputs,self.output);data=bytearray(self.output.read_bytes());struct.pack_into('>I',data,8+20+12,len(data)*2)
        with self.assertRaises(LayoutError):inspect_universal(data)
    def test_architecture_table_mismatch_is_rejected(self):
        universal(self.inputs,self.output);data=bytearray(self.output.read_bytes());struct.pack_into('>I',data,8,123)
        with self.assertRaises(LayoutError):inspect_universal(data)
    def test_overlapping_slices_rejected(self):
        universal(self.inputs,self.output);data=bytearray(self.output.read_bytes());struct.pack_into('>I',data,8+20+8,16384)
        with self.assertRaises(LayoutError):inspect_universal(data)
    def test_nonzero_gap_rejected(self):
        universal(self.inputs,self.output);data=bytearray(self.output.read_bytes());data[1024]=1
        with self.assertRaises(LayoutError):inspect_universal(data)
    def test_truncated_file_rejected(self):
        universal(self.inputs,self.output)
        with self.assertRaises(LayoutError):inspect_universal(self.output.read_bytes()[:-1])
    def test_trailing_thin_slice_rejected(self):
        self.inputs[0].write_bytes(self.inputs[0].read_bytes()+b'\0')
        with self.assertRaisesRegex(LayoutError,'LINKEDIT'):universal(self.inputs,self.output)
if __name__=='__main__':unittest.main(verbosity=2)
