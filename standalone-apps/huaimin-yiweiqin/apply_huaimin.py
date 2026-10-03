#!/usr/bin/env python3
from pathlib import Path
import sys

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
    '        applicationId = "com.cleo.cleos"\n        minSdk = 29\n        targetSdk = 36\n        versionCode = 62\n        versionName = "0.35.3"',
    '        applicationId = "com.lin.huaimin"\n        minSdk = 29\n        targetSdk = 36\n        versionCode = 62001\n        versionName = "0.35.3-huaimin.1"',
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

print("怀民亦未寝增量补丁已应用。")
