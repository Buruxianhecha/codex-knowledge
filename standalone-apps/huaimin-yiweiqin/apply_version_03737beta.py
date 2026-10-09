#!/usr/bin/env python3
"""Keep 0.37.37 stable; number the next multi-key + custom-font candidate 0.37.37beta."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
gradle = root / "app/build.gradle.kts"
text = gradle.read_text(encoding="utf-8")
for before, after in (
    ('versionName = "0.37.37"', 'versionName = "0.37.37beta"'),
    ('versionCode = 62059', 'versionCode = 62060'),
):
    hits = text.count(before)
    if hits != 1:
        raise RuntimeError(f"Beta version anchor mismatch: {before!r} (found {hits})")
    text = text.replace(before, after, 1)
gradle.write_text(text, encoding="utf-8")
print("Version: 0.37.37beta (62060) — signing/publication deferred")
