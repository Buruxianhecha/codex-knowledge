#!/usr/bin/env python3
"""Add own-message editing after all upstream/custom message-delivery patches."""
from pathlib import Path
import shutil
import sys


def apply_message_edit(root: Path):
    here = Path(__file__).resolve().parent
    root = root.resolve()
    pending = {}

    def replace(rel, old, new):
        path = root / rel
        text = pending.get(rel, path.read_text())
        if text.count(old) != 1:
            raise SystemExit(f"{rel}: expected one edit target, found {text.count(old)}: {old[:90]!r}")
        pending[rel] = text.replace(old, new, 1)

    dao = "app/src/main/java/com/cleo/cleos/data/db/Daos.kt"
    replace(dao, '    @Query("UPDATE conversations SET title = :title WHERE id = :id")', '''    @Update
    suspend fun updateForMessageEdit(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")''')
    replace(dao, '    @Query("SELECT * FROM messages WHERE id = :id")', '''    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND " +
        "(createdAt < :at OR (createdAt = :at AND id <= :id)) ORDER BY createdAt, id")
    suspend fun prefixForEdit(conversationId: Long, at: Long, id: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")''')

    repo = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
    replace(repo, 'import com.cleo.cleos.data.Recalls\n', 'import com.cleo.cleos.data.Recalls\nimport com.cleo.cleos.data.MessageEdits\nimport com.cleo.cleos.data.MessageEditException\n')
    replace(repo, 'import kotlinx.coroutines.currentCoroutineContext\n', 'import kotlinx.coroutines.currentCoroutineContext\nimport kotlinx.coroutines.ensureActive\n')
    replace(repo, '    fun deleteMessage(id: Long) {', '''    /** A new continuation at the edited turn, with the original conversation retained. */
    suspend fun editAndResend(expected: MessageEntity, text: String): Long {
        val originalId = expected.conversationId
        if (calling == originalId) throw MessageEditException("请先结束电话，再编辑消息。")
        var branchId: Long? = null
        val accepted = recallCoordinator.perform(originalId,
            eligible = { calling != originalId && db.messages().get(expected.id) == expected && MessageEdits.canSubmit(expected, text) },
        ) {
            recaps.withoutFolding(originalId) {
                val source = db.conversations().get(originalId) ?: throw MessageEditException("原对话已经删除。")
                val snapshot = MessageEdits.prefix(db.messages().prefixForEdit(originalId, expected.createdAt, expected.id), expected, text)
                val copied = mutableMapOf<String, String>()
                val staged = mutableListOf<String>()
                commitMessageEdit<Long>(
                    stage = {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val files = snapshot.flatMap { m -> MessageImages.decode(m.images).map { it.file } +
                                listOfNotNull(MessageAudios.decode(m.audio)?.file) }.distinct()
                            for (name in files) {
                                currentCoroutineContext().ensureActive()
                                if (java.io.File(name).name != name || name == "." || name == "..")
                                    throw MessageEditException("附件路径无效，无法重新发送。")
                                val old = images.file(name)
                                // A file already missing in the original remains unavailable, without blocking text edits.
                                if (!old.isFile) continue
                                val extension = old.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
                                val fresh = "edit-" + java.util.UUID.randomUUID().toString() + extension
                                staged += fresh
                                old.copyTo(images.file(fresh), overwrite = false)
                                copied[name] = fresh
                            }
                        }
                    },
                    commit = {
                        db.withTransaction {
                            if (calling == originalId || db.conversations().get(originalId) != source ||
                                db.messages().prefixForEdit(originalId, expected.createdAt, expected.id) != snapshot)
                                throw MessageEditException("原消息或前文已经变化，请重新打开编辑。")
                            val at = stamp()
                            val ids = mutableMapOf<Long, Long>()
                            val newId = db.conversations().insert(MessageEdits.copyConversation(source, expected, ids, at))
                            for (row in snapshot) {
                                val copy = MessageEdits.copyRow(row, newId, ids, copied)
                                val next = if (row.id == expected.id) copy.copy(content = text.trim(), error = null, createdAt = at) else copy
                                ids[row.id] = db.messages().insert(next)
                            }
                            db.conversations().updateForMessageEdit(MessageEdits.copyConversation(source, expected, ids, at).copy(id = newId))
                            newId
                        }
                    },
                    rollback = { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { images.delete(staged) } },
                    answer = { newId ->
                        branchId = newId
                        // An explicit resend answers at once, even while a separate draft is in the input.
                        if (!start(newId) { reply(newId); answerUntilQuiet(newId) }) answerSoon(newId)
                    },
                )
            }
        }
        if (!accepted) throw MessageEditException("原消息已经变化或正在通话，请重新打开编辑。")
        return branchId ?: throw MessageEditException("重新发送失败，请再试一次。")
    }

    fun deleteMessage(id: Long) {''')

    settings = "app/src/main/java/com/cleo/cleos/data/SettingsRepository.kt"
    replace(settings, '    suspend fun setCurrentConversation(id: Long?) {', '''    /** A completed edit must not take the person back after they switched conversation or TA. */
    suspend fun openEditedConversation(from: Long, to: Long, companionId: Long) {
        context.settingsStore.edit {
            if (it[Keys.currentConversation]?.toLongOrNull() == from &&
                (it[Keys.currentCompanion] ?: companionId) == companionId) {
                it[Keys.currentConversation] = to.toString()
            }
        }
    }

    suspend fun setCurrentConversation(id: Long?) {''')

    vm = "app/src/main/java/com/cleo/cleos/ui/chat/ChatViewModel.kt"
    replace(vm, 'import com.cleo.cleos.data.MessageQuotes\n', 'import com.cleo.cleos.data.MessageQuotes\nimport com.cleo.cleos.data.MessageEdits\nimport com.cleo.cleos.data.MessageEditException\n')
    replace(vm, '                    quoting = null\n                    c.chat.typing(it, false)', '''                    quoting = null
                    if (!editSending) cancelEdit()
                    c.chat.typing(it, false)''')
    replace(vm, '    fun delete(messageId: Long) = c.chat.deleteMessage(messageId)', '''    var editingMessage by mutableStateOf<MessageEntity?>(null)
        private set
    var editText by mutableStateOf("")
        private set
    var editSending by mutableStateOf(false)
        private set
    var editProblem by mutableStateOf<String?>(null)
        private set
    private var editContinuation: Long? = null

    fun edit(message: MessageEntity) {
        if (editSending || message.conversationId != conversationId.value || !MessageEdits.canEdit(message)) return
        editingMessage = message
        editContinuation = null
        editText = message.content
        editProblem = null
    }

    fun changeEdit(text: String) {
        if (editSending) return
        editText = text
        editContinuation = null
        editProblem = null
    }

    fun cancelEdit() {
        if (editSending) return
        editingMessage = null
        editContinuation = null
        editText = ""
        editProblem = null
    }

    fun resendEdited() {
        val original = editingMessage ?: return
        val replacement = editText
        if (editSending || !MessageEdits.canSubmit(original, replacement)) return
        editSending = true
        editProblem = null
        // A committed resend survives navigating away, like an ordinary sent message.
        c.appScope.launch {
            try {
                val companionId = c.db.conversations().get(original.conversationId)?.companionId
                    ?: throw MessageEditException("原对话已经删除。")
                val next = editContinuation ?: c.chat.editAndResend(original, replacement).also { editContinuation = it }
                c.settings.openEditedConversation(original.conversationId, next, companionId)
                editingMessage = null
                editText = ""
                editContinuation = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                editProblem = if (e is MessageEditException) e.message else if (editContinuation != null) "重新发送已完成，打开续聊失败。请重试，或从聊天记录打开「编辑续聊」。" else "重新发送失败，请再试一次。"
            } finally {
                editSending = false
            }
        }
    }

    fun delete(messageId: Long) = c.chat.deleteMessage(messageId)''')

    screen = "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
    replace(screen, 'import com.cleo.cleos.data.Recalls\n', 'import com.cleo.cleos.data.Recalls\nimport com.cleo.cleos.data.MessageEdits\n')
    replace(screen, '                                        onDelete = { vm.delete(m.id) },', '                                        onDelete = { vm.delete(m.id) },\n                                        onEdit = { vm.edit(m) },')
    replace(screen, '    onRecall: () -> Unit = {},', '    onRecall: () -> Unit = {},\n    onEdit: () -> Unit = {},')
    replace(screen, '                    if (Recalls.canRecall(message)) {', '''                    if (MessageEdits.canEdit(message)) {
                        DropdownMenuItem(text = { Text("编辑") }, onClick = {
                            menu = false
                            onEdit()
                        })
                    }
                    if (Recalls.canRecall(message)) {''')
    replace(screen, '    if (askVoiceSetup) {', '''    vm.editingMessage?.let { original ->
        MessageEditDialog(
            source = original, text = vm.editText, sending = vm.editSending, problem = vm.editProblem,
            onChange = vm::changeEdit, onSend = vm::resendEdited, onDismiss = vm::cancelEdit,
        )
    }

    if (askVoiceSetup) {''')

    for rel, text in pending.items():
        (root / rel).write_text(text)
    for name, directory in [
        ("MessageEdits.kt", "main/java/com/cleo/cleos/data"),
        ("MessageEditCommit.kt", "main/java/com/cleo/cleos/ai"),
        ("MessageEditDialog.kt", "main/java/com/cleo/cleos/ui/chat"),
        ("MessageEditsTest.kt", "test/java/com/cleo/cleos/data"),
        ("MessageEditCommitTest.kt", "test/java/com/cleo/cleos/ai"),
    ]:
        dest = root / "app/src" / directory / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        folder = "tests" if directory.startswith("test/") else "src"
        shutil.copyfile(here / folder / name, dest)
    print("自己消息的编辑、独立续聊、附件复制及重新回答补丁已应用。")


if __name__ == "__main__":
    apply_message_edit(Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd())
