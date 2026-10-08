#!/usr/bin/env python3
"""0.37.25: monotonic identity bump ONLY, no Room schema changes or data deletion."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
gradle = root / "app/build.gradle.kts"
content = gradle.read_text(encoding="utf-8")
changes = [
    ('applicationId = "com.lin.huaimin"', 'applicationId = "com.lin.huaimin"'),
    ('versionName = "0.37.24"', 'versionName = "0.37.25"'),
    ('versionCode = 62046', 'versionCode = 62047'),
]
for before, after in changes:
    found = content.count(before)
    if found != 1:
        raise SystemExit(f"Expected exactly one {before!r}; found {found}")
    content = content.replace(before, after)
gradle.write_text(content, encoding="utf-8")
print("0.37.25 / 62047: same package ID, same Room schema, upgrade-safe code bump")
