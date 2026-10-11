#!/usr/bin/env python3
"""IME app-window fix following 0.38.24 without changing QQ Music."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
java=root/"app/src/main/java/com/cleo/cleos"
for f,p in [
 ("KugouRuntimeRules_03825.kt",java/"ai/KugouRuntimeRules.kt"),
 ("KugouMusicAccessibilityService_03825.kt",java/"KugouMusicAccessibilityService.kt"),
 ("KugouRuntimeRulesTest_03825.kt",root/"app/src/test/java/com/cleo/cleos/ai/KugouRuntimeRulesTest.kt"),
]:
    assert p.is_file(),p
    copyfile(here/f,p)
g=root/"app/build.gradle.kts"
s=g.read_text()
for old,new in [('versionName = "0.38.24"','versionName = "0.38.25"'),('versionCode = 62090','versionCode = 62091')]:
    assert s.count(old)==1,old
    s=s.replace(old,new,1)
g.write_text(s)
print("0.38.25: recover Kugou behind IME focus, still reject other apps")
