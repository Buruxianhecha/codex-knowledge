#!/usr/bin/env python3
"""Formal release: block role deletion if it would strand an existing group."""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
def rep(path,old,new):
    p=root/path;s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n != 1:raise SystemExit(f"{path}: expected 1 anchor, got {n}: {old[:120]!r}")
    p.write_text(s.replace(old,new),encoding="utf-8")
base="app/src/main/java/com/cleo/cleos/"
shutil.copyfile(here/"GroupDeletionPolicy.kt",root/base/"data/GroupDeletionPolicy.kt")
shutil.copyfile(here/"GroupDeletionPolicyTest.kt",root/"app/src/test/java/com/cleo/cleos/data/GroupDeletionPolicyTest.kt")
rep(base+"data/db/Daos.kt",
'''    @Query("SELECT companionId FROM conversation_members WHERE conversationId = :conversationId ORDER BY position, companionId")
    suspend fun idsFor(conversationId: Long): List<Long>''',
'''    @Query("SELECT companionId FROM conversation_members WHERE conversationId = :conversationId ORDER BY position, companionId")
    suspend fun idsFor(conversationId: Long): List<Long>

    @Query("SELECT conversationId FROM conversation_members WHERE companionId=:companionId")
    suspend fun groupIdsFor(companionId: Long): List<Long>''')
rep(base+"data/Companions.kt",
'''        val pictures = conversations.flatMap { db.messages().imagesIn(it) }.flatMap { MessageImages.decode(it) }.map { it.file }
        db.withTransaction {
            conversations.forEach { db.conversations().delete(it) }''',
'''        val pictures = conversations.flatMap { db.messages().imagesIn(it) }.flatMap { MessageImages.decode(it) }.map { it.file }
        db.withTransaction {
            val joinedGroups = db.groupMembers().groupIdsFor(id)
            val counts = joinedGroups.map { db.groupMembers().idsFor(it).size }
            if (!GroupDeletionPolicy.canDelete(counts)) {
                throw IllegalStateException("这个角色所在的群聊不足三位成员。请先为相关群聊添加角色，再删除。")
            }
            conversations.forEach { db.conversations().delete(it) }''')
rep(base+"ui/settings/SettingsViewModel.kt",
'''    private var deleted = false
    private val modelWriter''',
'''    private var deleted = false
    var deleteError by mutableStateOf<String?>(null)
        private set

    fun clearDeleteError() { deleteError = null }

    private val modelWriter''')
rep(base+"ui/settings/SettingsViewModel.kt",
'''    fun deleteCompanion(then: () -> Unit) {
        deleted = true
        val id = companionId
        viewModelScope.launch {
            c.chat.stopRepliesOf(id)
            c.companions.delete(id)
            then()
        }
    }''',
'''    fun deleteCompanion(then: () -> Unit) {
        deleted = true
        deleteError = null
        val id = companionId
        viewModelScope.launch {
            try {
                c.chat.stopRepliesOf(id)
                c.companions.delete(id)
                then()
            } catch (e: CancellationException) {
                deleted = false
                throw e
            } catch (e: Exception) {
                deleted = false
                deleteError = e.message ?: "删除失败，群聊和历史记录已保留"
            }
        }
    }''')
rep(base+"ui/settings/TaPages.kt",
'''    if (confirmDelete) {
        AlertDialog(''',
'''    vm.deleteError?.let { error ->
        AlertDialog(
            onDismissRequest = vm::clearDeleteError,
            title = { Text("无法删除角色") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = vm::clearDeleteError) { Text("知道了") }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(''')
print("Group participant deletion guarded against orphaning group chats")
