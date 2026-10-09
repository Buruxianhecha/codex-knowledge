#!/usr/bin/env python3
"""Download and verify *exact* upstream Google Fonts files at an immutable Git commit.

Reject HTML, truncated fonts, mutable CDN outputs and changed licenses.
Only genuine complete TTF binaries go into the final APK; fail CI otherwise.
OFL license files are also included inside the APK.
"""
import hashlib
import pathlib
import struct
import sys
import time
import urllib.request

root=pathlib.Path(sys.argv[1]).resolve()
ref="500387eceede95ab421e5fac16f6ca1b01df0e09"
base=f"https://raw.githubusercontent.com/google/fonts/{ref}/ofl"
resources=(
    ("longcang/LongCang-Regular.ttf", "363bb08696fe4d3827bd36d3d2fbc028d1205161",
     "app/src/main/assets/display_fonts/LongCang-Regular.ttf", "font"),
    ("zhimangxing/ZhiMangXing-Regular.ttf", "d037d1f00395175de01de33e62957c30a008a1b1",
     "app/src/main/assets/display_fonts/ZhiMangXing-Regular.ttf", "font"),
    ("longcang/OFL.txt", "06a1757ca2ae5ff689aac4b410fffa98cd73dad0",
     "app/src/main/assets/font_licenses/LongCang-OFL.txt", "license"),
    ("zhimangxing/OFL.txt", "8e1e6c70cd7fad5b34f6337c694fed96b5e38884",
     "app/src/main/assets/font_licenses/ZhiMangXing-OFL.txt", "license"),
)
def fetch(url):
    error=None
    for attempt in range(4):
        try:
            req=urllib.request.Request(url,headers={"User-Agent":"HuaiMin-BundledFonts/0.37.38"})
            with urllib.request.urlopen(req,timeout=45) as response:
                return response.read(12*1024*1024+1)
        except Exception as e:
            error=e
            time.sleep(attempt+1)
    raise RuntimeError(f"Cannot download official file: {url} ({error})")

def font_sane(b):
    if len(b) not in range(2_000_000,12_000_001) or b[:4]!=b"\x00\x01\x00\x00":
        return False
    tables=struct.unpack_from(">H",b,4)[0]
    if not 4 <= tables <= 512 or 12+16*tables > len(b):
        return False
    for n in range(tables):
        off,length=struct.unpack_from(">II",b,12+16*n+8)
        if length and (off < 12+16*tables or off+length>len(b)):
            return False
    return True

for src,known_sha,out,kind in resources:
    url=base+"/"+src
    data=fetch(url)
    blob_hash=hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
    if blob_hash!=known_sha:
        raise RuntimeError(f"Upstream file changed or corrupted: {src}: {blob_hash}")
    if kind=="font":
        if not font_sane(data):
            raise RuntimeError(f"Font SFNT header or table bounds failed: {src}")
    elif b"SIL OPEN FONT LICENSE Version 1.1" not in data:
        raise RuntimeError(f"Missing required OFL license: {src}")
    dest=root/out
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_bytes(data)
    print(f"Verified {src}: {len(data)} bytes; git blob={blob_hash}; sha256={hashlib.sha256(data).hexdigest()}")
print("All two complete Google Fonts TTF and two OFL license assets installed for offline APK use")
