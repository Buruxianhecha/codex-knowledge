#!/usr/bin/env python3
"""0.38.35: robust Android location acquisition and honest map fallback."""
from pathlib import Path
from shutil import copyfile
import sys
root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
source = root/"app/src/main/java/com/cleo/cleos/ui/chat/ChatLocation.kt"
target = root/"app/src/test/java/com/cleo/cleos/ui/chat/LocationQualityTest.kt"
target.parent.mkdir(parents=True,exist_ok=True)
assert source.exists(), "0.38.31 location module must exist before this fix"
copyfile(here/"ChatLocation_03835.kt",source)
copyfile(here/"LocationQualityTest_03835.kt",target)
gradle = root/"app/build.gradle.kts"
text = gradle.read_text(encoding="utf-8")
for old,new in [
    ('versionName = "0.38.34"','versionName = "0.38.35"'),
    ('versionCode = 62100','versionCode = 62101'),
]:
    if text.count(old)!=1: raise RuntimeError("unexpected version in base: "+old)
    text=text.replace(old,new,1)
gradle.write_text(text,encoding="utf-8")
print("0.38.35/62101: Android network/GPS race, 7-second bound, manual map fallback, async geocoder")
