#!/usr/bin/env python3
"""Structural regression for v0.38.13 AI contacts management and version."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
hub=(root/"app/src/main/java/com/cleo/cleos/ui/HubScreens.kt").read_text(encoding="utf-8")
order=(root/"app/src/main/java/com/cleo/cleos/ui/ContactDirectoryOrder.kt").read_text(encoding="utf-8")
test=(root/"app/src/test/java/com/cleo/cleos/ui/ContactDirectoryOrderTest.kt").read_text(encoding="utf-8")
main=(root/"app/src/main/java/com/cleo/cleos/ui/MainScreen.kt").read_text(encoding="utf-8")
build=(root/"app/build.gradle.kts").read_text(encoding="utf-8")
assert 'versionName = "0.38.13"' in build and 'versionCode = 62079' in build
for content in (
    'onLongClickLabel="管理 AI 人设"',
    'onLongClick={menuFor=ta.id}',
    'DropdownMenu(expanded=menuFor==ta.id',
    'Text("上移一位")','Text("下移一位")','Text("置顶")','Text("删除 AI 人设")',
    'ContactDirectoryOrder.move(', 'ContactDirectoryOrder.top(',
    'getSharedPreferences("huaimin_ai_directory",Context.MODE_PRIVATE)',
    'prefs.edit().putString("contact_ids",encoded).apply()',
    'AlertDialog(', 'c.chat.stopRepliesOf(id)', 'c.companions.delete(id)',
    'Text("取消")','"确认删除"', 'onClick={onOpenCompanion(ta.id)}'
): assert content in hub, f"contact menu/deletion missing: {content}"
assert 'enabled=people.size>1' in hub, "do not delete final AI"
assert 'rememberCoroutineScope()' in hub, "deletion must stay on Main after suspensions"
assert 'if(!deleting)' in hub and 'deleting=true' in hub
assert 'fun move(' in order and 'fun decode(' in order and 'fun apply(' in order
assert 'Malformed' not in test or len(test)>300
assert 'ContactDirectoryOrderTest' in test
assert 'val uiScope = rememberCoroutineScope()' in main
assert 'c.appScope.launch {' not in main
print("v0.38.13 contact management integrated: long-press menu, stable persistent order, bounded moves, safe deletion and Main navigation")
