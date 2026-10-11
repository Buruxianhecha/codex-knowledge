#!/usr/bin/env python3
"""0.38.24: foreground IME recovery, confirmed tab transition and bounded taps."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
java = root / "app/src/main/java/com/cleo/cleos"
for source, target in [
    ("QqSearchPlan_03824.kt", java / "ai/QqSearchPlan.kt"),
    ("KugouSearchPlan_03824.kt", java / "ai/KugouSearchPlan.kt"),
    ("KugouRuntimeRules_03824.kt", java / "ai/KugouRuntimeRules.kt"),
    ("KugouMusicAccessibilityService_03824.kt", java / "KugouMusicAccessibilityService.kt"),
]:
    copyfile(here / source, target)
tests = root / "app/src/test/java/com/cleo/cleos/ai"
for name in ("KugouRecoveryTest", "KugouRuntimeRulesTest"):
    copyfile(here / (name + "_03824.kt"), tests / (name + ".kt"))
xml = root / "app/src/main/res/xml/kugou_music_accessibility.xml"
s = xml.read_text(encoding="utf-8")
assert s.count('android:accessibilityFlags="flagReportViewIds"') == 1
xml.write_text(s.replace('android:accessibilityFlags="flagReportViewIds"',
    'android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"', 1), encoding="utf-8")
android = java / "ai/AndroidPhoneApps.kt"
s = android.read_text(encoding="utf-8")
old = "KugouMusicAccessibilityService.select(app.packageName, song)"
assert s.count(old) == 1
android.write_text(s.replace(old, old + " { song.matches(music.currentFor(app.packageName), app.packageName) }", 1), encoding="utf-8")
gradle = root / "app/build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
for old, new in [('versionName = "0.38.23"', 'versionName = "0.38.24"'), ('versionCode = 62089', 'versionCode = 62090')]:
    assert s.count(old) == 1, old
    s = s.replace(old, new, 1)
gradle.write_text(s, encoding="utf-8")
print("0.38.24/62090: close observed IME, confirm single-song page, bounded semantic/gesture recovery")
