#!/usr/bin/env python3
"""Give the unchanged 0.37.10 source an independent beta installation identity."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
gradle = root / "app/build.gradle.kts"
strings = root / "app/src/main/res/values/strings.xml"
g = gradle.read_text(encoding="utf-8")
s = strings.read_text(encoding="utf-8")
old_id = 'applicationId = "com.lin.huaimin"'
old_name = '<string name="app_name">怀民亦未寝</string>'
assert g.count(old_id) == 1, "unexpected application identity"
assert s.count(old_name) == 1, "unexpected app label"
assert 'versionName = "0.37.10"' in g, "beta must stay on the tested 0.37.10 source"
assert 'versionCode = 62027' in g, "beta versionCode must stay unchanged"
gradle.write_text(g.replace(old_id, 'applicationId = "com.lin.huaimin.beta"', 1), encoding="utf-8")
strings.write_text(s.replace(old_name, '<string name="app_name">怀民亦未寝 beta版</string>', 1), encoding="utf-8")
print("Independent beta identity applied; 0.37.10 functions and backup format are unchanged.")
