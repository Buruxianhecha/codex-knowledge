#!/usr/bin/env python3
"""Install voluntary, model-decided overtime over the exact complete 0.38.49 app."""
from pathlib import Path
import sys

def apply(root:Path) -> None:
    source=Path(__file__).resolve().parent
    app=root/"app/src/main/java/com/cleo/cleos"
    tests=root/"app/src/test/java/com/cleo/cleos"
    gradle=root/"app/build.gradle.kts"
    before=gradle.read_text(encoding="utf-8")
    assert before.count('versionName = "0.38.49"') == 1
    assert before.count("versionCode = 62115") == 1
    mapping={
        "LifeModels_03850.kt":app/"data/LifeModels.kt",
        "LifeStore_03850.kt":app/"data/LifeStore.kt",
        "LifeScreen_03850.kt":app/"ui/LifeScreen.kt",
        "LifeWakeWorker_03850.kt":app/"ai/LifeWakeWorker.kt",
        "LifeOvertimeAutonomy_03850.kt":app/"ai/LifeOvertimeAutonomy.kt",
        "VoluntaryOvertimeTest_03850.kt":tests/"ai/VoluntaryOvertimeTest.kt",
    }
    for file,dest in mapping.items():
        assert (source/file).is_file(), "Missing integration input: "+file
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes((source/file).read_bytes())
    after=before.replace('versionName = "0.38.49"','versionName = "0.38.50"',1)
    after=after.replace("versionCode = 62115","versionCode = 62116",1)
    gradle.write_text(after,encoding="utf-8")
    model=(app/"data/LifeModels.kt").read_text(encoding="utf-8")
    store=(app/"data/LifeStore.kt").read_text(encoding="utf-8")
    screen=(app/"ui/LifeScreen.kt").read_text(encoding="utf-8")
    worker=(app/"ai/LifeWakeWorker.kt").read_text(encoding="utf-8")
    assert all(k in model for k in ("allowVoluntaryOvertime","LifeSavingsGoal","initiator","RMB_PER_STARCOIN = 1"))
    assert all(k in store for k in ("planVoluntaryOvertime","claimVoluntaryDecision","configureVoluntary","creditSalary"))
    assert "LifeOvertimeAutonomy(c).tick()" in worker
    assert all(k in screen for k in ("主动加班","被动加班","LifeVoluntaryDialog"))
    assert (app/"data/ChinaWorkCalendar.kt").is_file()
    print("0.38.50 Build 62116: genuine model-decided overtime; historical salary and memories intact")

if __name__=="__main__":
    if len(sys.argv)!=2:raise SystemExit("usage: apply_voluntary_overtime_03850.py PROJECT_ROOT")
    apply(Path(sys.argv[1]).resolve())
