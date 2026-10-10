#!/usr/bin/env python3
"""Prevent regression of ANR/crash: navigation from appScope (Dispatchers.Default)."""
from pathlib import Path
import re
import sys

root=Path(sys.argv[1]).resolve()
src=(root/"app/src/main/java/com/cleo/cleos/ui/MainScreen.kt").read_text(encoding="utf-8")
hub=(root/"app/src/main/java/com/cleo/cleos/ui/HubScreens.kt").read_text(encoding="utf-8")
app=(root/"app/src/main/java/com/cleo/cleos/CleosApp.kt").read_text(encoding="utf-8")
build=(root/"app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionName = "0.38.13"' in build
assert 'versionCode = 62079' in build
assert 'SupervisorJob() + Dispatchers.Default' in app, "appScope remains for true background jobs"
assert 'val uiScope = rememberCoroutineScope()' in src, "navigation must use main-thread UI scope"
assert 'import androidx.compose.runtime.rememberCoroutineScope' in src
assert 'c.appScope.launch {' not in src, "Default dispatcher may never call UI navigation"
assert src.count('uiScope.launch {') == 4, "all four Contacts/Groups actions must run on Main"
assert 'val c = appContainer()\n' in src
# All four awaited callbacks must remain functional and preserve the same UI navigation.
for snippet in (
    'onOpenCompanion = { id ->\n                                        uiScope.launch {',
    'onOpenGroup = { group ->\n                                        uiScope.launch {',
    'onCreateGroup = { ids ->\n                                        uiScope.launch {',
    'onAddCompanion = {',
    'c.companions.add()',
    'onOpenSettings()',
    'c.companions.select(id)',
    'c.companions.select(group.companionId)',
    'c.chat.newGroupConversation(ids.toList())',
    'c.settings.setCurrentConversation(group.id)',
    'secondPage = ""',
    'tab = 0'
):
    assert snippet in src, f"missing working navigation: {snippet}"
assert 'var creatingCompanion by remember { mutableStateOf(false) }' in src
assert 'if (!creatingCompanion)' in src and 'finally {\n                                                    creatingCompanion = false' in src
assert 'GlassIconButton(Icons.Rounded.PersonAdd,"创建 AI 角色",onAddCompanion,page)' in hub
assert re.search(r'onAddCompanion\s*=\s*\{[\s\S]{0,600}uiScope\.launch\s*\{[\s\S]{0,400}onOpenSettings\(\)',src), "must navigate after add from Main"
print("v0.38.12: 4 Contacts/Groups navigation paths Main-confined; no background Compose mutation, duplicate creation guarded")
