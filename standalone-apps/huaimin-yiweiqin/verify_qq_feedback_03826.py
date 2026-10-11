#!/usr/bin/env python3
"""Audit generated app, not source templates, after 0.38.26 applies."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
java=root/"app/src/main/java/com/cleo/cleos"
for name,dest in [
    ("PhoneAppActions_03826.kt",java/"ai/PhoneAppActions.kt"),
    ("QqMusicAccessibilityService_03826.kt",java/"QqMusicAccessibilityService.kt"),
    ("QqWindowRules_03826.kt",java/"ai/QqWindowRules.kt"),
    ("PhoneAppsTest_03826.kt",root/"app/src/test/java/com/cleo/cleos/ai/PhoneAppsTest.kt"),
    ("QqWindowRulesTest_03826.kt",root/"app/src/test/java/com/cleo/cleos/ai/QqWindowRulesTest.kt")
]:
    assert (here/name).read_bytes() == dest.read_bytes(),dest
a=(java/"ai/AndroidPhoneApps.kt").read_text(encoding="utf-8")
assert "QqMusicAccessibilityService.enabled(context)" in a
assert "song.matches(music.currentFor(app.packageName), app.packageName)" in a
assert "KugouMusicAccessibilityService.select" in a
assert "KugouPlayback" in a
q=(java/"ai/PhoneAppActions.kt").read_text(encoding="utf-8")
assert "suspend fun playKugou" in q
assert '"酷狗" to listOf("com.kugou.android")' in q
assert 'if (selectionError != null)' in q
assert 'confirmed(app, song, if (selectionError == null)' in q
x=(root/"app/src/main/res/xml/qq_music_accessibility.xml").read_text(encoding="utf-8")
assert 'flagRetrieveInteractiveWindows' in x and 'packageNames="com.tencent.qqmusic,com.tencent.qqmusiclite,com.tencent.qqmusicpad"' in x
v=(root/"app/build.gradle.kts").read_text()
assert 'versionName = "0.38.26"' in v and 'versionCode = 62092' in v
p=(java/"ai/Prompt.kt").read_text(encoding="utf-8")
assert "选歌超时或不在前台并不表示 QQ 音乐点歌权限未开启" in p
print("PASS: 0.38.26 QQ error reporting, music session, player separation and previous assets intact")
