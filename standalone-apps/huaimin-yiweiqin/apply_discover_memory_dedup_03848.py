#!/usr/bin/env python3
"""0.38.48: Remove only the duplicate Discover > 回忆 card, preserving Home 记忆 and stored data."""
from pathlib import Path
import sys

def apply(root: Path) -> None:
    java = root / "app/src/main/java/com/cleo/cleos"
    hub = java / "ui/HubScreens.kt"
    source = hub.read_text(encoding="utf-8")
    start = source.index("fun DiscoverTab(")
    end = source.index("\n@Composable", start)
    tab = source[start:end]
    target = 'item { DirectoryRow("回忆","TA 保存的长期记忆",Icons.Rounded.AutoStories,onMemory) }'
    rows = tab.splitlines(keepends=True)
    matches = [i for i, line in enumerate(rows) if target in line]
    if len(matches) != 1 or rows[matches[0]].strip() != target:
        raise RuntimeError("Expected one exact Discover memory row; refusing potentially destructive edit")
    rows.pop(matches[0])
    updated = source[:start] + "".join(rows) + source[end:]
    for preserved in ['DirectoryRow("钱包"', 'DirectoryRow("生活与成长"', 'DirectoryRow("日记"', 'DirectoryRow("待办"', 'DirectoryRow("收藏"']:
        if preserved not in updated[start:]:
            raise RuntimeError(f"Discover entry inadvertently lost: {preserved}")
    assert 'onMemory:()->Unit' in tab, "Keep original navigation signature for compatibility"
    hub.write_text(updated, encoding="utf-8")

    gradle = root / "app/build.gradle.kts"
    previous = gradle.read_text(encoding="utf-8")
    assert previous.count('versionName = "0.38.47"') == 1
    assert previous.count('versionCode = 62113') == 1
    changed = previous.replace('versionName = "0.38.47"', 'versionName = "0.38.48"', 1)
    changed = changed.replace('versionCode = 62113', 'versionCode = 62114', 1)
    gradle.write_text(changed, encoding="utf-8")
    assert 'DirectoryRow("回忆"' not in updated[start:end]
    print("0.38.48 Build 62114: removed Discover 回忆 entry only; Home 记忆 and all persisted data untouched")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: apply_discover_memory_dedup_03848.py ANDROID_PROJECT_ROOT")
    apply(Path(sys.argv[1]).resolve())
