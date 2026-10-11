#!/usr/bin/env python3
"""0.38.22: fix singer-home misclick in Kugou's comprehensive results page."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
plan=root/"app/src/main/java/com/cleo/cleos/ai/KugouSearchPlan.kt"
old=plan.read_text(encoding="utf-8")
if "class KugouSearchPlan" not in old or 'val tab = nodes.firstOrNull' not in old:
    raise RuntimeError("requires the original 0.38.21 Kugou result plan")
copyfile(here/"src/KugouSearchPlan.kt",plan)
test=root/"app/src/test/java/com/cleo/cleos/ai/KugouResultSafetyTest.kt"
test.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"KugouResultSafetyTest_03822.kt",test)
gradle=root/"app/build.gradle.kts"
s=gradle.read_text(encoding="utf-8")
for before,after in [
    ('versionName = "0.38.21"','versionName = "0.38.22"'),
    ('versionCode = 62087','versionCode = 62088'),
]:
    if s.count(before)!=1: raise RuntimeError("unexpected version: "+before)
    s=s.replace(before,after,1)
gradle.write_text(s,encoding="utf-8")
print("Applied v0.38.22: tab-first Kugou song selection, singer-page guard and playback-row tests")
