#!/usr/bin/env python3
"""0.38.27: skip only safely empty unowned chat shells in backup and restore."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
for name,rel in [
    ("BackupArchiveGuard_03827.kt","app/src/main/java/com/cleo/cleos/data/BackupArchiveGuard.kt"),
    ("BackupArchiveGuardTest_03827.kt","app/src/test/java/com/cleo/cleos/data/BackupArchiveGuardTest.kt")
]:
    dest=root/rel
    assert dest.is_file(),dest
    copyfile(here/name,dest)
p=root/"app/src/main/java/com/cleo/cleos/data/BackupService.kt"
s=p.read_text(encoding="utf-8")
def edit(old,new):
    global s
    n=s.count(old)
    if n!=1: raise RuntimeError(f"BackupService patch expected one match, found {n}: {old[:125]!r}")
    s=s.replace(old,new,1)

edit("    val credentials: Int? = null,\n) {",
     "    val credentials: Int? = null,\n    val skippedEmptyConversations: Int = 0,\n) {")
edit('            (credentials?.let { "、全部配置（$it 项已保存凭据）" } ?: "")',
     '            (credentials?.let { "、全部配置（$it 项已保存凭据）" } ?: "") +\n'
     '            (if (skippedEmptyConversations > 0) "；安全跳过 $skippedEmptyConversations 段角色已删除且无消息的空会话" else "")')
edit("        val companions = db.companions().all()\n        val lead = companions.firstOrNull()",
     "        val companions = db.companions().all()\n"
     "        val exportMessages = db.messages().all()\n"
     "        val exportMembers = db.groupMembers().all()\n"
     "        val originalConversations = db.conversations().all()\n"
     "        val exportConversations = BackupArchiveGuard.cleanEmptyOrphanConversations(\n"
     "            companions.map { it.id }.ifEmpty { listOf(Companions.FIRST) },\n"
     "            originalConversations, exportMembers, exportMessages,\n"
     "        )\n"
     "        BackupArchiveGuard.validateRows(\n"
     "            companions.map { it.id }.ifEmpty { listOf(Companions.FIRST) },\n"
     "            exportConversations, exportMembers, exportMessages,\n"
     "        )\n"
     "        val skippedOnExport = originalConversations.size - exportConversations.size\n"
     "        val lead = companions.firstOrNull()")
edit("            conversations = db.conversations().all(),",
     "            conversations = exportConversations,")
edit("            messages = db.messages().all(),",
     "            messages = exportMessages,")
edit("            groupMembers = db.groupMembers().all(),",
     "            groupMembers = exportMembers,")
edit("            written, voices, stickers, credentials = configuration.secrets.size,",
     "            written, voices, stickers, credentials = configuration.secrets.size,\n"
     "            skippedEmptyConversations = skippedOnExport,")
edit('            val d = data ?: throw BackupException("这不是怀民亦未寝的备份文件")\n'
     '            if (d.format != BackupFile.FORMAT) throw BackupException("这不是怀民亦未寝的备份文件")\n'
     '            if (d.version > BackupFile.VERSION) throw BackupException("这份备份来自更新版本的怀民亦未寝，先更新 App 再恢复")\n'
     '            // Validate graph before writing a snapshot, copying media, or modifying Room.\n'
     '            val roleIds = d.companions.map { it.id }.ifEmpty { listOf(Companions.FIRST) }\n'
     '            BackupArchiveGuard.validateRows(roleIds, d.conversations, d.groupMembers, d.messages)',
     '            val original = data ?: throw BackupException("这不是怀民亦未寝的备份文件")\n'
     '            if (original.format != BackupFile.FORMAT) throw BackupException("这不是怀民亦未寝的备份文件")\n'
     '            if (original.version > BackupFile.VERSION) throw BackupException("这份备份来自更新版本的怀民亦未寝，先更新 App 再恢复")\n'
     '            // Only discard obsolete single-chat shells with no messages,\n'
     '            // members or pin. Any real user content still blocks restoration.\n'
     '            val roleIds = original.companions.map { it.id }.ifEmpty { listOf(Companions.FIRST) }\n'
     '            val conversations = BackupArchiveGuard.cleanEmptyOrphanConversations(\n'
     '                roleIds, original.conversations, original.groupMembers, original.messages,\n'
     '            )\n'
     '            val skippedOnRestore = original.conversations.size - conversations.size\n'
     '            val d = original.copy(conversations = conversations)\n'
     '            BackupArchiveGuard.validateRows(roleIds, d.conversations, d.groupMembers, d.messages)')
edit('                pictures.size - stickers.size, stickers = stickers.size, credentials = fullConfiguration?.secrets?.size,',
     '                pictures.size - stickers.size, stickers = stickers.size, credentials = fullConfiguration?.secrets?.size,\n'
     '                skippedEmptyConversations = skippedOnRestore,')
p.write_text(s,encoding="utf-8")
gradle=root/"app/build.gradle.kts"
s=gradle.read_text()
for old,new in [('versionName = "0.38.26"','versionName = "0.38.27"'),
                ('versionCode = 62092','versionCode = 62093')]:
    assert s.count(old)==1,old
    s=s.replace(old,new,1)
gradle.write_text(s)
print("0.38.27: empty missing-TA chat cleanup on export/import, guard nonempty content, report skipped count")
