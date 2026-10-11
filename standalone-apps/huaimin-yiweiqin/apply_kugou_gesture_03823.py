#!/usr/bin/env python3
"""0.38.23: visible node geometry + safe GestureDescription fallback for Kugou."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
java=root/"app/src/main/java/com/cleo/cleos"
for name,dest in [
    ("QqSearchPlan.kt",java/"ai/QqSearchPlan.kt"),
    ("KugouSearchPlan.kt",java/"ai/KugouSearchPlan.kt"),
    ("KugouMusicAccessibilityService.kt",java/"KugouMusicAccessibilityService.kt"),
    ("kugou_music_accessibility.xml",root/"app/src/main/res/xml/kugou_music_accessibility.xml"),
]:
    if not dest.is_file(): raise RuntimeError("prior stage source absent: "+str(dest))
    copyfile(here/"src"/name,dest)
tests=root/"app/src/test/java/com/cleo/cleos/ai"
tests.mkdir(parents=True,exist_ok=True)
copyfile(here/"KugouGestureFallbackTest_03823.kt", tests/"KugouGestureFallbackTest.kt")
gradle=root/"app/build.gradle.kts"
s=gradle.read_text(encoding="utf-8")
for old,new in [('versionName = "0.38.22"','versionName = "0.38.23"'),
                ('versionCode = 62088','versionCode = 62089')]:
    if s.count(old)!=1: raise RuntimeError("invalid previous version "+old)
    s=s.replace(old,new,1)
gradle.write_text(s,encoding="utf-8")
print("v0.38.23/62089: gesture-enabled Kugou song tab and exact title; anti-singer guard kept")
