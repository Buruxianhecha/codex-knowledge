#!/usr/bin/env python3
"""0.38.16: swap 0.38.15's dark grid for reference-style frosted glass 2-column tiles."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

menu = root / "app/src/main/java/com/cleo/cleos/ui/chat/WechatMessageMenu.kt"
old = menu.read_text(encoding="utf-8")
if "internal const val MESSAGE_MENU_COLUMNS = 3" not in old or "internal fun WechatMessagePopup(" not in old:
    raise RuntimeError("Missing 0.38.15 predecessor; refused to replace an unknown message menu")
copyfile(here / "WechatMessageMenu_03816.kt", menu)

version_file = root / "app/build.gradle.kts"
version = version_file.read_text(encoding="utf-8")
for before, after in [
    ('versionName = "0.38.15"', 'versionName = "0.38.16"'),
    ("versionCode = 62081", "versionCode = 62082"),
]:
    if version.count(before) != 1:
        raise RuntimeError("Unexpected app build version: " + before)
    version = version.replace(before, after, 1)
version_file.write_text(version, encoding="utf-8")
print("0.38.16 / 62082: blue translucent frosted-glass menu, 2 columns, independent glass tiles and warm-red Delete")
