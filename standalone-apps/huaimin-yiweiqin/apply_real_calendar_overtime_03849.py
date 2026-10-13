#!/usr/bin/env python3
"""Install 0.38.49 on the complete, tested 0.38.48 tree without a destructive rebuild."""
from pathlib import Path
import sys

def apply(root: Path) -> None:
    src = Path(__file__).resolve().parent
    app = root / "app/src/main/java/com/cleo/cleos"
    tests = root / "app/src/test/java/com/cleo/cleos"
    replacements = {
        "LifeModels_03849.kt": app / "data/LifeModels.kt",
        "LifeStore_03849.kt": app / "data/LifeStore.kt",
        "LifeScreen_03849.kt": app / "ui/LifeScreen.kt",
        "ChinaWorkCalendar_03849.kt": app / "data/ChinaWorkCalendar.kt",
        "LifeRulesTest_03849.kt": tests / "data/LifeRulesTest.kt",
        "CalendarOvertimeTest_03849.kt": tests / "data/CalendarOvertimeTest.kt",
    }
    gradle = root / "app/build.gradle.kts"
    original = gradle.read_text(encoding="utf-8")
    assert original.count('versionName = "0.38.48"') == 1, "Expected exact v0.38.48 predecessor"
    assert original.count("versionCode = 62114") == 1, "Expected build 62114"
    hub = (app / "ui/HubScreens.kt").read_text(encoding="utf-8")
    tab = hub[hub.index("fun DiscoverTab("):]
    tab = tab[:tab.index("\n@Composable")]
    assert 'DirectoryRow("回忆"' not in tab and 'DirectoryRow("生活与成长"' in tab
    for file, path in replacements.items():
        assert (src / file).is_file(), "Missing feature patch: " + file
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes((src / file).read_bytes())
    changed = original.replace('versionName = "0.38.48"', 'versionName = "0.38.49"', 1)
    changed = changed.replace('versionCode = 62114', 'versionCode = 62115', 1)
    gradle.write_text(changed, encoding="utf-8")
    model = (app / "data/LifeModels.kt").read_text(encoding="utf-8")
    store = (app / "data/LifeStore.kt").read_text(encoding="utf-8")
    screen = (app / "ui/LifeScreen.kt").read_text(encoding="utf-8")
    assert 'RMB_PER_STARCOIN = 1' in model and "ChinaWorkCalendar" in model
    assert 'scheduleOvertime' in store and 'accrueWages' in store and 'creditSalary' in store
    assert 'LifeOvertimeDialog' in screen and '取消' in screen
    print("0.38.49/62115: official calendar, planned overtime, 1:1 wages; backup & Home memories untouched")

if __name__ == "__main__":
    if len(sys.argv) != 2: raise SystemExit("usage: apply_real_calendar_overtime_03849.py ANDROID_ROOT")
    apply(Path(sys.argv[1]).resolve())
