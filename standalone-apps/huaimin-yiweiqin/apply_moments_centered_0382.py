#!/usr/bin/env python3
"""Release bump and regression guard for centered Moments header layout (0.38.2)."""
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
p=root/"app/src/main/java/com/cleo/cleos/ui/MomentsMyProfile.kt"
text=p.read_text(encoding="utf-8")
checks=[
    'Modifier.fillMaxWidth().height(302.dp)',
    'Modifier.fillMaxWidth().height(244.dp)',
    'Modifier.align(Alignment.BottomCenter).size(116.dp)',
    '.border(3.dp,Color.White,CircleShape)',
    'horizontalAlignment=Alignment.CenterHorizontally',
    'clickable(onClick=onOpenMyTimeline)',
    'coverPicker.launch(',
    'avatarPicker.launch(',
    'onClick={aiControls=true}',
    'onClick=::editProfile',
]
for check in checks:
    assert check in text, "Missing centered header or original profile action: "+check
assert 'height(265.dp)' not in text, "Old left-aligned cover header remains"

build=root/"app/build.gradle.kts"
body=build.read_text(encoding="utf-8")
for old,new in [('versionName = "0.38.1"','versionName = "0.38.2"'),
                ('versionCode = 62067','versionCode = 62068')]:
    assert body.count(old)==1, "Version anchor changed: "+old
    body=body.replace(old,new)
build.write_text(body,encoding="utf-8")
print("0.38.2 / 62068: centered profile layout verified, cover/avatar/edit/settings preserved.")
