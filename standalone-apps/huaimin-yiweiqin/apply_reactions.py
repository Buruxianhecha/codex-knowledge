#!/usr/bin/env python3
"""Turn long-press emoji reactions into actual model input after the recall patch."""
from pathlib import Path
import shutil
import sys


def apply_reactions(root: Path):
    root = root.resolve()
    here = Path(__file__).resolve().parent
    pending = {}

    def replace(rel, old, new):
        text = pending.get(rel, (root / rel).read_text())
        if text.count(old) != 1:
            raise SystemExit(f"{rel}: expected one reaction patch target, found {text.count(old)}: {old[:100]!r}")
        pending[rel] = text.replace(old, new, 1)

    def imports(rel):
        replace(rel, "import com.cleo.cleos.data.Recalls\n", "import com.cleo.cleos.data.Recalls\nimport com.cleo.cleos.data.ReactionEvents\n")

    dao = "app/src/main/java/com/cleo/cleos/data/db/Daos.kt"
    replace(dao, '    @Query("UPDATE messages SET reactions = :reactions WHERE id = :id")', '''    @Query("SELECT MAX(createdAt) FROM messages WHERE conversationId = :conversationId AND role = 'assistant' AND error IS NULL AND call IS NULL AND content != ''")
    suspend fun lastAnsweredAt(conversationId: Long): Long?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND role = 'reaction_event' AND createdAt > :after ORDER BY createdAt, id")
    suspend fun reactionEventsAfter(conversationId: Long, after: Long): List<MessageEntity>

    @Query("UPDATE messages SET reactions = :reactions WHERE id = :id")''')

    repo = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
    imports(repo)
    replace(repo, 'db.messages().newest(conversationId, 1).none(Recalls::isEvent)',
        'db.messages().newest(conversationId, 1).none { Recalls.isEvent(it) || ReactionEvents.isEvent(it) }')
    replace(repo, 'userMessage || patOnTa || Recalls.isEvent(m)',
        'userMessage || patOnTa || Recalls.isEvent(m) || ReactionEvents.isEvent(m)')
    replace(repo, 'm.role == "user" || Recalls.isEvent(m) || (m.role == "pat"',
        'm.role == "user" || Recalls.isEvent(m) || ReactionEvents.isEvent(m) || (m.role == "pat"')
    replace(repo, '''    /**
     * Puts [emoji] on the TA's message [messageId], or takes it off when it is on. Nothing is
     * answered: the TA hears of it with the person's next message (Prompt), the way a reaction in
     * a chat app is seen without being a message of its own.
     */
    fun react(messageId: Long, emoji: String) {
        scope.launch {
            reacting.withLock {
                val m = db.messages().get(messageId)?.takeIf { it.role == "assistant" } ?: return@withLock
                val next = MessageReactions.toggle(MessageReactions.decode(m.reactions), emoji, stamp())
                db.messages().setReactions(messageId, MessageReactions.encode(next))
            }
        }
    }''', '''    /** A new menu emoji is a real user turn; removing it does not request another reply. */
    fun react(messageId: Long, emoji: String) {
        scope.launch {
            var notify: Long? = null
            reacting.withLock {
                db.withTransaction {
                    val m = db.messages().get(messageId) ?: return@withTransaction
                    val at = stamp()
                    val change = ReactionEvents.change(m, emoji, at) ?: return@withTransaction
                    // After reopening the app, removing a queued event must not replay old user messages.
                    answeredUpTo.putIfAbsent(m.conversationId, db.messages().lastAnsweredAt(m.conversationId) ?: Long.MIN_VALUE)
                    db.messages().setReactions(messageId, change.reactions)
                    if (change.event != null) {
                        db.messages().insert(change.event)
                        db.conversations().touch(m.conversationId, at)
                        notify = m.conversationId
                    } else {
                        val through = answeredUpTo[m.conversationId] ?: Long.MIN_VALUE
                        val pending = db.messages().reactionEventsAfter(m.conversationId, through)
                        for (id in ReactionEvents.pendingIds(pending, m.conversationId, messageId, emoji, through)) {
                            db.messages().delete(id)
                        }
                    }
                }
            }
            notify?.let { answerSoon(it) }
        }
    }''')

    prompt = "app/src/main/java/com/cleo/cleos/ai/Prompt.kt"
    imports(prompt)
    replace(prompt, '        add(Recalls.RULES)', '        add(Recalls.RULES)\n        add(ReactionEvents.RULES)')
    replace(prompt, '        val out = HashMap<Long, MutableList<String>>()',
        '        val out = HashMap<Long, MutableList<String>>()\n        val covered = ReactionEvents.covered(history)')
    replace(prompt, '            val list = MessageReactions.decode(m.reactions)',
        '            val list = MessageReactions.decode(m.reactions).filterNot { Triple(m.id, it.emoji, it.at) in covered }')
    replace(prompt, '        Recalls.EVENT -> Recalls.forModel(content)?.let { ApiMessage("user", it) }',
        '        Recalls.EVENT -> Recalls.forModel(content)?.let { ApiMessage("user", it) }\n        ReactionEvents.EVENT -> ReactionEvents.forModel(content)?.let { ApiMessage("user", it) }')

    recap = "app/src/main/java/com/cleo/cleos/ai/Recap.kt"
    imports(recap)
    replace(recap, '&& !Recalls.isEvent(live[end])', '&& !Recalls.isEvent(live[end]) && !ReactionEvents.isEvent(live[end])')
    replace(recap, '&& !Recalls.isEvent(live[stop])', '&& !Recalls.isEvent(live[stop]) && !ReactionEvents.isEvent(live[stop])')
    replace(recap, '            m.role == Recalls.EVENT -> Recalls.describe(m.content)?.let { "（$it）" }',
        '            m.role == Recalls.EVENT -> Recalls.describe(m.content)?.let { "（$it）" }\n            m.role == ReactionEvents.EVENT -> ReactionEvents.describe(m.content)?.let { "（$it）" }')

    screen = "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
    imports(screen)
    replace(screen, 'private fun MessageEntity.silent() = role == Recalls.EVENT ||',
        'private fun MessageEntity.silent() = role == Recalls.EVENT || role == ReactionEvents.EVENT ||')
    replace(screen, 'messages.indexOfLast { it.role != "pat" && it.role != Recalls.EVENT }',
        'messages.indexOfLast { it.role != "pat" && it.role != Recalls.EVENT && it.role != ReactionEvents.EVENT }')

    model = "app/src/main/java/com/cleo/cleos/data/MessageReactions.kt"
    replace(model, ''' * TA hears of it in the person's first message after that (Prompt): it happened between the two,
 * and a reaction on its own doesn't ask for an answer.''', ''' * TA hears of a new reaction as its own turn (ReactionEvents), without waiting for a text.
 * Older backups without such events keep Prompt's legacy next-message fallback.''')

    for rel, text in pending.items():
        (root / rel).write_text(text)
    for name, folder in [("ReactionEvents.kt", "main/java/com/cleo/cleos/data"),
                         ("ReactionEventsTest.kt", "test/java/com/cleo/cleos/data"),
                         ("ReactionPromptTest.kt", "test/java/com/cleo/cleos/ai")]:
        dest = root / "app/src" / folder / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(here / "src" / name, dest)
    print("小表情独立事件、AI 感知及主动回应补丁已应用。")


if __name__ == "__main__":
    apply_reactions(Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd())
