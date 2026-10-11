#!/usr/bin/env python3
"""0.38.36: keep native map thumbnails strictly inside chat location bubbles."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
source = root / "app/src/main/java/com/cleo/cleos/ui/chat/ChatLocation.kt"
gradle = root / "app/build.gradle.kts"
assert source.is_file(), "0.38.35 location module is missing"
assert 'internal fun ChatLocationCard(' in source.read_text(encoding="utf-8")
copyfile(here / "ChatLocation_03836.kt", source)
text = gradle.read_text(encoding="utf-8")
for old, new in [
    ('versionName = "0.38.35"', 'versionName = "0.38.36"'),
    ('versionCode = 62101', 'versionCode = 62102'),
]:
    if text.count(old) != 1:
        raise RuntimeError(f"base version precondition failed: {old}")
    text = text.replace(old, new, 1)
gradle.write_text(text, encoding="utf-8")
print("0.38.36/62102: AndroidView host clipping, bounded location bubble, small static pin")
