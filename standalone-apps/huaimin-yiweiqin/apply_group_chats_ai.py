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

chat = 'app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt'
rep(chat,
'''    suspend fun newConversation(companionId: Long): Long {
        val now = System.currentTimeMillis()
        return db.conversations().insert(
            ConversationEntity(title = DEFAULT_TITLE, createdAt = now, updatedAt = now, companionId = companionId),
        )
    }
''',
'''    suspend fun newConversation(companionId: Long): Long {
        val now = System.currentTimeMillis()
        return db.conversations().insert(
            ConversationEntity(title = DEFAULT_TITLE, createdAt = now, updatedAt = now, companionId = companionId),
        )
    }

    /** A shared room with two or more existing TAs. */
    suspend fun newGroupConversation(memberIds: List<Long>): Long {
        val valid = memberIds.distinct().mapNotNull { companions.get(it) }.take(GroupChats.MAX_MEMBERS)
        require(valid.size >= 2) { "群聊至少需要两个角色" }
        val now = System.currentTimeMillis()
        val title = valid.take(3).joinToString("、") { it.name.trim().ifEmpty { "TA" } } +
            if (valid.size > 3) " 等${valid.size}人" else ""
        return db.withTransaction {
            val id = db.conversations().insert(
                ConversationEntity(title = title, createdAt = now, updatedAt = now, companionId = valid.first().id, isGroup = true),
            )
            db.groupMembers().insertAll(valid.mapIndexed { index, ta -> ConversationMemberEntity(id, ta.id, index) })
            id
        }
    }

    /** Change an existing group's members without deleting any historical messages. */
    suspend fun updateGroupConversationMembers(conversationId: Long, memberIds: List<Long>) {
        val group = db.conversations().get(conversationId) ?: return
        require(group.isGroup) { "这不是群聊" }
        val valid = memberIds.distinct().mapNotNull { companions.get(it) }.take(GroupChats.MAX_MEMBERS)
        require(valid.size >= 2) { "群聊至少保留两个角色" }
        db.withTransaction {
            db.groupMembers().deleteFor(conversationId)
            db.groupMembers().insertAll(valid.mapIndexed { index, ta -> ConversationMemberEntity(conversationId, ta.id, index) })
            if (valid.none { it.id == group.companionId }) {
                db.conversations().reassignCompanion(conversationId, valid.first().id)
            }
        }
    }

    /** A real one-to-one chat even when the remembered screen is currently a group. */
    suspend fun singleConversation(companionId: Long): Long =
        db.conversations().latestFor(companionId)?.id ?: newConversation(companionId)
''')
# ChatRepository needs Room transaction and new entity imports if absent.
rep(chat,
'''import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.MessageEntity''',
'''import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.ConversationMemberEntity
import com.cleo.cleos.data.db.MessageEntity''')

rep(chat,
'''    suspend fun resolveConversation(remembered: Long?, companionId: Long): Long {
        if (remembered != null && db.conversations().get(remembered)?.companionId == companionId) return remembered
        return db.conversations().latestFor(companionId)?.id ?: newConversation(companionId)
    }''',
'''    suspend fun resolveConversation(remembered: Long?, companionId: Long): Long {
        if (remembered != null) {
            val saved = db.conversations().get(remembered)
            if (saved?.isGroup == true || saved?.companionId == companionId) return remembered
        }
        return db.conversations().latestFor(companionId)?.id ?: newConversation(companionId)
    }''')

# Add the shared-context helper before the regular reply.
rep(chat,
'''    private suspend fun reply(conversationId: Long) {
        val startedAt = System.currentTimeMillis()''',
'''    private suspend fun worldContext(conversationId: Long, latestText: String, s: AppSettings): String? {
        val all = db.companions().all()
        val named = all.filter { ta -> ta.name.trim().takeIf { it.isNotEmpty() }?.let { latestText.contains(it) } == true }.take(3)
        val rows = buildList {
            for (ta in named) addAll(db.messages().sharedForCompanion(conversationId, ta.id, 16))
            addAll(db.messages().sharedRecent(conversationId, 14))
        }
        return GroupChats.sharedContext(rows, all, s.userName)
    }

    /** One natural group turn: every member sees the prior member's just-stored message. */
    private suspend fun groupReply(conversationId: Long, conversation: ConversationEntity) {
        val s = settings.current()
        val members = db.groupMembers().idsFor(conversationId).mapNotNull { companions.get(it) }
        if (members.isEmpty()) return
        val firstHistory = Recap.sent(recaps.live(conversation), s.historySize)
        val lastInput = firstHistory.lastOrNull { it.role == "user" && it.note == null && it.error == null }
        lastInput?.let { m -> answeredUpTo.merge(conversationId, m.createdAt) { a, b -> maxOf(a, b) } }
        val mentions = GroupChats.mentioned(lastInput?.content.orEmpty(), members)
        val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }
        val lastSpeaker = firstHistory.lastOrNull { it.role == "assistant" }?.senderCompanionId
        val start = members.indexOfFirst { it.id == lastSpeaker }.let { if (it < 0) 0 else (it + 1) % members.size }
        val naturalOrder = members.drop(start) + members.take(start)
        val candidates = if (mentions.isNotEmpty()) mentions else naturalOrder.take(GroupChats.MAX_OPPORTUNITIES)
        var said = 0
        for (ta in candidates.take(GroupChats.MAX_MEMBERS)) {
            currentCoroutineContext().ensureActive()
            if (said >= GroupChats.MAX_REPLIES) break
            val latestConversation = db.conversations().get(conversationId) ?: return
            val raw = Recap.sent(recaps.live(latestConversation), s.historySize)
            val shaped = GroupChats.historyFor(raw, ta.id, names, conversation.companionId)
            val context = worldContext(conversationId, lastInput?.content.orEmpty(), s)
            show(StreamingReply(conversationId, "", thinking = false, activity = "${ta.name.trim().ifEmpty { "TA" }}正在输入"))
            try {
                if (groupTurn(conversationId, ta, members, shaped, context, targeted = mentions.isNotEmpty())) said++
            } finally {
                hide(conversationId)
            }
        }
    }

    private suspend fun groupTurn(
        conversationId: Long,
        ta: CompanionEntity,
        members: List<CompanionEntity>,
        history: List<MessageEntity>,
        shared: String?,
        targeted: Boolean,
    ): Boolean {
        val s = settings.current()
        val key = secrets.key(ta.apiBaseUrl)
        if (key.isNullOrBlank()) {
            withContext(NonCancellable) {
                val at = stamp()
                db.messages().insert(MessageEntity(conversationId = conversationId, role = "assistant", content = "", createdAt = at,
                    error = "${ta.name.trim().ifEmpty { "TA" }}还没有可用的 API Key。", senderCompanionId = ta.id))
                db.conversations().touch(conversationId, at)
            }
            return true
        }
        val endpoint = ApiEndpoint(ta.apiBaseUrl, key, ta.apiModel)
        val endpointKey = endpoint.chatUrl + "|" + endpoint.model
        val memories = if (ToolGroup.Memory in s.tools) db.memories().allFor(ta.id) else emptyList()
        val (stickers, _) = stickersFor(s)
        var withImages = endpointKey !in refusesImages && MessagePictures.hasImages(history, stickers)
        var thinking = ta.deepThinking && endpointKey !in refusesThinking
        val groupRule = GroupChats.turnInstruction(ta, members, targeted)
        val extra = listOfNotNull(shared, groupRule).joinToString("\\n\\n")
        fun build() = Prompt.messages(
            s, ta, history, ZonedDateTime.now(), tools = emptySet(), images = withImages, memories = memories,
            recap = null, stickers = stickers, sendStickers = false, extraContext = extra,
        )
        var messages = prepare(build())
        while (true) {
            when (val step = step(conversationId, endpoint, messages, emptyList(), mayRefuse = thinking || withImages,
                thinking = thinking, showThought = ta.deepThinking, wake = true)) {
                Step.Refused -> {
                    if (thinking) {
                        thinking = false
                        refusesThinking += endpointKey
                    } else if (withImages) {
                        withImages = false
                        refusesImages += endpointKey
                    } else return false
                    messages = prepare(build())
                }
                is Step.Said -> {
                    val error = step.error
                    if (error != null) {
                        withContext(NonCancellable) {
                            val at = stamp()
                            db.messages().insert(MessageEntity(conversationId = conversationId, role = "assistant", content = step.text.trim(),
                                createdAt = at, error = error, thought = step.thought?.let(MessageThoughts::encode), senderCompanionId = ta.id))
                            db.conversations().touch(conversationId, at)
                        }
                        return true
                    }
                    val body = step.text.trim()
                    if (body.isEmpty() || GroupChats.isSkip(body)) return false
                    storeAssistantBubbles(
                        conversationId,
                        AssistantBubbleSplitter.split(body, GroupChats.MAX_BUBBLES, null),
                        step.thought?.let(MessageThoughts::encode),
                        quiet = true,
                        speakerCompanionId = ta.id,
                    )
                    return true
                }
                is Step.Called -> return false
                is Step.Ended -> return false
            }
        }
    }

    private suspend fun reply(conversationId: Long) {
        val group = db.conversations().get(conversationId)
        if (group?.isGroup == true) {
            groupReply(conversationId, group)
            return
        }
        val startedAt = System.currentTimeMillis()''')

# Normal single-chat replies now receive compact real excerpts from the rest of the world.
rep(chat,
'''            val calls = callsOutside(history)
            fun build() = Prompt.messages(
                s, ta, history, now, groups, withImages, memories, recap, outside, due.map { it.what }, heard, stickers, sendStickers, calls = calls,
            )''',
'''            val calls = callsOutside(history)
            val shared = worldContext(conversationId, lastInput?.content.orEmpty(), s)
            fun build() = Prompt.messages(
                s, ta, history, now, groups, withImages, memories, recap, outside, due.map { it.what }, heard, stickers, sendStickers, calls = calls,
                extraContext = shared,
            )''')

# Store the real speaker on group assistant rows. Existing single-chat callers leave it null.
rep(chat,
'''        reasoning: String? = null,
    ): Long? {''',
'''        reasoning: String? = null,
        speakerCompanionId: Long? = null,
    ): Long? {''')
rep(chat,
'''                        reasoning = reasoning.takeIf { index == parts.lastIndex },
                    ),''',
'''                        reasoning = reasoning.takeIf { index == parts.lastIndex },
                        senderCompanionId = speakerCompanionId,
                    ),''')

# Companion deletion must keep shared rooms owned by another surviving member.
print('apply_group_chats_ai.py applied')
