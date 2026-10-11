#!/usr/bin/env python3
"""0.38.21 verify full Kugou-only dispatch and separate permission."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base=root/"app/src/main/java/com/cleo/cleos"
read=lambda p: (base/p).read_text(encoding="utf-8")
tool=read("ai/Tools.kt")
phone=read("ai/PhoneAppActions.kt")
android=read("ai/AndroidPhoneApps.kt")
prompt=read("ai/Prompt.kt")
shared=read("ui/settings/SharedPages.kt")
kugou=read("ai/KugouPlayback.kt")
plan=read("ai/KugouSearchPlan.kt")
service=read("KugouMusicAccessibilityService.kt")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
xml=(root/"app/src/main/res/xml/kugou_music_accessibility.xml").read_text(encoding="utf-8")
version=(root/"app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionName = "0.38.21"' in version and 'versionCode = 62087' in version
assert 'name = "play_kugou_music"' in tool
assert 'groups = setOf(ToolGroup.Apps)' in tool
assert 'ToolSpecs.playKugouMusic.name ->' in tool
assert 'phoneApps' in tool and '.playKugou(' in tool
assert 'playQqMusic,' in tool and 'playKugouMusic,' in tool
assert '"酷狗音乐" to listOf("com.kugou.android")' in phone
assert '"酷狗" to listOf("com.kugou.android")' in phone
assert 'suspend fun playKugou(' in phone
assert 'kugouPlayer.play(SongRequest.of(title, artist))' in android
assert 'AppNames.find("酷狗音乐", launcherApps().filter { it.packageName == "com.kugou.android" })' in android
assert 'KugouMusicAccessibilityService.connected()' in android
assert 'KugouMusicAccessibilityService.select(app.packageName, song)' in android
assert 'begin(app, song)' in android and 'song.query' in android
assert 'installedKugou()' in kugou and 'setOf("com.kugou.android")' in kugou
assert 'song.matches(it, app.packageName)' in kugou
assert 'com.kugou.android' in plan and 'if (packageName != player' in plan
assert 'if (player !in setOf("com.kugou.android"))' in service
assert '"com.kugou.android"' in xml and 'com.tencent.qqmusic' not in xml
assert 'class KugouMusicAccessibilityService' in service
assert 'android:name=".KugouMusicAccessibilityService"' in manifest
assert 'android:resource="@xml/kugou_music_accessibility"' in manifest
assert 'Chip("酷狗音乐点歌权限", selected = kugouAllowed)' in shared
assert 'QqMusicAccessibilityService.accessIntent(context)' in shared
assert 'KugouMusicAccessibilityService.accessIntent(context)' in shared
assert 'val kugouAllowed by remember' in shared or 'var kugouAllowed by remember' in shared
assert '打开酷狗播放小半' in prompt
assert '必须用 play_kugou_music' in prompt
assert '不能调用 play_qq_music' in prompt
assert '没有明确指定播放器时，用 request_song' in prompt
assert (root/"app/src/test/java/com/cleo/cleos/ai/KugouPlaybackTest.kt").is_file()
assert (root/"app/src/test/java/com/cleo/cleos/ai/KugouSearchPlanTest.kt").is_file()
print("PASS: v0.38.21 dedicated Kugou accessibility grant, QQ-safe routing and media-state confirmation")
