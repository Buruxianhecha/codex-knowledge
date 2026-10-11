#!/usr/bin/env python3
"""0.38.19 small incremental patch: complete reference preset catalog and keep existing persisted setting."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
data = root / "app/src/main/java/com/cleo/cleos/data/RecallDisplayText.kt"
settings = root / "app/src/main/java/com/cleo/cleos/ui/settings/RecallMessageSettings.kt"
gradle = root / "app/build.gradle.kts"
old = data.read_text(encoding="utf-8")
if 'object RecallDisplayText' not in old or 'MAX_LENGTH = 80' not in old:
    raise RuntimeError("expected existing 0.38.18 custom recall catalog")
copyfile(here / "RecallDisplayText_03819.kt", data)

screen = settings.read_text(encoding="utf-8")
before = '.ifEmpty { "无（默认）" }'
after = '.ifEmpty { "无" }'
if screen.count(before) != 1:
    raise RuntimeError("refusing to overwrite unknown recall settings UI")
screen = screen.replace(before, after, 1)
settings.write_text(screen, encoding="utf-8")

test = root / "app/src/test/java/com/cleo/cleos/data/RecallDisplayTextTest.kt"
copyfile(here / "RecallDisplayTextTest_03819.kt", test)

version = gradle.read_text(encoding="utf-8")
for current, next_version in [
    ('versionName = "0.38.18"', 'versionName = "0.38.19"'),
    ("versionCode = 62084", "versionCode = 62085"),
]:
    if version.count(current) != 1:
        raise RuntimeError("wrong predecessor version: " + current)
    version = version.replace(current, next_version, 1)
gradle.write_text(version, encoding="utf-8")
print("0.38.19 / 62085: thirteen reference recall presets; existing selection, custom input and shared persistence untouched.")
