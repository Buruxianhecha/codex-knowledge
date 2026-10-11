#!/usr/bin/env python3
"""0.38.21 dedicated Kugou tool, accessibility permission and playback confirmation."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
changed={}
def patch(path,old,new,reason):
    s=changed.get(path,(root/path).read_text(encoding="utf-8"))
    n=s.count(old)
    if n!=1: raise RuntimeError(f"{reason}: expected one target, found {n} in {path}")
    changed[path]=s.replace(old,new,1)

tools="app/src/main/java/com/cleo/cleos/ai/Tools.kt"
phone="app/src/main/java/com/cleo/cleos/ai/PhoneAppActions.kt"
android="app/src/main/java/com/cleo/cleos/ai/AndroidPhoneApps.kt"
shared="app/src/main/java/com/cleo/cleos/ui/settings/SharedPages.kt"

patch(phone,'"酷狗音乐" to listOf("com.kugou.android"),',
      '"酷狗音乐" to listOf("com.kugou.android"), "酷狗" to listOf("com.kugou.android"), "kugou" to listOf("com.kugou.android"),',"Kugou app name")
patch(phone,'    suspend fun playQQ(title: String, artist: String?): ToolOutcome\n',
'''    suspend fun playQQ(title: String, artist: String?): ToolOutcome
    suspend fun playKugou(title: String, artist: String?): ToolOutcome =
        throw ToolFailure("酷狗音乐点歌在此设备上尚未接入。", "未接入酷狗")
''',"dedicated interface; preserve legacy adapters")

patch(android,'import com.cleo.cleos.QqMusicAccessibilityService\n',
      'import com.cleo.cleos.QqMusicAccessibilityService\nimport com.cleo.cleos.KugouMusicAccessibilityService\n',"Android import")
patch(android,') : PhoneAppActions, QqPlaybackPhone {',') : PhoneAppActions, QqPlaybackPhone, KugouPlaybackPhone {',"Android Kugou interface")
patch(android,'    private val player = QqPlayback(this)\n',
      '    private val player = QqPlayback(this)\n    private val kugouPlayer = KugouPlayback(this)\n',"Kugou player")
patch(android,'    override suspend fun playQQ(title: String, artist: String?) = player.play(SongRequest.of(title, artist))\n',
'''    override suspend fun playQQ(title: String, artist: String?) = player.play(SongRequest.of(title, artist))
    override suspend fun playKugou(title: String, artist: String?) =
        kugouPlayer.play(SongRequest.of(title, artist))
    override fun installedKugou(): LaunchableApp =
        AppNames.find("酷狗音乐", launcherApps().filter { it.packageName == "com.kugou.android" })
    override fun screenControlKugou(): Boolean = KugouMusicAccessibilityService.connected()
''',"Android Kugou entry")
patch(android,'    override suspend fun selectOnScreen(app: LaunchableApp, song: SongRequest): String? =\n',
'''    override suspend fun beginKugou(app: LaunchableApp, song: SongRequest): Boolean =
        begin(app, song) // uses the chosen app's package; cannot substitute QQ Music
    override suspend fun selectKugouOnScreen(app: LaunchableApp, song: SongRequest): String? =
        KugouMusicAccessibilityService.select(app.packageName, song)
    override suspend fun selectOnScreen(app: LaunchableApp, song: SongRequest): String? =
''',"Android Kugou begin and select")

spec='''    val playKugouMusic = ToolSpec(
        name = "play_kugou_music",
        groups = setOf(ToolGroup.Apps),
        action = "酷狗音乐点歌",
        description = "对方明确说在酷狗音乐播放某歌时必须调用本工具；不允许换成 QQ 音乐。" +
            "只针对安装包 com.kugou.android 搜索播放，并用酷狗实际媒体会话核实歌曲正在播放。" +
            "系统指令失败才在专门授权的酷狗无障碍服务中搜索并选中歌曲；权限未开则提示开启。" +
            "打不开或搜索失败要明确反馈，不得打开 QQ 音乐代替。",
        parameters = schema(
            required = listOf("title"),
            "title" to prop("string", "用户指定的完整歌名"),
            "artist" to prop("string", "用户指定歌手，可不填，不能猜"),
        ),
    )

'''
t=changed.get(tools,(root/tools).read_text(encoding="utf-8"))
if t.count("    val all = listOf(\n")!=1: raise RuntimeError("ToolSpecs all list location missing")
changed[tools]=t.replace("    val all = listOf(\n",spec+"    val all = listOf(\n",1)
patch(tools,'        playQqMusic,\n','        playQqMusic,\n        playKugouMusic,\n',"new tool registered")
patch(tools,'                ToolSpecs.musicControl.name -> musicControl(args)\n',
'''                ToolSpecs.playKugouMusic.name -> (phoneApps
                    ?: throw ToolFailure("这里不能在酷狗音乐点歌。", "不能点歌")).playKugou(
                    ToolArgs.text(args, "title")
                        ?: throw ToolFailure("请说明要在酷狗音乐播放的歌名。", "缺少歌名"),
                    ToolArgs.text(args, "artist"),
                )
                ToolSpecs.musicControl.name -> musicControl(args)
''',"dedicated execution route")

prompt="app/src/main/java/com/cleo/cleos/ai/Prompt.kt"
ps=(root/prompt).read_text(encoding="utf-8")
first='        if (ToolGroup.Apps in tools) {\n'
last='        if (ToolGroup.Music in tools) {\n'
if ps.count(first)!=1: raise RuntimeError("Music tool prompt block not found")
i=ps.index(first);j=ps.index(last,i)
changed[prompt]=ps[:i]+'''        if (ToolGroup.Apps in tools) {
            add("必须尊重对方指定的音乐 App，绝对不能换播放器。" +
                "如果对方说打开酷狗音乐，只用 open_phone_app 指向「酷狗音乐」；" +
                "如果说打开酷狗播放小半、让酷狗音乐放一首歌，必须用 play_kugou_music，不能调用 play_qq_music。" +
                "只有明确指定 QQ 音乐时才能用 play_qq_music。" +
                "没有明确指定播放器时，用 request_song 询问对方使用哪个已安装播放器，不自行跳到 QQ。" +
                "酷狗未安装、权限没开、找不到歌时原样反馈，绝不能用 QQ 音乐替代。" +
                "实际播放成功必须核实对应播放器的媒体会话和正确歌曲，不能把启动 App 当作已播放。" +
                "不代替用户登录、充值或开会员。")
        }
'''+ps[j:]

patch(shared,'import com.cleo.cleos.QqMusicAccessibilityService\n',
      'import com.cleo.cleos.QqMusicAccessibilityService\nimport com.cleo.cleos.KugouMusicAccessibilityService\n',"settings import")
patch(shared,'    var qqAllowed by remember { mutableStateOf(QqMusicAccessibilityService.enabled(context)) }\n',
'''    var qqAllowed by remember { mutableStateOf(QqMusicAccessibilityService.enabled(context)) }
    var kugouAllowed by remember { mutableStateOf(KugouMusicAccessibilityService.enabled(context)) }
''',"Kugou toggle status")
patch(shared,'        qqAllowed = QqMusicAccessibilityService.enabled(context)\n',
'''        qqAllowed = QqMusicAccessibilityService.enabled(context)
        kugouAllowed = KugouMusicAccessibilityService.enabled(context)
''',"Kugou on resume")
patch(shared,'''                    Chip("QQ 音乐点歌权限", selected = qqAllowed) {
                        runCatching { context.startActivity(QqMusicAccessibilityService.accessIntent(context)) }
                    }
''',
'''                    Chip("QQ 音乐点歌权限", selected = qqAllowed) {
                        runCatching { context.startActivity(QqMusicAccessibilityService.accessIntent(context)) }
                    }
                    Chip("酷狗音乐点歌权限", selected = kugouAllowed) {
                        runCatching { context.startActivity(KugouMusicAccessibilityService.accessIntent(context)) }
                    }
''',"new Kugou permission chip")
# In the same status line, show Kugou permission separately rather than implying QQ covers it.
path=shared
status=changed[path]
old=' · QQ 音乐点歌权限：'
if status.count(old)!=1: raise RuntimeError("permission status label unexpected")
status=status.replace(old,' · QQ 音乐点歌权限：',1)
# New independent status line avoids Kotlin interpolation in patch scripts.
anchor='                    Chip("QQ 音乐点歌权限", selected = qqAllowed)'
if anchor not in status: raise RuntimeError("missing status block")
changed[path]=status

manifest="app/src/main/AndroidManifest.xml"
patch(manifest,'''        <service
            android:name=".QqMusicAccessibilityService"''',
'''        <service
            android:name=".KugouMusicAccessibilityService"
            android:exported="true"
            android:label="@string/kugou_music_accessibility_label"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data android:name="android.accessibilityservice" android:resource="@xml/kugou_music_accessibility" />
        </service>
        <service
            android:name=".QqMusicAccessibilityService"''',"Kugou accessibility declaration")
patch("app/src/main/res/values/strings.xml",'</resources>',
'''    <string name="kugou_music_accessibility_label">怀民亦未寝 · 酷狗音乐点歌</string>
    <string name="kugou_music_accessibility_description">只有在你明确点歌时操作酷狗音乐界面，不操作 QQ 音乐，也不自动登录、付款或充值；可以随时关闭。</string>
</resources>''',"Kugou accessibility resources")
patch("app/build.gradle.kts",'versionName = "0.38.20"','versionName = "0.38.21"',"version")
patch("app/build.gradle.kts",'versionCode = 62086','versionCode = 62087',"build number")
for rel,text in changed.items(): (root/rel).write_text(text,encoding="utf-8")
base=root/"app/src/main/java/com/cleo/cleos"
for name,folder in [("KugouPlayback.kt","ai"),("KugouSearchPlan.kt","ai"),("KugouMusicAccessibilityService.kt","")]:
    dest=base/folder/name
    dest.parent.mkdir(parents=True,exist_ok=True)
    copyfile(here/"src"/name,dest)
copyfile(here/"src/kugou_music_accessibility.xml",root/"app/src/main/res/xml/kugou_music_accessibility.xml")
print("v0.38.21: Kugou-only play tool, separate permission and real playback check; QQ untouched")
