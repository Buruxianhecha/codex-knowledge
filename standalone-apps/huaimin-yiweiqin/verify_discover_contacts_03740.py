#!/usr/bin/env python3
"""Fail CI if new navigation becomes labels-only or loses an existing feature path."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
files={
 "main":root/"app/src/main/java/com/cleo/cleos/ui/MainScreen.kt",
 "hub":root/"app/src/main/java/com/cleo/cleos/ui/HubScreens.kt",
 "diary":root/"app/src/main/java/com/cleo/cleos/ui/diary/DiaryListScreen.kt",
 "todo":root/"app/src/main/java/com/cleo/cleos/ui/todo/TodoScreen.kt",
 "dao":root/"app/src/main/java/com/cleo/cleos/data/db/Daos.kt",
}
texts={key:p.read_text(encoding="utf-8") for key,p in files.items()}
checks={
 "bottom tabs renamed": ('main',['GlassTab("通讯录"','GlassTab("发现"']),
 "real pages wired": ('main',['ContactsTab(','GroupDirectoryTab(','DiscoverTab(','"diary" -> DiaryTab(','"todos" -> TodoTab(']),
 "hardware back": ('main',['BackHandler(enabled = secondPage.isNotEmpty())','visible = !keyboardOpen && secondPage.isEmpty()']),
 "contacts real database": ('hub',['c.companions.all','c.db.conversations().observeGroups()','onOpenCompanion(ta.id)']),
 "groups real navigation": ('main',['c.companions.select(group.companionId)','c.settings.setCurrentConversation(group.id)','c.chat.newGroupConversation(ids.toList())']),
 "diary not replaced": ('diary',['fun DiaryTab(','onOpenEntry(0L, secret)','DiaryFilter.Secrets','onBack: (() -> Unit)? = null']),
 "todo not replaced": ('todo',['fun TodoTab(','vm.add(input)','onBack: (() -> Unit)? = null']),
 "live all-groups query": ('dao',['fun observeGroups(): Flow<List<ConversationEntity>>']),
}
for label,(file,needles) in checks.items():
 missing=[needle for needle in needles if needle not in texts[file]]
 if missing: raise RuntimeError(f"{label}: missing {missing}")
 print("PASS",label)
if 'fun DiaryTab' in texts['hub'] or 'fun TodoTab' in texts['hub']:
 raise RuntimeError("Feature pages were accidentally duplicated or rewritten")
print("Navigation contract verified: no original diary or todo features were replaced")
