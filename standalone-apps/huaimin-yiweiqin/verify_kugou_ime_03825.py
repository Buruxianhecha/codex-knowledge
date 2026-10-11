#!/usr/bin/env python3
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
j=r/"app/src/main/java/com/cleo/cleos"
for f,p in [
 ("KugouRuntimeRules_03825.kt",j/"ai/KugouRuntimeRules.kt"),
 ("KugouMusicAccessibilityService_03825.kt",j/"KugouMusicAccessibilityService.kt"),
 ("KugouRuntimeRulesTest_03825.kt",r/"app/src/test/java/com/cleo/cleos/ai/KugouRuntimeRulesTest.kt"),
]:
    assert (here/f).read_bytes()==p.read_bytes(),p
assert 'versionName = "0.38.25"' in (r/"app/build.gradle.kts").read_text()
assert 'versionCode = 62091' in (r/"app/build.gradle.kts").read_text()
print("PASS: ime foreground root logic and version")
