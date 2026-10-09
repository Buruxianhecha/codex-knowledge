#!/usr/bin/env python3
"""Structural acceptance: Moments must be first, working and backed up. No stub screen."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
paths={
 "hub":root/"app/src/main/java/com/cleo/cleos/ui/HubScreens.kt",
 "main":root/"app/src/main/java/com/cleo/cleos/ui/MainScreen.kt",
 "screen":root/"app/src/main/java/com/cleo/cleos/ui/MomentsScreen.kt",
 "store":root/"app/src/main/java/com/cleo/cleos/data/MomentsStore.kt",
 "backup":root/"app/src/main/java/com/cleo/cleos/data/BackupService.kt",
}
text={name:path.read_text(encoding="utf-8") for name,path in paths.items()}
h=text["hub"]
first=h.index('DirectoryRow("朋友圈"')
for old in ['DirectoryRow("日记"','DirectoryRow("待办"','DirectoryRow("信箱"','DirectoryRow("回忆"']:
    if h.index(old)<=first:raise RuntimeError("Moments must come before "+old)
checks={
 "real navigation":("main",['"moments" -> MomentsScreen(', 'onMoments = { secondPage = "moments" }']),
 "local persistence":("store",['moments-v1.json','suspend fun publish','suspend fun toggleLike','suspend fun reply','suspend fun inviteAi','suspend fun delete']),
 "real feed UI":("screen",['PickMultipleVisualMedia(9)','c.moments.publish','c.moments.reply','c.moments.inviteAi','c.moments.toggleLike','c.moments.delete','onOpenImage(file)']),
 "backup and restore":("backup",['val moments: MomentsSnapshot? = null','moments = if (momentsFile.exists())','data.moments.orEmptyPhotos()','val incomingMoments = d.moments ?: MomentsSnapshot()','momentsTemporary.renameTo(momentsFile)']),
}
for label,(source,needles) in checks.items():
    missing=[n for n in needles if n not in text[source]]
    if missing:raise RuntimeError(label+" missing "+str(missing))
    print("PASS",label)
print("Verified: Moments first, actual feature page and persistent photo backups")
