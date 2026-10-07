#!/usr/bin/env python3
"""Apply message recall after the pinned upstream backports have finished."""
from pathlib import Path
import shutil
import sys


def apply_recall(root: Path):
    root = root.resolve()
    here = Path(__file__).resolve().parent
    pending = {}

    def replace(rel, old, new):
        path = root / rel
        text = pending.get(rel, path.read_text())
        if text.count(old) != 1:
            raise SystemExit(f"{rel}: expected one recall patch target, found {text.count(old)}: {old[:90]!r}")
        pending[rel] = text.replace(old, new, 1)

    def imports(rel):
        replace(rel, "import com.cleo.cleos.data.Pats\n", "import com.cleo.cleos.data.Pats\nimport com.cleo.cleos.data.Recalls\n")

    dao = "app/src/main/java/com/cleo/cleos/data/db/Daos.kt"
    replace(dao, '    @Query("UPDATE conversations SET title = :title WHERE id = :id")', '''    @Query("UPDATE conversations SET recap = NULL, recapUntilAt = NULL, recapUntilId = NULL WHERE id = :id")
    suspend fun clearRecap(id: Long)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")''')
    replace(dao, '    @Query("UPDATE messages SET content = :content WHERE id = :id")', '    @Query("UPDATE messages SET content = :content WHERE id = :id AND role != \'recalled\'")')
    replace(dao, '    @Query("UPDATE messages SET error = :error WHERE id = :id")', '    @Query("UPDATE messages SET error = :error WHERE id = :id AND role != \'recalled\'")')
    replace(dao, '    @Insert\n    suspend fun insert(message: MessageEntity): Long', '''    @androidx.room.Update
    suspend fun updateForRecall(message: MessageEntity): Int

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND quote IS NOT NULL")
    suspend fun quotesIn(conversationId: Long): List<MessageEntity>

    @Query("UPDATE messages SET quote = :quote WHERE id = :id")
    suspend fun setQuote(id: Long, quote: String?)

    @Insert
    suspend fun insert(message: MessageEntity): Long''')

    repo = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
    imports(repo)
    replace(repo, 'package com.cleo.cleos.ai\n', 'package com.cleo.cleos.ai\n\nimport androidx.room.withTransaction\n')
    replace(repo, '    private val jobs = ConcurrentHashMap<Long, Job>()', '''    private val jobs = ConcurrentHashMap<Long, Job>()
    private val recalling = ConcurrentHashMap.newKeySet<Long>()
    private val recallCoordinator by lazy {
        RecallCoordinator(
            pause = { id -> synchronized(lock) { recalling += id } },
            stop = { id -> jobs[id]?.cancelAndJoin() },
            resume = { id ->
                synchronized(lock) { recalling -= id }
                answerSoon(id)
            },
        )
    }''')
    replace(repo, 'fun busy(conversationId: Long): Boolean = jobs[conversationId]?.isActive == true || calling == conversationId',
        'fun busy(conversationId: Long): Boolean = conversationId in recalling || jobs[conversationId]?.isActive == true || calling == conversationId')
    replace(repo, '            val held = conversationId in typingIn || (holds[conversationId] ?: 0) > 0', '''            val held = (conversationId in typingIn || (holds[conversationId] ?: 0) > 0) &&
                db.messages().newest(conversationId, 1).none(Recalls::isEvent)''')
    replace(repo, 'm.createdAt > upTo && (userMessage || patOnTa)', 'm.createdAt > upTo && (userMessage || patOnTa || Recalls.isEvent(m))')
    replace(repo, '        val lastInput = history.lastOrNull { m ->\n            m.role == "user" ||',
        '        val lastInput = history.lastOrNull { m ->\n            m.role == "user" || Recalls.isEvent(m) ||')
    replace(repo, '            db.messages().setContent(id, text)\n            return true', '''            if (db.messages().get(id)?.role != "user") return false
            db.messages().setContent(id, text)
            return true''')
    replace(repo, '    fun deleteMessage(id: Long) {', '''    /** Withdraw only this conversation's ordinary user messages, then answer the event. */
    fun recallMessage(conversationId: Long, id: Long) {
        scope.launch {
            recallCoordinator.perform(conversationId,
                eligible = { db.messages().get(id)?.let { it.conversationId == conversationId && Recalls.canRecall(it) } == true },
            ) {
                try {
                    recaps.withoutFolding(conversationId) {
                        db.withTransaction {
                            val conversation = db.conversations().get(conversationId) ?: return@withTransaction
                            val current = db.messages().get(id)
                                ?.takeIf { it.conversationId == conversationId && Recalls.canRecall(it) } ?: return@withTransaction
                            val at = stamp()
                            db.messages().updateForRecall(Recalls.withdrawn(current))
                            for (quoted in db.messages().quotesIn(conversationId)) {
                                val redacted = Recalls.redactQuote(quoted.quote, current.id)
                                if (redacted != quoted.quote) db.messages().setQuote(quoted.id, redacted)
                            }
                            if (Recalls.wasFolded(current, conversation)) db.conversations().clearRecap(conversationId)
                            db.messages().insert(Recalls.event(current, at))
                            db.conversations().touch(conversationId, at)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (db.conversations().get(conversationId) != null) {
                        db.messages().insert(MessageEntity(conversationId = conversationId, role = "note", content = "",
                            note = "撤回失败，请再试一次。", createdAt = stamp()))
                    }
                }
            }
        }
    }

    fun deleteMessage(id: Long) {''')

    recap = "app/src/main/java/com/cleo/cleos/ai/Recap.kt"
    replace(recap, 'import com.cleo.cleos.data.MessageQuotes\n', 'import com.cleo.cleos.data.MessageQuotes\nimport com.cleo.cleos.data.Recalls\n')
    replace(recap, 'import kotlinx.coroutines.CoroutineScope\n', 'import kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.CoroutineStart\nimport kotlinx.coroutines.Job\nimport kotlinx.coroutines.cancelAndJoin\n')
    replace(recap, 'live[end].role != "user"', 'live[end].role != "user" && !Recalls.isEvent(live[end])')
    replace(recap, 'live[stop].role != "user"', 'live[stop].role != "user" && !Recalls.isEvent(live[stop])')
    replace(recap, '            m.error != null -> null', '''            m.error != null -> null
            m.role == Recalls.WITHDRAWN -> null
            m.role == Recalls.EVENT -> Recalls.describe(m.content)?.let { "（$it）" }''')
    replace(recap, '    private val folding = ConcurrentHashMap.newKeySet<Long>()', '''    private val folding = ConcurrentHashMap<Long, Job>()
    private val foldLock = Any()
    private val paused = HashSet<Long>()

    /** Prevent a fold captured before withdrawal from putting its original words back. */
    suspend fun <T> withoutFolding(conversationId: Long, block: suspend () -> T): T {
        val job = synchronized(foldLock) { paused += conversationId; folding[conversationId] }
        try {
            job?.cancelAndJoin()
            return block()
        } finally {
            synchronized(foldLock) { paused -= conversationId }
        }
    }''')
    replace(recap, '''        if (!folding.add(conversationId)) return
        scope.launch {
            try {
                fold(conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "recap: ${e.message}")
            } finally {
                folding.remove(conversationId)
            }
        }''', '''        synchronized(foldLock) {
            if (conversationId in paused || folding[conversationId]?.isActive == true) return
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    fold(conversationId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "recap: ${e.message}")
                }
            }
            folding[conversationId] = job
            job.invokeOnCompletion { folding.remove(conversationId, job) }
            job.start()
        }''')

    prompt = "app/src/main/java/com/cleo/cleos/ai/Prompt.kt"
    imports(prompt)
    replace(prompt, '        add(FORMAT_RULE)', '        add(FORMAT_RULE)\n        add(Recalls.RULES)')
    replace(prompt, '        "assistant" -> {\n            val calls = if (withTools)',
        '        Recalls.EVENT -> Recalls.forModel(content)?.let { ApiMessage("user", it) }\n        "assistant" -> {\n            val calls = if (withTools)')

    vm = "app/src/main/java/com/cleo/cleos/ui/chat/ChatViewModel.kt"
    replace(vm, '    fun delete(messageId: Long) = c.chat.deleteMessage(messageId)', '''    fun recall(messageId: Long) {
        val conversationId = conversationId.value ?: return
        if (quoting?.id == messageId) quoting = null
        c.chat.recallMessage(conversationId, messageId)
    }

    fun delete(messageId: Long) = c.chat.deleteMessage(messageId)''')

    screen = "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
    imports(screen)
    replace(screen, 'private fun MessageEntity.silent() =\n', 'private fun MessageEntity.silent() = role == Recalls.EVENT ||\n')
    replace(screen, 'messages.indexOfLast { it.role != "pat" }', 'messages.indexOfLast { it.role != "pat" && it.role != Recalls.EVENT }')
    replace(screen, '                                m.role == "pat" -> PatLine',
        '                                m.role == Recalls.WITHDRAWN -> ToolNote(Recalls.MARKER, Icons.Rounded.Info)\n                                m.role == "pat" -> PatLine')
    replace(screen, '                                        onDelete = { vm.delete(m.id) },', '''                                        onDelete = { vm.delete(m.id) },
                                        onRecall = {
                                            if (playing != null && playing == MessageAudios.decode(m.audio)?.file) stopPlaying()
                                            vm.recall(m.id)
                                        },''')
    replace(screen, '    onDelete: () -> Unit,\n    onOpenImage: (String) -> Unit,', '    onDelete: () -> Unit,\n    onRecall: () -> Unit = {},\n    onOpenImage: (String) -> Unit,')
    replace(screen, '                    DropdownMenuItem(text = { Text("删除") }, onClick = {', '''                    if (Recalls.canRecall(message)) {
                        DropdownMenuItem(text = { Text("撤回") }, onClick = {
                            menu = false
                            onRecall()
                        })
                    }
                    DropdownMenuItem(text = { Text("删除") }, onClick = {''')

    for rel, text in pending.items():
        (root / rel).write_text(text)
    for name, directory in [("Recalls.kt", "main/java/com/cleo/cleos/data"),
                            ("RecallsTest.kt", "test/java/com/cleo/cleos/data"),
                            ("RecallPromptTest.kt", "test/java/com/cleo/cleos/ai"),
                            ("RecallCoordinator.kt", "main/java/com/cleo/cleos/ai"),
                            ("RecallCoordinatorTest.kt", "test/java/com/cleo/cleos/ai")]:
        dest = root / "app/src" / directory / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(here / "src" / name, dest)
    print("消息撤回、AI 感知及回应补丁已应用。")


if __name__ == "__main__":
    apply_recall(Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd())
