#!/usr/bin/env python3
"""Check generated integration; behavioral coverage belongs to the Kotlin tests."""
from pathlib import Path
import sys
r = Path(sys.argv[1])
j = r / "app/src/main/java/com/cleo/cleos"
for source, destination in [
    ("QqSearchPlan_03824.kt", "ai/QqSearchPlan.kt"),
    ("KugouSearchPlan_03824.kt", "ai/KugouSearchPlan.kt"),
    ("KugouRuntimeRules_03824.kt", "ai/KugouRuntimeRules.kt"),
    ("KugouMusicAccessibilityService_03824.kt", "KugouMusicAccessibilityService.kt"),
]:
    assert (j / destination).read_bytes() == (Path(__file__).parent / source).read_bytes(), destination
android = (j / "ai/AndroidPhoneApps.kt").read_text()
assert "song.matches(music.currentFor(app.packageName), app.packageName)" in android
xml = (r / "app/src/main/res/xml/kugou_music_accessibility.xml").read_text()
assert 'android:packageNames="com.kugou.android"' in xml
assert 'android:canPerformGestures="true"' in xml
assert "flagRetrieveInteractiveWindows" in xml
g = (r / "app/build.gradle.kts").read_text()
assert 'versionName = "0.38.24"' in g and "versionCode = 62090" in g
for test in ("KugouRecoveryTest", "KugouRuntimeRulesTest"):
    assert (r / ("app/src/test/java/com/cleo/cleos/ai/" + test + ".kt")).is_file()
print("PASS: 0.38.24 generated sources, IME windows, callback and real-media confirmation are connected")
