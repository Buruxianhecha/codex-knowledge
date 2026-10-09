#!/usr/bin/env python3
"""Extend the existing portable backup rather than leaving Moments out of restore/undo."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
p=root/"app/src/main/java/com/cleo/cleos/data/BackupService.kt"
code=p.read_text(encoding="utf-8")
def replace(old,new,reason):
    global code
    n=code.count(old)
    if n!=1: raise RuntimeError(f"{reason}: expected one anchor; found {n}")
    code=code.replace(old,new,1)

replace(
'''    val groupMembers: List<ConversationMemberEntity> = emptyList(),
''',
'''    val groupMembers: List<ConversationMemberEntity> = emptyList(),
    /** Absent before Moments existed. Posts and comments are private local data. */
    val moments: MomentsSnapshot? = null,
''',"backups old and new decode")
replace(
'''    private val snapshot = File(context.filesDir, "backups/before-restore.zip")
''',
'''    private val snapshot = File(context.filesDir, "backups/before-restore.zip")
    private val momentsFile = File(context.filesDir, "moments-v1.json")
''',"moments local backing file")
replace(
'''            groupMembers = db.groupMembers().all(),
''',
'''            groupMembers = db.groupMembers().all(),
            moments = if (momentsFile.exists()) runCatching {
                json.decodeFromString<MomentsSnapshot>(momentsFile.readText())
            }.getOrElse { throw BackupException("朋友圈本地数据损坏，无法生成安全备份") }
            else MomentsSnapshot(),
''',"include posts in portable backup")
replace(
'''            s.displayFonts.map { it.file }).toSet()
''',
'''            s.displayFonts.map { it.file } +
            data.moments.orEmptyPhotos()).toSet()
''',"include all moments photos in ZIP")
# Extension syntax lets old snapshots skip moments without importing anything.
insert='''
/** Media belonging to actual Moments posts must travel together with the backup. */
private fun MomentsSnapshot?.orEmptyPhotos(): List<String> =
    this?.posts?.flatMap { it.photos } ?: emptyList()

'''
before='/** Recordings are named voice_…, among the pictures (VoiceRecorder). */'
replace(before,insert+before,"backup media extension")
replace(
'''            BackupArchiveGuard.validateRows(roleIds, d.conversations, d.groupMembers, d.messages)
''',
'''            BackupArchiveGuard.validateRows(roleIds, d.conversations, d.groupMembers, d.messages)
            val incomingMoments = d.moments ?: MomentsSnapshot()
            if (incomingMoments.version != 1 || incomingMoments.posts.size > 50_000 ||
                incomingMoments.posts.map { it.id }.distinct().size != incomingMoments.posts.size)
                throw BackupException("朋友圈备份数据格式无效")
            incomingMoments.posts.forEach { post ->
                try {
                    MomentsRules.validatePost(post.text, post.photos.size)
                    require(post.photos.all(MomentsRules::validImageName))
                    require(post.comments.size <= 10000)
                    post.comments.forEach { MomentsRules.validateReply(it.text) }
                } catch (e: Exception) {
                    throw BackupException("朋友圈动态或评论数据不合法，已停止恢复")
                }
            }
''',"validate backup before snapshot and overwrite")
# Before modifying database, verify that every photo named by the moment actually comes
# in the archive; existing images may survive older backup, but don't trust them blindly.
replace(
'''            val bs = d.settings
''',
'''            incomingMoments.posts.flatMap { it.photos }.forEach { name ->
                if (!images.file(name).exists()) throw BackupException("朋友圈照片缺失，已停止恢复")
            }
            val bs = d.settings
''',"prevent broken photos after restore")
replace(
'''            return BackupSummary(
                companions.size, d.conversations.size, d.messages.size, d.diary.size, d.letters.size, d.todos.size,
''',
'''            // Existing database and credentials have now passed their transaction checks.
            // A snapshot includes Moments as well, so Undo Restore also restores these posts.
            val momentsTemporary = File(momentsFile.path + ".restore")
            try {
                momentsTemporary.writeText(json.encodeToString(incomingMoments))
                if (!momentsTemporary.renameTo(momentsFile))
                    throw BackupException("无法保存恢复后的朋友圈，仍保留了恢复前的撤销备份")
            } finally { momentsTemporary.delete() }
            return BackupSummary(
                companions.size, d.conversations.size, d.messages.size, d.diary.size, d.letters.size, d.todos.size,
''',"restore persisted moments and support undo")
p.write_text(code,encoding="utf-8")
print("Moments portable ZIP backup/restore/undo: posts, AI comments and associated local photos included")
