#!/usr/bin/env python3
from pathlib import Path
import sys
r=Path(sys.argv[1])
b=r/"app/src/main/java/com/cleo/cleos"
get=lambda p:(b/p).read_text(encoding="utf-8")
qq=get("ai/QqSearchPlan.kt")
plan=get("ai/KugouSearchPlan.kt")
service=get("KugouMusicAccessibilityService.kt")
xml=(r/"app/src/main/res/xml/kugou_music_accessibility.xml").read_text()
grad=(r/"app/build.gradle.kts").read_text()
tests=(r/"app/src/test/java/com/cleo/cleos/ai/KugouGestureFallbackTest.kt").read_text()
assert 'versionName = "0.38.23"' in grad and 'versionCode = 62089' in grad
assert 'val centerX: Int = -1, val centerY: Int = -1' in qq
for x in ['val navY', 'val videoY', 'centerX > 0', 'tab?.id', 'title.centerX > 0',
          'val singerLink', 'if (artistCard || comprehensiveSelected) return QqUiAction.Wait']:
    assert x in plan,x
for x in ['import android.graphics.Path', 'import android.accessibilityservice.GestureDescription',
          'bounds.centerX(), bounds.centerY()', 'dispatchGesture(gesture, null, null)',
          'action.purpose in setOf("songs", "song")', 'AppNames.key(song.title)',
          'val safeBounds', 'validLabel']:
    assert x in service,x
assert 'android:canPerformGestures="true"' in xml
assert 'android:packageNames="com.kugou.android"' in xml
assert 'com.tencent.qqmusic' not in xml
assert tests.count('@Test') >= 4
assert 'unclickableButLocatedSongTabIsTapped' in tests
print("PASS 0.38.23: bounded clickable/gesture fallback + exact title, in Kugou only")
