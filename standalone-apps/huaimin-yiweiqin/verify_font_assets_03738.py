#!/usr/bin/env python3
"""Fail release packaging if either real offline TTF or mandatory OFL license is missing."""
from pathlib import Path
import hashlib
import sys
from zipfile import ZipFile

apk=Path(sys.argv[1]).resolve()
need={
  "assets/display_fonts/LongCang-Regular.ttf":"363bb08696fe4d3827bd36d3d2fbc028d1205161",
  "assets/display_fonts/ZhiMangXing-Regular.ttf":"d037d1f00395175de01de33e62957c30a008a1b1",
  "assets/font_licenses/LongCang-OFL.txt":"06a1757ca2ae5ff689aac4b410fffa98cd73dad0",
  "assets/font_licenses/ZhiMangXing-OFL.txt":"8e1e6c70cd7fad5b34f6337c694fed96b5e38884",
}
with ZipFile(apk) as z:
    names=set(z.namelist())
    for name,expected in need.items():
        if name not in names: raise SystemExit(f"Missing offline font or OFL copyright asset: {name}")
        data=z.read(name)
        sha=hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
        if sha!=expected:
            raise SystemExit(f"Bundled font/license integrity failed: {name}: {sha}")
        if name.endswith(".ttf") and (len(data)<2_000_000 or data[:4]!=b"\x00\x01\x00\x00"):
            raise SystemExit(f"Bundled font invalid or placeholder: {name}")
        if name.endswith(".txt") and b"SIL OPEN FONT LICENSE Version 1.1" not in data:
            raise SystemExit(f"Bundled license invalid: {name}")
        print(f"Verified APK {name}: {len(data)} bytes, exact upstream git blob={sha}")
print("Both complete original TTFs and both licenses are physically bundled inside the release APK.")
