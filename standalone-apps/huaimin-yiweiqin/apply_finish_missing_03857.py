#!/usr/bin/env python3
from pathlib import Path
import subprocess,sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
gradle=root/"app/build.gradle.kts"
before=gradle.read_text(encoding="utf-8")
assert before.count('versionName = "0.38.56"')==1
assert before.count("versionCode = 62122")==1
for patch in ["apply_career_03857.py","apply_group_freedom_03857.py"]:
    subprocess.run([sys.executable,str(here/patch),str(root)],check=True)
tests=root/"app/src/test/java/com/cleo/cleos/data"
(tests/"LifeCareerFreedomTest.kt").write_bytes((here/"LifeCareerFreedomTest_03857.kt").read_bytes())
assert (root/"app/src/main/java/com/cleo/cleos/data/LifeModels.kt").read_text().count("customProfessionTitle")>=2
assert "claimGroupInviteAttempt(" in (root/"app/src/main/java/com/cleo/cleos/ai/GroupAiInvitations.kt").read_text()
assert "MAX_MEMBERS = Int.MAX_VALUE" in (root/"app/src/main/java/com/cleo/cleos/ai/GroupChats.kt").read_text()
gradle.write_text(before.replace('versionName = "0.38.56"','versionName = "0.38.57"')
    .replace("versionCode = 62122","versionCode = 62123"),encoding="utf-8")
print("0.38.57 Build 62123: profession name and salary can be customized; no six-person cap or daily invite lock")
