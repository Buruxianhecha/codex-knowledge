#!/usr/bin/env python3
"""Validate integration links before the full Android test and build, including every model-request path."""
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
base=root/'app/src/main/java/com/cleo/cleos'
required={
 'CleosApp.kt':['val life', 'lifeStudy', 'LifeWakeWorker', 'life = life'],
 'ai/ChatRepository.kt':['deferForLife','resumeLifePending','life?.context','deferGoalForLife'],
 'ai/LifeWakeWorker.kt':['WorkManager','resumeLifePending'],
 'data/LifeStore.kt':['AtomicFile','recordModelCheck','creditSalary','Mutex'],
 'data/VirtualWalletStore.kt':['creditSalary'],
 'ui/LifeScreen.kt':['生活与成长','lifeStudy.study','导入','职业'],
 'ui/HubScreens.kt':['生活与成长','onLife'],
 'ui/MainScreen.kt':['LifeScreen','onLife'],
 'ui/chat/ChatScreen.kt':['LifeChatBanner'],
 'ai/MomentsAutonomy.kt':['life.availability'],
 'data/BackupService.kt':['LifeSnapshot','WalletBook'],
}
for relative, tokens in required.items():
    source=(base/relative).read_text()
    for token in tokens:
        if token not in source:
            raise AssertionError(f'Missing integrated feature link: {relative}: {token}')
gradle=(root/'app/build.gradle.kts').read_text()
assert 'versionName = "0.38.47"' in gradle and 'versionCode = 62113' in gradle
print('Life integration links present; Android tests and Debug/Release compilation must still pass.')
