#!/usr/bin/env python3
from pathlib import Path
import shutil, sys

ROOT = Path(sys.argv[1]).resolve()
HERE = Path(__file__).resolve().parent

def rep(rel, old, new):
    p = ROOT / rel
    s = p.read_text(encoding='utf-8')
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{rel}: expected one match, got {n}: {old[:120]!r}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

def cp(src_name, rel):
    src = HERE / src_name
    dst = ROOT / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)

companions = 'app/src/main/java/com/cleo/cleos/data/Companions.kt'
rep(companions,
'''        val conversations = db.conversations().idsFor(id)
        val pictures = conversations.flatMap { db.messages().imagesIn(it) }.flatMap { MessageImages.decode(it) }.map { it.file }
        db.withTransaction {
            conversations.forEach { db.conversations().delete(it) }''',
'''        val conversations = db.conversations().idsFor(id)
        val ownedGroups = db.conversations().groupsOwnedBy(id)
        val groupReplacements = ownedGroups.mapNotNull { group ->
            db.groupMembers().idsFor(group.id).firstOrNull { it != id }?.let { group.id to it }
        }
        val pictures = conversations.flatMap { db.messages().imagesIn(it) }.flatMap { MessageImages.decode(it) }.map { it.file }
        db.withTransaction {
            conversations.forEach { db.conversations().delete(it) }
            groupReplacements.forEach { (groupId, replacement) -> db.conversations().reassignCompanion(groupId, replacement) }''')

# Backup group membership together with the conversations it belongs to.
backup = 'app/src/main/java/com/cleo/cleos/data/BackupService.kt'
rep(backup,
'''import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.DiaryEntryEntity''',
'''import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.ConversationMemberEntity
import com.cleo.cleos.data.db.DiaryEntryEntity''')
rep(backup,
'''    /** Absent in backups from before there were stickers; their pictures are under `images/` with the rest. */
    val stickers: List<StickerEntity> = emptyList(),
) {''',
'''    /** Absent in backups from before there were stickers; their pictures are under `images/` with the rest. */
    val stickers: List<StickerEntity> = emptyList(),
    /** Absent before 0.37.22; old backups simply contain no group chats. */
    val groupMembers: List<ConversationMemberEntity> = emptyList(),
) {''')
rep(backup,
'''            stickers = db.stickers().all(),
        )''',
'''            stickers = db.stickers().all(),
            groupMembers = db.groupMembers().all(),
        )''')
rep(backup,
'''                    db.messages().clear()
                    db.conversations().clear()''',
'''                    db.messages().clear()
                    db.groupMembers().clear()
                    db.conversations().clear()''')
rep(backup,
'''                    db.companions().insertAll(companions)
                    db.conversations().insertAll(d.conversations)
                    db.messages().insertAll(d.messages)''',
'''                    db.companions().insertAll(companions)
                    db.conversations().insertAll(d.conversations)
                    db.groupMembers().insertAll(d.groupMembers)
                    db.messages().insertAll(d.messages)''')

# Chat state knows whether this is a group and who each assistant bubble belongs to.
print('apply_group_chats_data.py applied')
