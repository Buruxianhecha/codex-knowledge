#!/usr/bin/env python3
"""v0.38.13: package persistent long-press AI contact ordering, safely confirmed deletion."""
from pathlib import Path
from shutil import copyfile
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
src=root/"app/src/main/java/com/cleo/cleos/ui"
test=root/"app/src/test/java/com/cleo/cleos/ui"
src.mkdir(parents=True,exist_ok=True)
test.mkdir(parents=True,exist_ok=True)
# Current HubScreens_03740.kt is copied by the older apply_discover_contacts patch
# and may have additional later patches; never replace it at this stage.
hub=(src/"HubScreens.kt").read_text(encoding="utf-8")
if 'longClickLabel="管理 AI 人设"' not in hub and 'onLongClickLabel="管理 AI 人设"' not in hub:
    raise RuntimeError("contacts source was not updated with long-press controls")
if 'c.chat.stopRepliesOf(id)' not in hub or 'c.companions.delete(id)' not in hub:
    raise RuntimeError("destructive action must reuse the proven safe delete sequence")
copyfile(here/"ContactDirectoryOrder_03813.kt",src/"ContactDirectoryOrder.kt")
copyfile(here/"ContactDirectoryOrderTest_03813.kt",test/"ContactDirectoryOrderTest.kt")
gradle=root/"app/build.gradle.kts"
s=gradle.read_text(encoding="utf-8")
for before,after in [('versionName = "0.38.12"','versionName = "0.38.13"'),
                    ('versionCode = 62078','versionCode = 62079')]:
    if s.count(before)!=1: raise RuntimeError(f"version source mismatch: {before}")
    s=s.replace(before,after,1)
gradle.write_text(s,encoding="utf-8")
print("v0.38.13/62079: long press AI role reorder/pin/delete with local persistence and safe delete checks")
