"""Deterministic pixel mask from the supplied logo. No tracing or redrawing.
H1 contains RGB white-on-black artwork, not a transparent logo. RGB luminance
is the only supplied coverage information; retain it as alpha, white RGB.
The full original is retained in resources for exact provenance.
"""
from PIL import Image
from pathlib import Path
import shutil,json
root=Path(__file__).resolve().parents[1]
src=root/'app/KapKan-AppIcon.png';im=Image.open(src).convert('RGB')
alpha=im.convert('L');bbox=alpha.point(lambda p:255 if p>128 else 0).getbbox()
# Tight framing only; all logo pixels and original aspect ratio are retained.
a=alpha.crop(bbox);badge=Image.new('RGBA',a.size,(255,255,255,0));badge.putalpha(a)
for out in [root/'java-resources/gg/kapkan/h2/double-k-white.png',root/'companion/src/main/resources/assets/kapkan/textures/font/double_k.png']:
 out.parent.mkdir(parents=True,exist_ok=True);badge.save(out)
shutil.copy2(src,root/'companion/src/main/resources/assets/kapkan/official-logo-original.png')
f={'providers':[{'type':'bitmap','file':'kapkan:font/double_k_glyph.png','height':8,'ascent':7,'chars':['\ue000']},{'type':'space','advances':{'\ue001':2}}]}
(root/'companion/src/main/resources/assets/kapkan/font/badge.json').write_text(json.dumps(f,indent=2)+'\n')
print('Original logo bbox:',bbox,'badge size:',a.size)
