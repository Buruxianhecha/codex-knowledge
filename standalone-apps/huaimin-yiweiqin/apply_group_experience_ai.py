#!/usr/bin/env python3
"""v0.37.23 group replies, safe message edits/retries, shared-memory retrieval, usage caps."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
def replace(path,before,after,expected=1):
    p=root/path; s=p.read_text(encoding="utf-8")
    n=s.count(before)
    if n!=expected: raise SystemExit(f"{path}: expected {expected}, got {n}: {before[:120]!r}")
    p.write_text(s.replace(before,after),encoding="utf-8")
base="app/src/main/java/com/cleo/cleos/"
chat=base+"ai/ChatRepository.kt"

# Group edit continuation copies the exact group membership; otherwise an edited group would
# silently turn into a one-person conversation. Existing edit prefix, attachment and ID remaps stay.
replace(chat,
'''                            val newId = db.conversations().insert(MessageEdits.copyConversation(source, expected, ids, at))
                            for (row in snapshot) {''',
'''                            val newId = db.conversations().insert(MessageEdits.copyConversation(source, expected, ids, at))
                            if (source.isGroup) {
                                db.groupMembers().insertAll(
                                    db.groupMembers().forConversation(originalId).map { it.copy(conversationId = newId) },
                                )
                            }
                            for (row in snapshot) {''')

# Preserve the original speaker on retry, never regenerate the whole group.
replace(chat,
'''    fun retry(conversationId: Long, assistantMessageId: Long) {
        start(conversationId) {
            db.messages().delete(assistantMessageId)
            reply(conversationId)
        }
    }''',
'''    fun retry(conversationId: Long, assistantMessageId: Long) {
        start(conversationId) {
            val row = db.messages().get(assistantMessageId) ?: return@start
            if (row.conversationId != conversationId || row.role != "assistant") return@start
            val convo = db.conversations().get(conversationId) ?: return@start
            db.messages().delete(assistantMessageId)
            if (!convo.isGroup) {
                reply(conversationId)
            } else {
                val members = db.groupMembers().idsFor(conversationId).mapNotNull { companions.get(it) }
                val speaker = members.firstOrNull { it.id == row.senderCompanionId } ?: return@start
                val history = Recap.sent(recaps.live(convo), settings.current().historySize)
                val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }
                val shaped = GroupChats.historyFor(history, speaker.id, names, convo.companionId)
                val query = history.lastOrNull { it.role == "user" }?.content.orEmpty()
                if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) != 0) {
                    groupTurn(conversationId, speaker, members, shaped, worldContext(conversationId, query, settings.current()), targeted = true)
                }
            }
        }
    }''')

replace(chat,
'''    private suspend fun worldContext(conversationId: Long, latestText: String, s: AppSettings): String? {
        val all = db.companions().all()
        val named = all.filter { ta -> ta.name.trim().takeIf { it.isNotEmpty() }?.let { latestText.contains(it) } == true }.take(3)
        val rows = buildList {
            for (ta in named) addAll(db.messages().sharedForCompanion(conversationId, ta.id, 16))
            addAll(db.messages().sharedRecent(conversationId, 14))
        }
        return GroupChats.sharedContext(rows, all, s.userName)
    }''',
'''    private suspend fun worldContext(conversationId: Long, latestText: String, s: AppSettings): String? {
        val owner = db.conversations().get(conversationId)
        if (owner?.isGroup == true && !owner.groupShareOutside) return null
        val all = db.companions().all()
        val named = all.filter { ta -> ta.name.trim().takeIf { it.isNotEmpty() }?.let { latestText.contains(it) } == true }.take(3)
        val keywords = latestText
            .split(' ', '，', '。', '！', '？', '、', ':', '：', ',', '.', '!', '?', '@', '＠')
            .map { it.trim() }
            .filter { it.length in 2..22 && it !in setOf("你们", "他们", "我们", "什么", "今天", "昨天", "知道") }
            .take(3)
        val rows = buildList {
            for (term in keywords) {
                val safe = term.replace("%", "").replace("_", "")
                addAll(db.messages().sharedMatches(conversationId, "%$safe%", 12))
            }
            for (ta in named) addAll(db.messages().sharedForCompanion(conversationId, ta.id, 12))
            addAll(db.messages().sharedRecent(conversationId, 8))
        }
        return GroupChats.sharedContext(rows, all, s.userName)
    }''')

replace(chat,
'''        val naturalOrder = members.drop(start) + members.take(start)
        val candidates = when {
            patTarget != null -> members.filter { it.id == patTarget }
            mentions.isNotEmpty() -> mentions
            else -> naturalOrder.take(GroupChats.MAX_OPPORTUNITIES)
        }
        var said = 0
        for (ta in candidates.take(GroupChats.MAX_MEMBERS)) {
            currentCoroutineContext().ensureActive()
            if (said >= GroupChats.MAX_REPLIES) break''',
'''        val naturalOrder = members.drop(start) + members.take(start)
        val muted = conversation.groupMutedIds.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        val eligible = members.filterNot { it.id in muted }
        val candidates = when {
            patTarget != null -> eligible.filter { it.id == patTarget }
            mentions.isNotEmpty() -> mentions.filter { it.id !in muted }
            conversation.groupMode == 2 -> emptyList()
            conversation.groupMode == 1 -> eligible
            else -> naturalOrder.filter { it.id !in muted }.take(GroupChats.MAX_OPPORTUNITIES)
        }
        var said = 0
        for (ta in candidates.take(GroupChats.MAX_MEMBERS)) {
            currentCoroutineContext().ensureActive()
            if (said >= conversation.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)) break
            // Claim before sending network traffic. This is per-group, per-local-day, across restarts.
            if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) == 0) break''')

replace(chat,
'''if (groupTurn(conversationId, ta, members, shaped, context, targeted = patTarget != null || mentions.isNotEmpty())) said++''',
'''if (groupTurn(conversationId, ta, members, shaped, context, targeted = patTarget != null || mentions.isNotEmpty() || conversation.groupMode == 1)) said++''')

# Explicit next-group-turn is user controlled, not an infinite self-reply loop.
replace(chat,
'''    /** One natural group turn: every member sees the prior member's just-stored message. */
    private suspend fun groupReply''',
'''    /** Allow one deliberate continuation; anti-spam: at most once every 45 seconds per group. */
    private val groupContinuationAt = ConcurrentHashMap<Long, Long>()

    fun continueGroup(conversationId: Long) {
        scope.launch {
            val convo = db.conversations().get(conversationId) ?: return@launch
            if (!convo.isGroup || convo.groupMode == 2) return@launch
            val now = System.currentTimeMillis()
            val previous = groupContinuationAt[conversationId] ?: 0L
            if (now - previous < 45_000) return@launch
            groupContinuationAt[conversationId] = now
            start(conversationId) { groupReply(conversationId, convo) }
        }
    }

    /** One natural group turn: every member sees the prior member's just-stored message. */
    private suspend fun groupReply''')

# Group announcement influences the group members; don't leak other groups' announcements.
replace(chat,
'''        val groupRule = GroupChats.turnInstruction(ta, members, targeted)
        val extra = listOfNotNull(shared, groupRule).joinToString("\\n\\n")''',
'''        val groupRule = GroupChats.turnInstruction(ta, members, targeted)
        val groupOptions = db.conversations().get(conversationId)
        val announcement = groupOptions?.groupAnnouncement?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "这段群公告由用户填写，仅供本群参与者参考：$it" }
        val extra = listOfNotNull(shared, announcement, groupRule).joinToString("\\n\\n")''')

# ChatModel is still in full control of each character's decision to SKIP in natural mode.
print("0.37.23 group reply limits, privacy-bound memory, editing and targeted retry applied")
