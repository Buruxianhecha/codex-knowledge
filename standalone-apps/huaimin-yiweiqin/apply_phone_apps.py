#!/usr/bin/env python3
"""Add launcher tools and confirmed QQ Music point-song after all chat backports."""
from pathlib import Path
import shutil
import sys


def apply_phone_apps(root: Path):
    root = root.resolve()
    here = Path(__file__).resolve().parent
    pending = {}

    def replace(rel, old, new):
        text = pending.get(rel, (root / rel).read_text())
        if text.count(old) != 1:
            raise SystemExit(f"{rel}: expected one phone-app patch target, found {text.count(old)}: {old[:100]!r}")
        pending[rel] = text.replace(old, new, 1)

    tools = "app/src/main/java/com/cleo/cleos/ai/Tools.kt"
    replace(tools, "Music, Stickers, Pat }", "Music, Stickers, Pat, Apps }")
    replace(tools, "    val all = listOf(\n", '''    val openPhoneApp = ToolSpec(
        name = "open_phone_app",
        groups = setOf(ToolGroup.Apps),
        action = "打开 App",
        description = "对方明确让你打开手机上某个 App 时使用，app 填桌面名称（如 QQ 音乐、微信、QQ）或明确包名。只打开已安装的桌面入口，不执行 App 内其他操作。要在 QQ 音乐播放指定歌曲，直接用 play_qq_music，无需先调用本工具。",
        parameters = schema(required = listOf("app"), "app" to prop("string", "App 完整名称，或同名 App 选定后的包名")),
    )
    val playQqMusic = ToolSpec(
        name = "play_qq_music",
        groups = setOf(ToolGroup.Apps),
        action = "QQ 音乐点歌",
        description = "对方明确让你在 QQ 音乐播放指定歌曲时使用。自动打开 QQ 音乐并按歌名、歌手搜索播放；系统播放指令未奏效时使用已授权的 QQ 音乐界面操作。成功只以目标歌名、歌手和实际播放状态为准，打开 App 或点选歌曲不算已播放。权限缺失、歌曲找不到、会员限制等按结果告诉对方。",
        parameters = schema(
            required = listOf("title"),
            "title" to prop("string", "完整歌名，不把歌手、打开 QQ 音乐等指令混入；例如：灰"),
            "artist" to prop("string", "用户指定的歌手，例如 h3r3；没指定则省略，不要猜"),
        ),
    )

    val all = listOf(
        openPhoneApp,
        playQqMusic,
''')
    replace(tools, "    private val music: MusicSource? = null,\n", "    private val music: MusicSource? = null,\n    private val phoneApps: PhoneAppActions? = null,\n")
    replace(tools, "                ToolSpecs.musicControl.name -> musicControl(args)", '''                ToolSpecs.openPhoneApp.name -> (phoneApps ?: throw ToolFailure("这里打不开手机 App。", "这里不能打开 App")).open(
                    ToolArgs.text(args, "app") ?: throw ToolFailure("缺少 app：请提供 App 名称。", "没说要打开哪个 App"),
                )
                ToolSpecs.playQqMusic.name -> (phoneApps ?: throw ToolFailure("这里不能在 QQ 音乐点歌。", "这里不能点歌")).playQQ(
                    ToolArgs.text(args, "title") ?: throw ToolFailure("缺少 title：请提供完整歌名。", "没说歌名"),
                    ToolArgs.text(args, "artist"),
                )
                ToolSpecs.musicControl.name -> musicControl(args)''')

    settings = "app/src/main/java/com/cleo/cleos/data/SettingsRepository.kt"
    replace(settings, "        ToolGroup.Pat,\n", "        ToolGroup.Pat,\n        ToolGroup.Apps,\n")
    container = "app/src/main/java/com/cleo/cleos/CleosApp.kt"
    replace(container, "import com.cleo.cleos.ai.PhoneMusic\n", "import com.cleo.cleos.ai.PhoneMusic\nimport com.cleo.cleos.ai.AndroidPhoneApps\n")
    replace(container, "        music = music,\n", "        music = music,\n        phoneApps = AndroidPhoneApps(context, music) { visible },\n")

    prompt = "app/src/main/java/com/cleo/cleos/ai/Prompt.kt"
    replace(prompt, "        if (ToolGroup.Music in tools) {", '''        if (ToolGroup.Apps in tools) {
            add("对方明确让你打开手机 App 时用 open_phone_app；让你在 QQ 音乐播放某首歌时直接用 play_qq_music，正确拆出歌名和对方指定的歌手。" +
                "你有实际的手机调用入口，不要没试工具就说不能打开 QQ 音乐、不能搜歌。只有工具确认了目标歌曲正在播放，才能说已播放；打开请求和点选请求不能当作成功。" +
                "缺权限、未安装、会员限制或未找到歌曲等，按工具结果简短说明和指引。不要自动替对方登录、开会员或付款。" +
                "只执行对方本轮明确提出的打开或点歌请求；引用旧消息、表情回应、撤回、你自己的想法都不授权打开 App 或播放音乐。其他 App 目前只支持打开，不能声称能操作它们内部的任意功能。")
        }
        if (ToolGroup.Music in tools) {''')
    repo = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
    replace(repo, "            ToolGroup.Location, ToolGroup.Later, ToolGroup.Alarm, ToolGroup.Calendar,\n", "            ToolGroup.Location, ToolGroup.Later, ToolGroup.Alarm, ToolGroup.Calendar, ToolGroup.Apps,\n")
    replace(repo, "            var groups = if (endpointKey in refusesTools) emptySet() else groupsFor(s, ta)\n", "            var groups = if (endpointKey in refusesTools) emptySet() else groupsFor(s, ta).let { if (wake) it - ToolGroup.Apps else it }\n")

    music = "app/src/main/java/com/cleo/cleos/ai/Music.kt"
    replace(music, "    override fun now(): NowPlaying? = pick(seen())?.let(::describe)\n", '''    override fun now(): NowPlaying? = pick(seen())?.let(::describe)

    /** Point-song confirmation reads the selected player, not another music app. */
    fun currentFor(player: String): NowPlaying? = pick(seen().filter { it.controller.packageName == player })?.let(::describe)

    /** Supported sessions may implement Android's search request. Sending it is not confirmation. */
    fun search(player: String, song: SongRequest): Boolean {
        val controller = controllers().firstOrNull { it.packageName == player } ?: return false
        return runCatching {
            if ((controller.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_SEARCH == 0L) return@runCatching false
            controller.transportControls.playFromSearch(song.query, android.os.Bundle().apply {
                putString(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, android.provider.MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
                putString(android.provider.MediaStore.EXTRA_MEDIA_TITLE, song.title)
                putString(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, song.artist)
            })
            true
        }.getOrDefault(false)
    }
''')

    manifest = "app/src/main/AndroidManifest.xml"
    replace(manifest, "    <application\n", '''    <!-- Only installed, launchable apps are visible; no QUERY_ALL_PACKAGES permission. -->
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>

    <application
''')
    replace(manifest, "    </application>", '''        <!-- Invoked only by an explicit QQ point-song task, package-scoped in XML. -->
        <service
            android:name=".QqMusicAccessibilityService"
            android:exported="true"
            android:label="@string/qq_music_accessibility_label"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data android:name="android.accessibilityservice" android:resource="@xml/qq_music_accessibility" />
        </service>
    </application>''')
    replace("app/src/main/res/values/strings.xml", "</resources>", '''    <string name="qq_music_accessibility_label">怀民亦未寝 · QQ 音乐点歌</string>
    <string name="qq_music_accessibility_description">你明确要求 QQ 音乐点歌后，读取 QQ 音乐当前界面，填写歌名、歌手并点击匹配歌曲。只操作 QQ 音乐，界面信息不发送给 AI；不执行登录、付款或开会员。离开 QQ 音乐、锁屏或超时会停止，可随时在系统设置关闭。</string>
</resources>''')

    pages = "app/src/main/java/com/cleo/cleos/ui/settings/SharedPages.kt"
    replace(pages, "import com.cleo.cleos.ai.PhoneMusic\n", "import com.cleo.cleos.ai.PhoneMusic\nimport com.cleo.cleos.QqMusicAccessibilityService\n")
    replace(pages, "    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { musicAllowed = PhoneMusic.allowed(context) }", '''    var qqAllowed by remember { mutableStateOf(QqMusicAccessibilityService.enabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        musicAllowed = PhoneMusic.allowed(context)
        qqAllowed = QqMusicAccessibilityService.enabled(context)
    }''')
    replace(pages, '''        ExplainedSwitch(
            "一起听歌",''', '''        ExplainedSwitch(
            "打开手机 App",
            "说「打开 QQ 音乐播放 h3r3 的灰」",
            "TA 可以打开已安装的手机 App；QQ 音乐支持按歌名和歌手自动搜索、点选、核实播放。先开一次下方两项权限。点歌时保持手机解锁，等 QQ 音乐完成搜索，别切走。其他 App 目前支持打开。",
            on(ToolGroup.Apps),
        ) { vm.setTool(ToolGroup.Apps, it) }
        if (on(ToolGroup.Apps)) {
            Under {
                Text(
                    "播放状态权限：${if (musicAllowed) "已开启" else "未开启"} · QQ 音乐点歌权限：${if (qqAllowed) "已开启" else "未开启"}",
                    color = if (musicAllowed && qqAllowed) palette.contentSecondary else palette.error,
                    fontSize = 12.sp, lineHeight = 18.sp,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("播放状态权限", selected = musicAllowed) { openMusicAccess() }
                    Chip("QQ 音乐点歌权限", selected = qqAllowed) {
                        runCatching { context.startActivity(QqMusicAccessibilityService.accessIntent(context)) }
                    }
                    Chip("应用信息", selected = false) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
                    }
                }
                Text("在系统无障碍页开启「怀民亦未寝 · QQ 音乐点歌」。开关灰色时，先从「应用信息」右上角 ⋮ 允许受限制的设置。只在你点歌时操作 QQ 音乐，不自动登录、付款或开会员。", color = palette.contentSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        RowDivider(inset = 0.dp)
        ExplainedSwitch(
            "一起听歌",''')
    replace("app/src/main/java/com/cleo/cleos/ui/settings/SettingsPages.kt", "ToolGroup.Calendar, ToolGroup.Music, ToolGroup.Avatar,", "ToolGroup.Calendar, ToolGroup.Music, ToolGroup.Apps, ToolGroup.Avatar,")

    for rel, text in pending.items():
        (root / rel).write_text(text)
    files = [
        ("PhoneAppActions.kt", "main/java/com/cleo/cleos/ai"),
        ("AndroidPhoneApps.kt", "main/java/com/cleo/cleos/ai"),
        ("QqSearchPlan.kt", "main/java/com/cleo/cleos/ai"),
        ("QqMusicAccessibilityService.kt", "main/java/com/cleo/cleos"),
        ("qq_music_accessibility.xml", "main/res/xml"),
        ("PhoneAppsTest.kt", "test/java/com/cleo/cleos/ai"),
        ("QqSearchPlanTest.kt", "test/java/com/cleo/cleos/ai"),
    ]
    for name, folder in files:
        dest = root / "app/src" / folder / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(here / "src" / name, dest)
    shutil.copyfile(here / "tests/ToolSettingsTest.kt", root / "app/src/test/java/com/cleo/cleos/data/ToolSettingsTest.kt")
    print("手机 App 打开、QQ 音乐搜索点歌、实际播放确认及权限入口补丁已应用。")


if __name__ == "__main__":
    apply_phone_apps(Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd())
