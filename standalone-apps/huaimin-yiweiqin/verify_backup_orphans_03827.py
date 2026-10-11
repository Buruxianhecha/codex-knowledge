#!/usr/bin/env python3
"""Inspect assembled app to ensure non-destructive orphan chat handling."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
for src,dst in [
    ("BackupArchiveGuard_03827.kt","app/src/main/java/com/cleo/cleos/data/BackupArchiveGuard.kt"),
    ("BackupArchiveGuardTest_03827.kt","app/src/test/java/com/cleo/cleos/data/BackupArchiveGuardTest.kt"),
]:
    assert (here/src).read_bytes()==(r/dst).read_bytes(),dst
s=(r/"app/src/main/java/com/cleo/cleos/data/BackupService.kt").read_text()
assert s.count("BackupArchiveGuard.cleanEmptyOrphanConversations") == 2
assert "val d = original.copy(conversations = conversations)" in s
assert "db.conversations().insertAll(d.conversations)" in s
assert "skippedEmptyConversations = skippedOnRestore" in s
assert "skippedEmptyConversations = skippedOnExport" in s
assert "withConfigurationRollback(" in s
assert "db.withTransaction {" in s
assert "BackupArchiveGuard.validateRows" in s
gradle=(r/"app/build.gradle.kts").read_text()
assert 'versionName = "0.38.27"' in gradle
assert 'versionCode = 62093' in gradle
print("PASS: 0.38.27 backup orphan cleaning, full data path and rollback preserved")
