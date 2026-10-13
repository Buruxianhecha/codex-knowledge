#!/usr/bin/env python3
"""Install current-version screens and minimally extend the existing Discover directory."""
from pathlib import Path
import re
import shutil
import sys


def apply(project: Path) -> None:
    source = Path(__file__).resolve().parent
    java = project / "app/src/main/java/com/cleo/cleos"
    for artifact, target in (
        ("LifeScreen_03847.kt", "ui/LifeScreen.kt"),
        ("MainScreen_03847.kt", "ui/MainScreen.kt"),
        ("ChatScreen_03847.kt", "ui/chat/ChatScreen.kt"),
    ):
        dest = java / target
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source / artifact, dest)

    hub = java / "ui/HubScreens.kt"
    text = hub.read_text(encoding="utf-8")
    start = text.index("fun DiscoverTab(")
    body_start = text.index("{", start)
    # The next composable belongs to DirectoryRow; never replace the historical file.
    end = text.index("\n@Composable", body_start)
    block = text[start:end]
    if "onLife" not in block[:block.index("{")]:
        block, count = re.subn(r"(onWallet\s*:\s*\(\s*\)\s*->\s*Unit)",
            r"\1,onLife:()->Unit", block, count=1)
        if count != 1:
            raise RuntimeError("DiscoverTab wallet callback anchor missing; refusing to overwrite HubScreens")
    if 'DirectoryRow("生活与成长"' not in block:
        lines = block.splitlines(keepends=True)
        wallet_rows = [i for i, line in enumerate(lines) if "DirectoryRow" in line and "onWallet" in line]
        if len(wallet_rows) != 1:
            raise RuntimeError("DiscoverTab wallet row anchor is not unique")
        i = wallet_rows[0]
        indent = re.match(r"\s*", lines[i]).group(0)
        lines.insert(i + 1, indent + 'item { DirectoryRow("生活与成长","工作、休息、工资、经验与读书学习",Icons.Rounded.AutoStories,onLife) }\n')
        block = "".join(lines)
    hub.write_text(text[:start] + block + text[end:], encoding="utf-8")
    print("0.38.47 UI ready: Discover → 生活与成长; existing Hub preserved")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: ui_entry_03847.py ANDROID_PROJECT_ROOT")
    apply(Path(sys.argv[1]))
