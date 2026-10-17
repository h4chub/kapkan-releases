#!/usr/bin/env python3
"""Build a minimal AMD64 COFF resource object: original KapKan ICO + asInvoker manifest.
The resource data-entry addresses are IMAGE_REL_AMD64_ADDR32NB relocations,
which Go's PE linker resolves against the emitted .rsrc section.
"""
from pathlib import Path
import struct
ROOT=Path(__file__).resolve().parents[1]
ico=(ROOT/'app/KapKan.ico').read_bytes()
reserved,kind,count=struct.unpack_from('<HHH',ico)
assert reserved==0 and kind==1 and 1<=count<=20
icons={};group=bytearray(struct.pack('<HHH',0,1,count))
for i in range(count):
 w,h,nc,r,planes,bits,size,offset=struct.unpack_from('<BBBBHHII',ico,6+16*i)
 assert offset+size<=len(ico)
 icons[i+1]={1033:ico[offset:offset+size]}
 group+=struct.pack('<BBBBHHIH',w,h,nc,r,planes,bits,size,i+1)
manifest=b'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
 <assemblyIdentity version="1.0.5.0" processorArchitecture="amd64" name="KapKan.Client" type="win32"/>
 <description>KapKan Client</description>
 <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3"><security><requestedPrivileges><requestedExecutionLevel level="asInvoker" uiAccess="false"/></requestedPrivileges></security></trustInfo>
 <application xmlns="urn:schemas-microsoft-com:asm.v3"><windowsSettings><dpiAware xmlns="http://schemas.microsoft.com/SMI/2005/WindowsSettings">true</dpiAware></windowsSettings></application>
 <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1"><application><supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}"/></application></compatibility>
</assembly>'''
tree={3:icons,14:{1:{1033:bytes(group)}},24:{1:{1033:manifest}}}
data=bytearray();relocs=[]
def alloc(n):
 while len(data)%4:data.append(0)
 p=len(data);data.extend(b'\0'*n);return p
def node(items):
 p=alloc(16+len(items)*8)
 struct.pack_into('<IIHHHH',data,p,0,0,0,0,0,len(items))
 for i,(id,child) in enumerate(sorted(items.items())):
  if isinstance(child,dict):off=node(child)|0x80000000
  else:
   off=alloc(16);body=alloc(len(child));data[body:body+len(child)]=child
   struct.pack_into('<IIII',data,off,body,len(child),0,0);relocs.append(off)
  struct.pack_into('<II',data,p+16+8*i,id,off)
 return p
assert node(tree)==0
raw=60;reloc_pos=raw+len(data);sym_pos=reloc_pos+10*len(relocs)
header=struct.pack('<HHIIIHH',0x8664,1,0,sym_pos,2,0,0)
section=struct.pack('<8sIIIIIIHHI',b'.rsrc\0\0\0',0,0,len(data),raw,reloc_pos,0,len(relocs),0,0x40300040)
relocations=b''.join(struct.pack('<IIH',p,0,3) for p in relocs)
sym=struct.pack('<8sIhHBB',b'.rsrc\0\0\0',0,1,0,3,1)
aux=struct.pack('<IHHIhBBH',len(data),len(relocs),0,0,0,0,0,0)
obj=header+section+data+relocations+sym+aux+struct.pack('<I',4)
for target in ['launcher','installer']:
 (ROOT/f'native/cmd/{target}/rsrc_windows_amd64.syso').write_bytes(obj)
print(f'Original double-K Windows icon embedded in COFF: {count} sizes')
