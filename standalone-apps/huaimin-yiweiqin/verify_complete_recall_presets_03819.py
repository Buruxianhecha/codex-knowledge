#!/usr/bin/env python3
"""0.38.19: verify each reference item, UI labels and old recall behavior."""
from pathlib import Path
import sys
root = Path(sys.argv[1]).resolve()
p = root / "app/src/main/java/com/cleo/cleos"
catalog = (p / "data/RecallDisplayText.kt").read_text(encoding="utf-8")
view = (p / "ui/settings/RecallMessageSettings.kt").read_text(encoding="utf-8")
prefs = (p / "data/SettingsRepository.kt").read_text(encoding="utf-8")
recall = (p / "data/Recalls.kt").read_text(encoding="utf-8")
repo = (p / "ai/ChatRepository.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/com/cleo/cleos/data/RecallDisplayTextTest.kt").read_text(encoding="utf-8")
assert 'versionName = "0.38.19"' in gradle and "versionCode = 62085" in gradle
suffixes = [
    "因为有错别字。", "并坏笑了一下。", "你猜猜撤回了什么。",
    "并祝你新春快乐 🐮", "你品，你细品~", "并抱了你一下 ❤️",
    "保持高贵的沉默。", "试图萌混过关 😉", "并假装无事发生。",
    "并对你施了遗忘咒。", "就是辣么神秘 😎", "并祝你节日快乐",
]
assert catalog.count('"$DEFAULT，') == len(suffixes)
assert '\n        DEFAULT,\n' in catalog
for s in suffixes:
    assert '"$DEFAULT，' + s + '"' in catalog, s
assert 'assertEquals(13, RecallDisplayText.presets.size)' in tests
assert '.ifEmpty { "无" }' in view and '无（默认）' not in view
assert 'RecallDisplayText.presets.forEach' in view
assert 'vm.setRecallDisplayText(option)' in view
assert 'vm.setRecallDisplayText(draft)' in view
assert 'recallDisplayText = RecallDisplayText.clean(' in prefs
assert 'Recalls.withdrawn(current, recallNotice)' in repo
assert 'Recalls.event(current, at)' in repo
assert 'note = RecallDisplayText.clean(displayText)' in recall
print("PASS v0.38.19: all 13 presets exactly present, default label correct, custom entry and real recall retained")
