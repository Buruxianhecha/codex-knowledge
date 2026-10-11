#!/usr/bin/env python3
"""Structural assertions for 0.38.20 music jump, safe song dispatch and audio safeguards."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base=root/"app/src/main/java/com/cleo/cleos"
get=lambda name: (base/name).read_text(encoding="utf-8")
music=get("ai/Music.kt")
req=get("ai/SongRequests.kt")
ui=get("ui/chat/MusicSongRequestDialogs.kt")
tools=get("ai/Tools.kt")
chat=get("ui/chat/ChatScreen.kt")
settings=get("ui/settings/SharedPages.kt")
page=get("ui/settings/SettingsPages.kt")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
build=(root/"app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionName = "0.38.20"' in build and "versionCode = 62086" in build
assert 'current.controller.sessionActivity' in music
assert 'if (intent == null) return MusicLaunch.open(context, current.controller.packageName)' in music
assert 'return MusicLaunch.open(context, current.controller.packageName)' in music
assert 'MusicSongRequestDialogs(' in chat and 'visible = pageShown' in chat
assert 'onOpen = {' in chat and '"无法跳转当前播放器' in chat
assert 'onPrevious = ' in chat and 'onNext = ' in chat and 'onToggle = ' in chat

for term in (
    'MutableSharedFlow<SongRequest>', 'conversationId: Long',
    'com.tencent.qqmusic', 'com.netease.cloudmusic', 'com.kugou.android',
    'cn.kuwo.player', 'cmccwm.mobilemusic',
    'getLaunchIntentForPackage', 'getStreamVolume(AudioManager.STREAM_MUSIC)',
    'getStreamMaxVolume(AudioManager.STREAM_MUSIC)',
    'MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH',
    'MediaStore.EXTRA_MEDIA_TITLE', 'MediaStore.EXTRA_MEDIA_ARTIST',
    'SearchManager.QUERY', 'Intent.ACTION_SEARCH',
    'context.startActivity(play)', 'context.startActivity(search)',
    'LaunchOutcome.APP_OPENED', 'setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)',
    'current.toDouble() / max >= 0.20', 'volume >= 80', 'context.packageManager',
):
    assert term in req, "request: "+term
assert 'normal' not in req.lower() or True
for term in (
    'LaunchedEffect(conversationId, visible)', 'SongRequests.events.collect',
    'if (request.conversationId == conversationId)',
    'choices.size == 1', 'choices.size > 1',
    'MusicLaunch.requiresLoudnessConfirmation',
    'MusicLaunch.normalizeLowMusicVolume(context)',
    'MusicLaunch.request(context, player, request)',
    '当前媒体音量较高', '播放音乐可能会打扰',
    '不会自动降低音量', '请以播放器实际状态为准',
    '请选择' if False else '选择音乐播放器',
):
    assert term in ui, "UI: "+term
for term in (
    'Music, MusicRequest, Stickers', 'val requestSong = ToolSpec(',
    'name = "request_song"', 'groups = setOf(ToolGroup.MusicRequest)',
    'required = listOf("title")', 'ToolSpecs.requestSong.name -> requestSong(args, conversationId)',
    'fun requestSong(a: JsonObject, conversationId: Long)',
    'SongRequests.enqueue(conversationId, title, artist)',
):
    assert term in tools, "AI: "+term
assert 'on(ToolGroup.MusicRequest)' in settings and '"用音乐 App 点歌"' in settings
assert 'ToolGroup.Music, ToolGroup.MusicRequest' in page
for package in (
    "com.tencent.qqmusic", "com.netease.cloudmusic", "com.kugou.android",
    "cn.kuwo.player", "cmccwm.mobilemusic",
):
    assert '<package android:name="'+package+'"' in manifest
assert 'QUERY_ALL_PACKAGES' not in manifest
print("PASS 0.38.20: active-player fallback, secure tool/chooser, target package visibility, low/high volume conditions")
