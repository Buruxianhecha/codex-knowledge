#!/usr/bin/env python3
"""Keep 0.37.38 feature set, increment build for icon-only regression hotfix."""
from pathlib import Path
import sys
gradle=Path(sys.argv[1]).resolve() / "app/build.gradle.kts"
s=gradle.read_text(encoding="utf-8")
old='versionCode = 62061'
assert s.count('versionName = "0.37.38"') == 1, "wrong release baseline"
assert s.count(old) == 1, "unexpected versionCode; prevent rollback"
gradle.write_text(s.replace(old, 'versionCode = 62062', 1), encoding="utf-8")
print("Launcher fix release: 0.37.38 (62062); no features or data schema changed")
