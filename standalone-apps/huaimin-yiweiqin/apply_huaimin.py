#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys
from verify_icon import verify_source
from apply_model_save import apply_model_save
from apply_sticker_vision import apply_sticker_vision
from apply_sticker_presets import apply_sticker_presets
from apply_pat import apply_pat

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(".").resolve()


def replace_once(rel: str, old: str, new: str):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected exactly one match, found {count}: {old[:80]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) 独立安装身份 + 手机上显示中文名。namespace 不动，避免为了改包路径触碰大量源码。
replace_once(
    "app/build.gradle.kts",
    '        applicationId = "com.cleo.cleos"\n        minSdk = 29\n        targetSdk = 36\n        versionCode = 66\n        versionName = "0.35.7"',
    '        applicationId = "com.lin.huaimin"\n        minSdk = 29\n        targetSdk = 36\n        versionCode = 62020\n        versionName = "0.37.3"',
)
replace_once(
    "app/src/main/res/values/strings.xml",
    '<string name="app_name">Cleos</string>',
    '<string name="app_name">怀民亦未寝</string>',
)

# 2) 扩展系统闹钟能力：下一次闹钟 + 打开完整闹钟列表。
replace_once(
    "app/src/main/java/com/cleo/cleos/ai/Alarms.kt",
    '''    /** Throws [ToolFailure]. */
    fun setTimer(seconds: Int, label: String)
}''',
    '''    /** Throws [ToolFailure]. */
    fun setTimer(seconds: Int, label: String)

    /** Android only exposes the next alarm-clock trigger, not the clock app's full alarm database. */
    fun nextAlarm(): Long? = null

    /** Open the phone clock app's alarm list. Throws [ToolFailure] when no clock app can handle it. */
    fun showAlarms() {
        throw ToolFailure("这台手机没有提供可打开的闹钟列表。", "打不开闹钟列表")
    }
}''',
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ai/Alarms.kt",
    '''    /** The next alarm on the phone, anyone's; null when there is none, or the phone won't say. */
    fun nextAlarm(): Long? = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
''',
    '''    /** The next alarm on the phone, anyone's; null when there is none, or the phone won't say. */
    override fun nextAlarm(): Long? = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime

    /** Show the complete list in the system clock app; Android does not expose that list for us to read. */
    override fun showAlarms() {
        open(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    }
''',
)

tools = "app/src/main/java/com/cleo/cleos/ai/Tools.kt"
replace_once(
    tools,
    '''    val readCalendar = ToolSpec(
''',
    '''    val getNextAlarm = ToolSpec(
        name = "get_next_alarm",
        groups = setOf(ToolGroup.Alarm),
        action = "看下一个闹钟",
        description = "读取 Android 系统公开的下一次闹钟时间。只能得到下一次触发时间；系统不允许普通 App 读取时钟 App 里的完整闹钟列表、标签和重复规则。",
        parameters = schema(),
    )
    val showAlarms = ToolSpec(
        name = "show_alarms",
        groups = setOf(ToolGroup.Alarm),
        action = "打开闹钟列表",
        description = "打开对方手机自带时钟 App 的闹钟列表。对方问“我有哪些闹钟”“查看全部闹钟”时用；不要编造列表内容。",
        parameters = schema(),
    )
    val readCalendar = ToolSpec(
''',
)
replace_once(
    tools,
    '''        setAlarm,
        setTimer,
        readCalendar,
''',
    '''        setAlarm,
        setTimer,
        getNextAlarm,
        showAlarms,
        readCalendar,
''',
)
replace_once(
    tools,
    '''                ToolSpecs.setAlarm.name -> setAlarm(args)
                ToolSpecs.setTimer.name -> setTimer(args)
                ToolSpecs.readCalendar.name -> readCalendar(args, today)
''',
    '''                ToolSpecs.setAlarm.name -> setAlarm(args)
                ToolSpecs.setTimer.name -> setTimer(args)
                ToolSpecs.getNextAlarm.name -> getNextAlarm()
                ToolSpecs.showAlarms.name -> showAlarms()
                ToolSpecs.readCalendar.name -> readCalendar(args, today)
''',
)
replace_once(
    tools,
    '''    private fun setTimer(a: JsonObject): ToolOutcome {
''',
    '''    private fun getNextAlarm(): ToolOutcome {
        val phone = alarms ?: throw ToolFailure("这里看不了闹钟。", "这里看不了")
        val trigger = phone.nextAlarm()
            ?: return ToolOutcome(
                "Android 系统没有提供下一次闹钟时间：可能没有启用的闹钟，或者当前时钟 App 没有公开。要看完整列表，可以打开系统闹钟页。",
                "没查到下一个闹钟",
            )
        val now = Instant.ofEpochMilli(clock()).atZone(zone())
        val at = Instant.ofEpochMilli(trigger).atZone(zone())
        val whenText = LaterRules.at(at, now)
        return ToolOutcome(
            "手机下一次系统闹钟是 $whenText。Android 只公开下一次触发时间，不公开完整闹钟明细。",
            "下一个闹钟：$whenText",
        )
    }

    private fun showAlarms(): ToolOutcome {
        val phone = alarms ?: throw ToolFailure("这里看不了闹钟。", "这里看不了")
        phone.showAlarms()
        return ToolOutcome(
            "已经打开手机自带时钟的闹钟列表。完整闹钟由系统时钟 App 管理。",
            "打开了闹钟列表",
        )
    }

    private fun setTimer(a: JsonObject): ToolOutcome {
''',
)

# 3) 告诉模型什么时候该查、什么时候该打开列表，避免“读不到却编一个列表”。
replace_once(
    "app/src/main/java/com/cleo/cleos/ai/Prompt.kt",
    '''            add("对方让你定闹钟、叫醒、计时的时候，用 set_alarm 或 set_timer 在对方手机的时钟里定，定好了再说；没调用就别说定好了。对方没让，别自己给对方定闹钟。")
''',
    '''            add("对方让你定闹钟、叫醒、计时的时候，用 set_alarm 或 set_timer 在对方手机的时钟里定，定好了再说；没调用就别说定好了。对方没让，别自己给对方定闹钟。对方问下一个闹钟几点，用 get_next_alarm；问有哪些闹钟、想查看全部闹钟，用 show_alarms 打开系统时钟列表。Android 不让普通 App 读取完整闹钟明细，所以不要凭空列出全部闹钟。")
''',
)

# 4) 设置页说明同步更新；只改闹钟这一项，不碰别的功能开关。
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/SharedPages.kt",
    '''            "定闹钟",
            "在手机自带的时钟里定闹钟、计时",
            "你让 TA 定闹钟、计时，它在手机自带的时钟里定，到点手机响，和你自己定的一样。只在 Cleos 开着的时候能定：手机不让 App 在后台打开时钟。",
''',
    '''            "闹钟",
            "定闹钟、计时，查看下一次闹钟或打开闹钟列表",
            "你让 TA 定闹钟、计时，它会交给手机自带时钟；问下一个闹钟几点时可以直接读取系统公开的下一次触发时间；问全部闹钟时会打开系统时钟列表。Android 不允许普通 App 直接读取完整闹钟明细。设置和打开列表都需要怀民亦未寝在前台。",
''',
)

# 5) 关于页顶部使用新名称；原版发布入口、致谢/支持原作者等功能仍保留。
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    '''    Section("Cleos ${version.orEmpty()}") {
''',
    '''    Section("怀民亦未寝 ${version.orEmpty()}") {
''',
)

# 6) 增加单元测试，验证“查下一次 + 打开列表”的工具链，不改变原测试。
test_path = ROOT / "app/src/test/java/com/cleo/cleos/ai/AlarmsCalendarTest.kt"
test_text = test_path.read_text(encoding="utf-8")
needle = '''    @Test
    fun calendarTimesAreStoredTheWayCalendarsKeepThem() {
'''
if test_text.count(needle) != 1:
    raise SystemExit("AlarmsCalendarTest.kt: insertion point not found exactly once")
new_test = '''    @Test
    fun nextAlarmCanBeReadAndTheFullListCanBeOpened() = runBlocking {
        var opened = false
        val phone = object : AlarmSource {
            override fun setAlarm(hour: Int, minute: Int, label: String, days: List<Int>) = Unit
            override fun setTimer(seconds: Int, label: String) = Unit
            override fun nextAlarm(): Long = at(9, 30, 7, 45)
            override fun showAlarms() {
                opened = true
            }
        }
        val box = ToolBox(unused(), unused(), unused(), alarms = phone, clock = { now.toInstant().toEpochMilli() }, zone = { zone })
        val settings = AppSettings()

        val next = box.run(ToolCall("next", "get_next_alarm", "{}"), settings)
        assertEquals("下一个闹钟：明天 07:45", next.note)
        assertTrue(next.result.contains("Android 只公开下一次触发时间"))

        val shown = box.run(ToolCall("show", "show_alarms", "{}"), settings)
        assertTrue(opened)
        assertEquals("打开了闹钟列表", shown.note)
    }

'''
test_path.write_text(test_text.replace(needle, new_test + needle, 1), encoding="utf-8")

# 7) 只替换应用图标；直接让 Manifest 指向最终图片，兼容部分国产系统安装器。
icon_src = Path(__file__).resolve().parent / "assets" / "app_icon.jpg"
verify_source(icon_src)
icon_dst = ROOT / "app/src/main/res/drawable-nodpi/huaimin_app_icon.jpg"
icon_dst.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(icon_src, icon_dst)

replace_once(
    "app/src/main/AndroidManifest.xml",
    '        android:icon="@mipmap/ic_launcher"\n        android:label="@string/app_name"\n        android:roundIcon="@mipmap/ic_launcher"',
    '        android:icon="@drawable/huaimin_app_icon"\n        android:label="@string/app_name"\n        android:roundIcon="@drawable/huaimin_app_icon"',
)

# 8) 关于页品牌文案与赞赏码。保持应用图标等其他资源不变。
about_page = "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt"

replace_once(
    about_page,
    '"Cleos 是一个人做的，一直免费。觉得好用、想请开发者喝杯奶茶的话，可以用微信扫一下。"',
    '"怀民亦未寝基于 Cleos 开发，并在此基础上进行了适配、功能扩展与持续维护，目前一直免费提供使用。如果你觉得好用，愿意支持后续开发，可以用微信扫一下，请开发者喝杯奶茶 ☕。"',
)
replace_once(
    about_page,
    'put(MediaStore.Images.Media.DISPLAY_NAME, "Cleos-milk-tea.png")',
    'put(MediaStore.Images.Media.DISPLAY_NAME, "怀民亦未寝-赞赏码.jpg")',
)
replace_once(
    about_page,
    'put(MediaStore.Images.Media.MIME_TYPE, "image/png")',
    'put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")',
)
replace_once(
    about_page,
    'put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Cleos")',
    'put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/怀民亦未寝")',
)
replace_once(
    about_page,
    '"存好了，在相册的「Cleos」里。打开微信「扫一扫」，点右上角的相册选它就行。"',
    '"存好了，在相册的「怀民亦未寝」里。打开微信「扫一扫」，点右上角的相册选它就行。"',
)
replace_once(
    about_page,
    '"Cleos 上次闪退了（${text.lineSequence().first().substringAfter("，").substringBefore(" 闪退")}）。"',
    '"怀民亦未寝上次闪退了（${text.lineSequence().first().substringAfter("，").substringBefore(" 闪退")}）。"',
)
replace_once(
    about_page,
    'ClipData.newPlainText("Cleos 闪退记录", text)',
    'ClipData.newPlainText("怀民亦未寝 闪退记录", text)',
)
replace_once(
    about_page,
    '"（Zenodo，doi:10.5281/zenodo.4297951，CC BY 4.0；Cleos 只留了三档距离、换了存法），"',
    '"（Zenodo，doi:10.5281/zenodo.4297951，CC BY 4.0；怀民亦未寝只留了三档距离、换了存法），"',
)

tip_src = Path(__file__).resolve().parent / "assets" / "tip_qr.jpg"
tip_old = ROOT / "app/src/main/res/drawable-nodpi/tip_qr.png"
tip_dst = ROOT / "app/src/main/res/drawable-nodpi/tip_qr.jpg"
if not tip_src.is_file():
    raise SystemExit(f"missing tip image: {tip_src}")
if tip_old.exists():
    tip_old.unlink()
tip_dst.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(tip_src, tip_dst)

# 9) 本次版本：赞赏码按钮文案。
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    'Chip("看收款码", selected = false) { showTip = true }',
    '''Chip("看赞赏码", selected = false) {
            // Prefer WeChat's public scan URL scheme. Some Android/WeChat builds do not expose it,
            // so try the scanner activity as a second route. If both fail, fall back to the
            // existing in-app QR dialog.
            val scanIntent = Intent(Intent.ACTION_VIEW, Uri.parse("weixin://scanqrcode"))
                .setPackage("com.tencent.mm")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opened = runCatching {
                context.startActivity(scanIntent)
                true
            }.getOrDefault(false) || runCatching {
                context.startActivity(
                    Intent()
                        .setClassName("com.tencent.mm", "com.tencent.mm.plugin.scanner.ui.BaseScanUI")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
            if (!opened) showTip = true
        }'''
)

# 9.1) 0.37.1：新版安装包改放 QQ 群文件，按钮直接拉起 QQ 群名片。
replace_once(
    about_page,
    '''        Text(
            "新版本都放在蓝奏云上。更新时直接装新的 apk、覆盖安装，聊天记录都还在；别先卸载，卸载会把这台手机上的聊天、" +
                "日记一起清掉。真要重装，先在「数据与备份」里导出一份备份。",
            color = palette.contentSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Chip("去蓝奏云看新版", selected = false) {
            // The page asks for the code once; it is on the clipboard by then.
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("提取码", Releases.CODE))
            releasesHint = "正在找能打开的地址…"
            // On whichever of 蓝奏云's domains still exists: one of them going away stranded every copy of an
            // older version on a page that never loads.
            scope.launch {
                val url = Releases.reachableUrl()
                val opened = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                releasesHint = if (opened) {
                    "提取码 ${Releases.CODE} 已经复制好了，页面让输密码时粘贴就行。"
                } else {
                    "没找到能打开网页的浏览器。地址是 $url ，提取码 ${Releases.CODE}（已复制）。"
                }
            }
        }
        releasesHint?.let { Text(it, color = palette.content, fontSize = 13.sp, lineHeight = 19.sp) }
''',
    '''        Text(
            "新版本放在 QQ 群 1026802228 的群文件里。更新时直接下载安装新的 apk、覆盖安装，聊天记录都还在；" +
                "别先卸载，卸载会把这台手机上的聊天、日记一起清掉。真要重装，先在「数据与备份」里导出一份备份。",
            color = palette.contentSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Chip("去QQ群看新版", selected = false) {
            val group = "1026802228"
            val qqUri = Uri.parse(
                "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$group&card_type=group&source=qrcode"
            )
            val opened = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, qqUri)
                        .setPackage("com.tencent.mobileqq")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            releasesHint = if (opened) {
                "已经打开 QQ 群 1026802228，新版安装包在群文件里。"
            } else {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("QQ群号", group))
                "没有找到手机 QQ。群号 1026802228 已复制，请打开 QQ 搜索群号。"
            }
        }
        releasesHint?.let { Text(it, color = palette.content, fontSize = 13.sp, lineHeight = 19.sp) }
''',
)

# 10) 0.36.1：保留原有 4 张预设，新增清理后的海底峡谷壁纸。现有应用图标保持不变。
for index in range(1, 6):
    preset_src = Path(__file__).resolve().parent / "assets" / f"wallpaper_{index}.jpg"
    preset_dst = ROOT / "app/src/main/res/drawable-nodpi" / f"huaimin_wallpaper_{index}.jpg"
    if not preset_src.is_file():
        raise SystemExit(f"missing preset wallpaper: {preset_src}")
    preset_dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(preset_src, preset_dst)

replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt",
    '    fun setWallpaper(uri: Uri) {\n',
    '    fun setWallpaper(uri: Uri, prefix: String = "wallpaper-") {\n',
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt",
    '                val stored = c.images.import(uri, maxEdge = 2560, prefix = "wallpaper-")',
    '                val stored = c.images.import(uri, maxEdge = 2560, prefix = prefix)',
)

replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    'import androidx.compose.foundation.layout.heightIn\n',
    'import androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.size\n',
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    'import androidx.compose.material3.TextButton\n',
    'import androidx.compose.material3.TextButton\nimport androidx.compose.foundation.shape.RoundedCornerShape\n',
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    'import androidx.compose.ui.Alignment\n',
    'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.draw.clip\n',
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    """    val palette = LocalGlassPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
""",
    """    val palette = LocalGlassPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
""",
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    """        vm.wallpaperError?.let { Text(it, color = palette.error, fontSize = 13.sp) }
    }

    Section("玻璃") {
""",
    """        vm.wallpaperError?.let { Text(it, color = palette.error, fontSize = 13.sp) }

        Text("预设壁纸", color = palette.content, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        val presets = listOf(
            Triple(R.drawable.huaimin_wallpaper_1, "月下荷塘", "wallpaper-preset-1-"),
            Triple(R.drawable.huaimin_wallpaper_2, "云海朝霞", "wallpaper-preset-2-"),
            Triple(R.drawable.huaimin_wallpaper_3, "竹影月湖", "wallpaper-preset-3-"),
            Triple(R.drawable.huaimin_wallpaper_4, "月下庭园", "wallpaper-preset-4-"),
            Triple(R.drawable.huaimin_wallpaper_5, "海底峡谷", "wallpaper-preset-5-"),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            presets.forEach { (resId, label, prefix) ->
                val selected = settings.wallpaper?.startsWith(prefix) == true
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 64.dp, height = 104.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = !vm.wallpaperBusy) {
                                val uri = Uri.parse("android.resource://${context.packageName}/$resId")
                                vm.setWallpaper(uri, prefix)
                            },
                    )
                    Text(
                        if (selected) "✓ $label" else label,
                        color = if (selected) palette.accent else palette.contentSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }

    Section("玻璃") {
""",
)

# 11) 0.35.6：设置首页“关于”摘要使用新应用名。
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/SettingsPages.kt",
    '            Entry(Icons.Rounded.Info, "关于", "Cleos $version · 上次闪退了，记录在这里", palette.error) { onOpen(SettingsPage.About) }',
    '            Entry(Icons.Rounded.Info, "关于", "怀民亦未寝 $version · 上次闪退了，记录在这里", palette.error) { onOpen(SettingsPage.About) }',
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/SettingsPages.kt",
    '            Entry(Icons.Rounded.Info, "关于", "Cleos $version · 新版本、许可与出处") { onOpen(SettingsPage.About) }',
    '            Entry(Icons.Rounded.Info, "关于", "怀民亦未寝 $version · 新版本、许可与出处") { onOpen(SettingsPage.About) }',
)

# 12) 0.35.7：内置《用户协议》和《隐私政策》全文页面。
legal_src = Path(__file__).resolve().parent / "src" / "LegalPages.kt"
legal_dst = ROOT / "app/src/main/java/com/cleo/cleos/ui/settings/LegalPages.kt"
if not legal_src.is_file():
    raise SystemExit(f"missing legal pages source: {legal_src}")
legal_dst.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(legal_src, legal_dst)

replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/SettingsPages.kt",
    '''    Data("数据与备份"),
    About("关于"),
    ;
''',
    '''    Data("数据与备份"),
    About("关于"),
    UserAgreement("用户协议"),
    PrivacyPolicy("隐私政策"),
    ;
''',
)

replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/SettingsScreen.kt",
    '''                    SettingsPage.Data -> DataPage(vm)
                    SettingsPage.About -> AboutPage()
''',
    '''                    SettingsPage.Data -> DataPage(vm)
                    SettingsPage.About -> AboutPage(
                        onOpenAgreement = { page = SettingsPage.UserAgreement },
                        onOpenPrivacy = { page = SettingsPage.PrivacyPolicy },
                    )
                    SettingsPage.UserAgreement -> UserAgreementPage()
                    SettingsPage.PrivacyPolicy -> PrivacyPolicyPage()
''',
)

replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    '''internal fun AboutPage() {
''',
    '''internal fun AboutPage(
    onOpenAgreement: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
''',
)

replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    '''    Section("隐私") {
        Text(
            "聊天、日记和待办都只存在这台手机上。API Key 用系统密钥库加密。",
            color = palette.contentSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
''',
    '''    Section("协议与隐私") {
        Text(
            "聊天、日记和待办主要保存在这台手机上；使用第三方 AI、语音、位置、天气或 MCP 服务时，完成请求所必要的数据可能会发送给相应服务。详细规则请查看下面的全文。",
            color = palette.contentSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("用户协议", selected = false, onClick = onOpenAgreement)
            Chip("隐私政策", selected = false, onClick = onOpenPrivacy)
        }
    }
''',
)

# 13) 0.35.8：清理工具设置页残留品牌名；日历采用方案 A。
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/SharedPages.kt",
    '"看你的日程，记的加在「Cleos」日历里"',
    '"看你的日程，记的加在「怀民亦未寝」日历里"',
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/SharedPages.kt",
    '"TA 能看你手机日历上的安排；你让它记的日程，加在一个叫「Cleos」的日历里，可以带提醒。它只能改、删自己加的，你的日程只能看。要日历权限，打开时会问。"',
    '"TA 能看你手机日历上的安排；你让它记的日程，加在一个叫「怀民亦未寝」的日历里，可以带提醒。它只能改、删自己加的，你的日程只能看。要日历权限，打开时会问。"',
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/SharedPages.kt",
    '"还没开「通知使用权」，TA 听不到：点「去开」，把 Cleos 的开关打开。" +',
    '"还没开「通知使用权」，TA 听不到：点「去开」，把「怀民亦未寝」的开关打开。" +',
)

# 日历真正创建/显示的名称也同步为“怀民亦未寝”。
replace_once(
    "app/src/main/java/com/cleo/cleos/ai/Calendars.kt",
    '            put(Calendars.NAME, "cleos")\n            put(Calendars.CALENDAR_DISPLAY_NAME, "Cleos")',
    '            put(Calendars.NAME, "huaimin")\n            put(Calendars.CALENDAR_DISPLAY_NAME, "怀民亦未寝")',
)

# 14) 0.35.9：把“月下庭园”设为真正的默认壁纸。
# 默认状态（settings.wallpaper == null）直接显示内置 wallpaper_4；
# “用回默认”会回到月下庭园，预设列表也把它标记为当前选中。
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/theme/CleosTheme.kt",
    "import androidx.compose.ui.platform.LocalDensity\n",
    "import androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.platform.LocalContext\n",
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/theme/CleosTheme.kt",
    "        GlassMode.Auto -> if (custom != null) settings.wallpaperDark ?: systemDark else systemDark",
    "        GlassMode.Auto -> if (custom != null) settings.wallpaperDark ?: systemDark else true",
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/theme/CleosTheme.kt",
    """    val config = LocalConfiguration.current
    val density = LocalDensity.current
""",
    """    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val bundledDefault = remember {
        BitmapFactory.decodeResource(context.resources, com.cleo.cleos.R.drawable.huaimin_wallpaper_4)?.asImageBitmap()
    }
""",
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/theme/CleosTheme.kt",
    """    val image = bitmap
    if (file != null && image != null) {
""",
    """    val image = bitmap ?: if (file == null) bundledDefault else null
    if (image != null) {
""",
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    '                val selected = settings.wallpaper?.startsWith(prefix) == true',
    '''                val selected = if (prefix == "wallpaper-preset-4-") {
                    settings.wallpaper == null || settings.wallpaper?.startsWith(prefix) == true
                } else {
                    settings.wallpaper?.startsWith(prefix) == true
                }''',
)
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt",
    '''                            .clickable(enabled = !vm.wallpaperBusy) {
                                val uri = Uri.parse("android.resource://${context.packageName}/$resId")
                                vm.setWallpaper(uri, prefix)
                            },''',
    '''                            .clickable(enabled = !vm.wallpaperBusy) {
                                if (prefix == "wallpaper-preset-4-") {
                                    vm.resetWallpaper()
                                } else {
                                    val uri = Uri.parse("android.resource://${context.packageName}/$resId")
                                    vm.setWallpaper(uri, prefix)
                                }
                            },''',
)

# 15) 0.36.2：把模型页面的 OpenRouter 预设替换为用户指定的“随想”。
# 保留 OpenAI 兼容接口处理和按地址保存 Key；模型名由该服务的真实列表选择。
replace_once(
    "app/src/main/java/com/cleo/cleos/data/SettingsRepository.kt",
    '        ApiPreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),',
    '        ApiPreset("随想", "https://www.sui-xiang.net/v1", ""),',
)

# 16) 0.36.3：显式保存模型，等待写入和回读确认后退出；自动保存共用同一把锁。
apply_model_save(ROOT)

# 17) 0.36.4：从用户已发送的表情标记解析真实图片，接入普通图片的多模态请求。
apply_sticker_vision(ROOT)

# 18) 0.36.5：表情包已经支持真实图片识别，移除空表情列表里的过时说明。
replace_once(
    "app/src/main/java/com/cleo/cleos/ui/chat/Stickers.kt",
    '''        if (stickers.isEmpty()) {
            item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "从相册加几张表情包，起个名字。TA 看不到图，是按名字认的，也会从这里挑着发给你（设置里「发表情包」开着的话）。",
                    color = palette.contentSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        }
''',
    "",
)
# 19) 0.36.6：新增每个版本首次打开时的“本次更新”说明。
release_notes_src = Path(__file__).resolve().parent / "src" / "ReleaseNotes.kt"
release_notes_dst = ROOT / "app/src/main/java/com/cleo/cleos/ReleaseNotes.kt"
if not release_notes_src.is_file():
    raise SystemExit(f"missing release notes source: {release_notes_src}")
release_notes_dst.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(release_notes_src, release_notes_dst)

replace_once(
    "app/src/main/java/com/cleo/cleos/MainActivity.kt",
    '''            settings?.let { s ->
                CleosTheme(s, container.images) { CleosNavHost() }
                AskForNotifications(container, s)
            }
''',
    '''            settings?.let { s ->
                CleosTheme(s, container.images) {
                    CleosNavHost()
                    ReleaseNotesDialogIfNeeded()
                }
                AskForNotifications(container, s)
            }
''',
)
# 20) 0.36.7：加入内置小黄脸/手势表情预设，并增加小爱心“我的表情”入口。
apply_sticker_presets(ROOT)

# 21) 0.36.8：回移 Cleos 的“拍一拍”：双击头像、连拍计数、震动、可自定义文案与 TA 拍回来。
# 22) 0.36.9：拍 TA 后直接触发回复；连续拍会先合并，再只回复一轮。
apply_pat(ROOT)
print("怀民亦未寝增量补丁已应用。")
