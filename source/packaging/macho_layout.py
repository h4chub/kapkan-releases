#!/usr/bin/env python3
"""Bounded Mach-O structural checks. Does NOT replace Apple's codesign verification."""
from pathlib import Path
import hashlib
import struct

class LayoutError(ValueError):
    pass

def check_slice(data: bytes) -> dict:
    if len(data) < 32:
        raise LayoutError('Truncated Mach-O header')
    magic, cpu, subtype, kind, count, cmdbytes, flags, reserved = struct.unpack_from('<8I', data)
    if magic != 0xfeedfacf or kind != 2:
        raise LayoutError('Expected a 64-bit Mach-O executable')
    if count > 10000 or 32 + cmdbytes > len(data):
        raise LayoutError('Invalid load-command bounds')
    at = 32
    linkedit = None
    signature = None
    for _ in range(count):
        if at+8 > 32+cmdbytes:
            raise LayoutError('Truncated load command')
        cmd, size = struct.unpack_from('<II', data, at)
        if size < 8 or size % 8 or at+size > 32+cmdbytes:
            raise LayoutError('Invalid load command size')
        if cmd == 0x19:
            if size < 72:
                raise LayoutError('Truncated segment')
            name=data[at+8:at+24].split(b'\0')[0]
            vmaddr,vmsize,fo,fs=struct.unpack_from('<4Q',data,at+24)
            if fo+fs > len(data):
                raise LayoutError('Segment extends beyond its architecture slice')
            if name == b'__LINKEDIT':
                linkedit=(fo,fs)
        if cmd == 0x1d:
            signature=struct.unpack_from('<II',data,at+8)
        at += size
    if at != 32+cmdbytes:
        raise LayoutError('Load-command length mismatch')
    if linkedit is None or sum(linkedit) != len(data):
        raise LayoutError('The executable has trailing bytes outside __LINKEDIT')
    verified_pages=0
    if signature is not None:
        start,size=signature
        if start < linkedit[0] or start+size != len(data):
            raise LayoutError('Code signature is not at the slice end')
        # Verify Go's existing SHA-256 ad-hoc code page hashes when present.
        # This does not validate a Developer ID certificate, seal or Gatekeeper trust.
        if size < 12:
            raise LayoutError('Truncated signature')
        magic,length,blobs=struct.unpack_from('>3I',data,start)
        if magic != 0xfade0cc0 or length > size or 12+blobs*8 > length:
            raise LayoutError('Invalid embedded signature superblob')
        for i in range(blobs):
            slot,relative=struct.unpack_from('>II',data,start+12+i*8)
            if relative+8>length:
                raise LayoutError('Signature slot outside bounds')
            bm,bl=struct.unpack_from('>II',data,start+relative)
            if relative+bl>length:
                raise LayoutError('Signature blob outside bounds')
            if slot==0 and bm==0xfade0c02:
                cd=data[start+relative:start+relative+bl]
                if len(cd)<44:
                    raise LayoutError('Truncated CodeDirectory')
                vals=struct.unpack_from('>9I',cd)
                _,_,ver,_,hashoff,_,special,pages,codelimit=vals
                hashsize,hashtype,platform,pageshift=struct.unpack_from('4B',cd,36)
                if hashtype==2 and hashsize==32:
                    if pageshift>20 or codelimit!=start or pages!=(codelimit+(1<<pageshift)-1)//(1<<pageshift):
                        raise LayoutError('Unexpected CodeDirectory coverage')
                    if hashoff+pages*hashsize>len(cd):
                        raise LayoutError('CodeDirectory hashes outside bounds')
                    for page in range(pages):
                        begin=page*(1<<pageshift);end=min(codelimit,begin+(1<<pageshift))
                        expected=cd[hashoff+page*hashsize:hashoff+(page+1)*hashsize]
                        if hashlib.sha256(data[begin:end]).digest()!=expected:
                            raise LayoutError('Code page hash mismatch')
                    verified_pages=pages
    return {'cpu':cpu,'subtype':subtype,'bytes':len(data),'code_pages_verified':verified_pages,'linkedit_end':sum(linkedit)}

def inspect_universal(data: bytes) -> dict:
    if len(data)<8:
        raise LayoutError('Truncated universal header')
    magic,n=struct.unpack_from('>II',data)
    if magic!=0xcafebabe or n!=2 or 8+n*20>len(data):
        raise LayoutError('Expected a two-architecture universal executable')
    entries=[];last_end=8+n*20;types=set()
    for i in range(n):
        cpu,sub,offset,size,align=struct.unpack_from('>IIIII',data,8+i*20)
        if align>20 or offset%(1<<align) or offset<last_end or size<32 or offset+size>len(data):
            raise LayoutError('Invalid universal architecture bounds/alignment')
        if any(data[last_end:offset]):
            raise LayoutError('Non-zero bytes between architecture slices')
        report=check_slice(data[offset:offset+size])
        if cpu!=report['cpu'] or sub!=report['subtype'] or cpu in types:
            raise LayoutError('Architecture table disagrees with the slice')
        types.add(cpu);last_end=offset+size
        entries.append({**report,'offset':offset,'end':last_end,'alignment':align})
    if types!={0x01000007,0x0100000c}:
        raise LayoutError('Expected x86_64 and ARM64')
    if last_end!=len(data):
        raise LayoutError(f'{len(data)-last_end} trailing bytes after the final architecture slice')
    return {'bytes':len(data),'final_slice_end':last_end,'trailing_bytes':0,'slices':entries}

def universal(files: list[Path], dest: Path) -> dict:
    chunks=[f.read_bytes() for f in files]
    records=[];position=8+20*len(chunks)
    for data in chunks:
        info=check_slice(data)
        position=(position+16383)&~16383  # Align each START, never pad the final END.
        records.append((info['cpu'],info['subtype'],position,len(data),14))
        position += len(data)
    combined=bytearray(position)
    struct.pack_into('>II',combined,0,0xcafebabe,len(records))
    for i,(record,data) in enumerate(zip(records,chunks)):
        struct.pack_into('>5I',combined,8+20*i,*record)
        combined[record[2]:record[2]+record[3]]=data
    report=inspect_universal(combined)
    for record,data in zip(records,chunks):
        if combined[record[2]:record[2]+record[3]]!=data:
            raise LayoutError('Architecture bytes were modified')
    dest.write_bytes(combined);dest.chmod(0o755)
    return report

if __name__=='__main__':
    import sys,json
    print(json.dumps(inspect_universal(Path(sys.argv[1]).read_bytes()),indent=2))
