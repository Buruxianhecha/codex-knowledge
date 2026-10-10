#!/usr/bin/env python3
"""v0.38.12: keep Compose/Nav callbacks on main thread, not Default appScope."""
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
path=root/"app/src/main/java/com/cleo/cleos/ui/MainScreen.kt"
src=path.read_text(encoding="utf-8")

def once(before,after,description):
    global src
    n=src.count(before)
    if n!=1: raise RuntimeError(f"{description}: expected 1 anchor, found {n}")
    src=src.replace(before,after,1)

once('import androidx.compose.runtime.remember\n',
     'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\n',
     "Compose UI-bound coroutine import")
once('    val c = appContainer()\n',
     '''    val c = appContainer()
    // UI navigation must resume on Dispatchers.Main, including after suspending Room/DataStore work.
    // AppContainer.appScope uses Dispatchers.Default and must NEVER change Compose/Nav state.
    val uiScope = rememberCoroutineScope()
    var creatingCompanion by remember { mutableStateOf(false) }
''',
     "main-thread lifecycle scope and duplicate-create guard")

# Four callbacks are introduced by apply_discover_contacts_03740.py:
# (1) open existing companion, (2) open group, (3) create group, (4) add companion.
# The app-wide appScope stays Default for real background tasks.
old='c.appScope.launch {'
n=src.count(old)
if n!=4: raise RuntimeError(f"contacts/group UI actions: expected 4 appScope launches, found {n}")
src=src.replace(old,'uiScope.launch {')

once('''                                    onAddCompanion = {
                                        uiScope.launch {
                                            c.companions.add()
                                            onOpenSettings()
                                        }
                                    },''',
'''                                    onAddCompanion = {
                                        // Protect against double-tap creating two AI roles.
                                        if (!creatingCompanion) {
                                            creatingCompanion = true
                                            uiScope.launch {
                                                try {
                                                    c.companions.add()
                                                    // This callback navigates Compose; rememberCoroutineScope keeps Main.
                                                    onOpenSettings()
                                                } finally {
                                                    creatingCompanion = false
                                                }
                                            }
                                        }
                                    },''',
"guard add and keep onOpenSettings on Main")

path.write_text(src,encoding="utf-8")

build=root/"app/build.gradle.kts"
gradle=build.read_text(encoding="utf-8")
for a,b in [('versionName = "0.38.11"','versionName = "0.38.12"'),
            ('versionCode = 62077','versionCode = 62078')]:
    if gradle.count(a)!=1: raise RuntimeError(f"bad version anchor: {a}")
    gradle=gradle.replace(a,b,1)
build.write_text(gradle,encoding="utf-8")
print("v0.38.12 / Build 62078: UI routes moved from Dispatchers.Default to main-thread remembered scope; guarded duplicate AI creation")
