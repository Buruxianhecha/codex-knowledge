#!/usr/bin/env python3
"""0.38.20: open active player; AI-triggered foreground song requests, player chooser and volume safeguards."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

def edit(rel, old, new, label):
    file = root / rel
    text = file.read_text(encoding="utf-8")
    n = text.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {n} in {rel}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")

rel = "app/src/main/java/com/cleo/cleos/ai/Music.kt"
edit(rel,
'''    fun open(): Boolean {
        val intent = pick(seen())?.controller?.sessionActivity ?: return false
''',
'''    fun open(): Boolean {
        val current = pick(seen()) ?: return false
        val intent = current.controller.sessionActivity
        // Some QQ/other player versions expose no session activity; use the
        // actual session package to open its launcher as a second route.
        if (intent == null) return MusicLaunch.open(context, current.controller.packageName)
''',
"media session fallback if activity absent")
edit(rel,
'''        return runCatching { intent.send(context, 0, null, null, null, null, options) }.isSuccess
''',
'''        if (runCatching { intent.send(context, 0, null, null, null, null, options) }.isSuccess) return true
        return MusicLaunch.open(context, current.controller.packageName)
''',
"media session fallback on pending intent failure")

tools = "app/src/main/java/com/cleo/cleos/ai/Tools.kt"
edit(tools,
    'Calendar, Music, Stickers }',
    'Calendar, Music, MusicRequest, Stickers }',
    "new separately controlled AI app-launch ability")
edit(tools,
'''    val all = listOf(
        sendMessage,
''',
'''    val requestSong = ToolSpec(
        name = "request_song",
        groups = setOf(ToolGroup.MusicRequest),
        action = "点歌",
        description = "对方明确说想听某首歌时用。提交歌曲名称与可选歌手，由手机上的 QQ 音乐、网易云、酷狗等支持的音乐 App 尝试搜索并播放。" +
            "如果安装多个播放器，会先让对方选一个；高音量要让对方确认。只表示发起请求，不表示播放一定成功。" +
            "仅在对方主动要求播放歌曲时调用，不可自主启动音乐。",
        parameters = schema(
            required = listOf("title"),
            "title" to prop("string", "要听的歌曲名称，必须从对方的要求提取"),
            "artist" to prop("string", "歌手，可为空"),
        ),
    )

    val all = listOf(
        sendMessage,
''',
"AI play song tool declaration")
edit(tools,
'''        deleteEvent,
        musicControl,
''',
'''        deleteEvent,
        musicControl,
        requestSong,
''',
"register AI song tool")
edit(tools,
'''                ToolSpecs.musicControl.name -> musicControl(args)
                else -> getWeather(args, settings)
''',
'''                ToolSpecs.musicControl.name -> musicControl(args)
                ToolSpecs.requestSong.name -> requestSong(args, conversationId)
                else -> getWeather(args, settings)
''',
"tool dispatch")
edit(tools,
'''    private suspend fun musicControl(a: JsonObject): ToolOutcome {
''',
'''    private fun requestSong(a: JsonObject, conversationId: Long): ToolOutcome {
        val title = ToolArgs.text(a, "title")?.trim().orEmpty()
        val artist = ToolArgs.text(a, "artist")?.trim().orEmpty()
        if (title.isBlank()) throw ToolFailure("要先说清楚想听哪首歌。", "缺少歌名")
        if (!SongRequests.enqueue(conversationId, title, artist)) {
            throw ToolFailure("目前没有前台聊天窗口接收点歌请求，请进入会话后再试。", "点歌未送达")
        }
        return ToolOutcome(
            "已发起《" + title + "》点歌操作，手机将选择可用音乐 App、检查媒体音量。" +
                "Android 与第三方播放器可能只支持搜索；请不要声称已经成功播放，除非媒体会话实际显示在播放。",
            "已请求播放《" + title + "》",
        )
    }

    private suspend fun musicControl(a: JsonObject): ToolOutcome {
''',
"music song request executor")

shared = "app/src/main/java/com/cleo/cleos/ui/settings/SharedPages.kt"
edit(shared,
'''        ExplainedSwitch("换自己的头像", "用你发来的图或表情换头像", null, on(ToolGroup.Avatar)) { vm.setTool(ToolGroup.Avatar, it) }
''',
'''        ExplainedSwitch(
            "用音乐 App 点歌",
            "聊天里说歌名，自动尝试在播放器搜索并播放",
            "这是独立于「一起听歌」的授权开关：打开后，你主动要求听歌时，TA 才能启动已安装的音乐 App。" +
                "多个播放器先询问你选哪个。媒体音量低于20%自动调到20%，达到80%会先确认。" +
                "使用 Android 标准播放搜索接口；QQ 音乐等第三方 App 是否能直接播放由它们自己决定。" +
                "只跳到搜索或首页时，需要你在音乐 App 里手动选择歌曲。该功能不需要读取其他应用通知。",
            on(ToolGroup.MusicRequest),
        ) { vm.setTool(ToolGroup.MusicRequest, it) }
        RowDivider(inset = 0.dp)
        ExplainedSwitch("换自己的头像", "用你发来的图或表情换头像", null, on(ToolGroup.Avatar)) { vm.setTool(ToolGroup.Avatar, it) }
''',
"song permission switch")
pages = "app/src/main/java/com/cleo/cleos/ui/settings/SettingsPages.kt"
edit(pages,
    'ToolGroup.Calendar, ToolGroup.Music, ToolGroup.Avatar,',
    'ToolGroup.Calendar, ToolGroup.Music, ToolGroup.MusicRequest, ToolGroup.Avatar,',
    "ability switch totals")

manifest="app/src/main/AndroidManifest.xml"
edit(manifest,
'''    <application
''',
'''    <!-- Only declared music packages are queried. No broad package enumeration. -->
    <queries>
        <package android:name="com.tencent.qqmusic" />
        <package android:name="com.netease.cloudmusic" />
        <package android:name="com.kugou.android" />
        <package android:name="cn.kuwo.player" />
        <package android:name="cmccwm.mobilemusic" />
        <intent><action android:name="android.media.action.MEDIA_PLAY_FROM_SEARCH" /></intent>
        <intent><action android:name="android.intent.action.SEARCH" /></intent>
    </queries>

    <application
''',
"Android 11+ targeted package visibility")

chat="app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
edit(chat,
'''    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
''',
'''    MusicSongRequestDialogs(
        conversationId = state.conversationId,
        visible = pageShown,
        onStatus = { voiceHint = it },
    )
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
''',
"foreground picker integrated into ChatTab")
edit(chat,
'''                        onOpen = { if (!c.music.open()) voiceHint = "打不开这个播放器" },
''',
'''                        onOpen = {
                            if (!c.music.open()) voiceHint =
                                "无法跳转当前播放器，请确认音乐 App 仍在运行且已安装"
                        },
''',
"player open feedback")

base=root/"app/src/main/java/com/cleo/cleos"
copyfile(here/"SongRequests_03820.kt",base/"ai/SongRequests.kt")
copyfile(here/"MusicSongRequestDialogs_03820.kt",base/"ui/chat/MusicSongRequestDialogs.kt")

gradle="app/build.gradle.kts"
edit(gradle,'versionName = "0.38.19"','versionName = "0.38.20"',"release name")
edit(gradle,"versionCode = 62085","versionCode = 62086","release code")
print("0.38.20 / 62086: launcher fallback, AI song request permission, chooser, safe volume and standardized search/play")
