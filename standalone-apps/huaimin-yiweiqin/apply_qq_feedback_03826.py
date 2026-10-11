#!/usr/bin/env python3
"""0.38.26: accurate QQ playback result, no false permission errors, IME recovery."""
from pathlib import Path
from shutil import copyfile
import sys
root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
java = root / "app/src/main/java/com/cleo/cleos"
tests = root / "app/src/test/java/com/cleo/cleos/ai"
for name, dest in [
    ("PhoneAppActions_03826.kt", java/"ai/PhoneAppActions.kt"),
    ("QqMusicAccessibilityService_03826.kt", java/"QqMusicAccessibilityService.kt"),
    ("QqWindowRules_03826.kt", java/"ai/QqWindowRules.kt"),
    ("PhoneAppsTest_03826.kt", tests/"PhoneAppsTest.kt"),
    ("QqWindowRulesTest_03826.kt", tests/"QqWindowRulesTest.kt"),
]:
    if dest.name not in ("QqWindowRules.kt", "QqWindowRulesTest.kt"):
        assert dest.is_file(), dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    copyfile(here / name, dest)
android = java/"ai/AndroidPhoneApps.kt"
s = android.read_text(encoding="utf-8")
def change(old, new, description):
    global s
    count = s.count(old)
    if count != 1: raise RuntimeError(f"{description}: expected 1, found {count}")
    s = s.replace(old, new, 1)
change("    override fun screenControlAllowed() = QqMusicAccessibilityService.connected()\n",
    "    override fun screenControlAllowed() = QqMusicAccessibilityService.connected()\n"
    "    override fun screenControlEnabled(): Boolean = QqMusicAccessibilityService.enabled(context)\n",
    "separate service enabled versus connected")
change("        QqMusicAccessibilityService.select(app.packageName, song)\n",
    "        QqMusicAccessibilityService.select(app.packageName, song) {\n"
    "            song.matches(music.currentFor(app.packageName), app.packageName)\n"
    "        }\n",
    "actual QQ music session checked during screen selection")
android.write_text(s, encoding="utf-8")
xml = root/"app/src/main/res/xml/qq_music_accessibility.xml"
s = xml.read_text(encoding="utf-8")
old = 'android:accessibilityFlags="flagReportViewIds"'
assert s.count(old) == 1
xml.write_text(s.replace(old, 'android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"', 1), encoding="utf-8")
prompt = java/"ai/Prompt.kt"
s = prompt.read_text(encoding="utf-8")
anchor = '        if (ToolGroup.Music in tools) {'
assert s.count(anchor) == 1
s = s.replace(anchor,
    '        if (ToolGroup.Apps in tools) {\n'
    '            add("音乐点歌工具的失败提示必须按实际诊断解释：选歌超时或不在前台并不表示 QQ 音乐点歌权限未开启。"\n'
    '                + "只有工具明确报告该权限未开启时才可要求开启；已开启但未连接时应说明系统服务未连接。"\n'
    '                + "只有工具确认对应播放器和目标歌曲真实处于播放状态才能说播放成功。"\n'
    '                + "不要用未经证实的权限原因替换工具返回的具体错误。")\n'
    '        }\n'
    + anchor, 1)
prompt.write_text(s, encoding="utf-8")
gradle = root/"app/build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
for old,new in [('versionName = "0.38.25"', 'versionName = "0.38.26"'),
                ('versionCode = 62091', 'versionCode = 62092')]:
    assert s.count(old) == 1, old
    s=s.replace(old,new,1)
gradle.write_text(s, encoding="utf-8")
print("0.38.26: QQ feedback / Android IME recovery / true media-state verification")
