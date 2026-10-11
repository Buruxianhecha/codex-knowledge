#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
j=root/"app/src/main/java/com/cleo/cleos"
for src,dest in [
    ("QqFinalPlayRules_03828.kt",j/"ai/QqFinalPlayRules.kt"),
    ("QqFinalPlayRulesTest_03828.kt",root/"app/src/test/java/com/cleo/cleos/ai/QqFinalPlayRulesTest.kt"),
    ("QqMusicAccessibilityService_03828.kt",j/"QqMusicAccessibilityService.kt"),
]:
    assert (here/src).read_bytes()==dest.read_bytes(),dest
a=(j/"ai/AndroidPhoneApps.kt").read_text()
assert "resumePaused = { music.resumeExact(app.packageName, song) }" in a
assert "KugouMusicAccessibilityService" in a
m=(j/"ai/Music.kt").read_text()
assert "fun resumeExact(player: String, song: SongRequest): Boolean" in m
assert "controller.transportControls.play()" in m
q=(j/"QqMusicAccessibilityService.kt").read_text()
assert "while (true)" in q and "QqFinalPlayRules.choose" in q
assert "playTriggered = true" in q
assert '"selected" -> null' in q
xml=(root/"app/src/main/res/xml/qq_music_accessibility.xml").read_text()
assert 'android:canPerformGestures="true"' in xml
assert 'android:packageNames="com.tencent.qqmusic,com.tencent.qqmusiclite,com.tencent.qqmusicpad"' in xml
g=(root/"app/build.gradle.kts").read_text()
assert 'versionName = "0.38.28"' in g and 'versionCode = 62094' in g
b=(j/"data/BackupService.kt").read_text()
assert "BackupArchiveGuard.cleanEmptyOrphanConversations" in b
print("PASS: QQ final play and media confirmation, package isolation, backup fix retained")
