#!/usr/bin/env python3
"""Install the life/learning feature over the exact generated v0.38.46 source. Never rebuild a simplified app."""
from pathlib import Path
import hashlib
import subprocess
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
base = root / 'app/src/main/java/com/cleo/cleos'
tests = root / 'app/src/test/java/com/cleo/cleos'
guards = {"ai/ChatRepository.kt":"0fe9b13a42f79e433f5a05f5d9b633fc86984b199a5090b29b387de7cef7e08c","CleosApp.kt":"c2e40bc6cb659c27fa7bb07544cbf7d5a881d1ad621d694f5c216004e70fe375","data/BackupService.kt":"f341babff95bee1a66b8e6419c1ade21d102a6631b8ad0f690a012ac61d7726f","ui/MainScreen.kt":"a01089ee61ddb95d573832a46f30471fb6f06d75ccdd638c4f551d53caae30de","ui/chat/ChatScreen.kt":"fdc09b38dce8c9f6f86a9d595b20aa42e1735382cbc3c98156160ac8ee635cac"}
for rel, expected in guards.items():
    target = base / rel
    if hashlib.sha256(target.read_bytes()).hexdigest() != expected:
        raise RuntimeError('v0.38.46 source does not match reviewed integration snapshot: ' + rel)

mapping = {
    'LifeModels_03847.kt': base/'data/LifeModels.kt',
    'LifeStore_03847.kt': base/'data/LifeStore.kt',
    'VirtualWalletStore_03847.kt': base/'data/VirtualWalletStore.kt',
    'ChatRepository_03847.kt': base/'ai/ChatRepository.kt',
    'CleosApp_03847.kt': base/'CleosApp.kt',
    'LifeWakeWorker_03847.kt': base/'ai/LifeWakeWorker.kt',
    'LifeStudyService_03847.kt': base/'ai/LifeStudyService.kt',
    'MomentsAutonomy_03847.kt': base/'ai/MomentsAutonomy.kt',
    'BackupService_03847.kt': base/'data/BackupService.kt',
    'LifeRulesTest_03847.kt': tests/'data/LifeRulesTest.kt',
    'LifeGateRulesTest_03847.kt': tests/'ai/LifeGateRulesTest.kt',
    'LifeStudyRulesTest_03847.kt': tests/'ai/LifeStudyRulesTest.kt',
}
optional = {'LifeStoreTest_03847.kt': tests/'data/LifeStoreTest.kt'}
for source, destination in {**mapping, **{k:v for k,v in optional.items() if (here/k).exists()}}.items():
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes((here/source).read_bytes())

subprocess.run([sys.executable, str(here/'ui_entry_03847.py'), str(root)], check=True)
gradle = root/'app/build.gradle.kts'
s = gradle.read_text()
for before, after in [('versionName = "0.38.46"', 'versionName = "0.38.47"'),
                      ('versionCode = 62112', 'versionCode = 62113')]:
    if s.count(before) != 1:
        raise RuntimeError('Unexpected app version before work/life integration: ' + before)
    s = s.replace(before, after, 1)
gradle.write_text(s)
print('v0.38.47 Build 62113: installed real work/life, wages, learning and message gate over exact v0.38.46.')
