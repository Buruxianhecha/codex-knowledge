#!/usr/bin/env python3
"""0.37.35 group reaction target and speaker-attributed rolling recap."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()

def change(path, old, new, title):
    p = root / path
    value = p.read_text(encoding="utf-8")
    n = value.count(old)
    if n != 1:
        raise SystemExit(title + ": expected one anchor, found " + str(n))
    p.write_text(value.replace(old, new, 1), encoding="utf-8")

chat = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
recap = "app/src/main/java/com/cleo/cleos/ai/Recap.kt"

change(chat,
'''        val patTarget = if (continued) null else pat?.targetCompanionId
''',
'''        val patTarget = if (continued) null else pat?.targetCompanionId
        val reactedMessageId = if (continued) null else trigger
            ?.takeIf { ReactionEvents.isEvent(it) }
            ?.let { ReactionEvents.decode(it.content)?.messageId }
        val reactedOwnerId = reactedMessageId?.let { messageId ->
            val original = db.messages().get(messageId)
            if (original == null || original.conversationId != conversationId ||
                original.role != "assistant") null
            else original.senderCompanionId ?: conversation.companionId
        }
''', "reaction target resolution")
change(chat,
'''            mentions.isNotEmpty() -> mentions.filter { it.id !in muted }
            conversation.groupMode == 2 -> emptyList()
''',
'''            mentions.isNotEmpty() -> mentions.filter { it.id !in muted }
            reactedMessageId != null -> eligible.filter { it.id == reactedOwnerId }
            conversation.groupMode == 2 -> emptyList()
''', "reacted speaker priority")
change(chat,
'''                        groupLocation != null || groupWeather != null || (isQuestion && said == 0),
''',
'''                        eventTurn || groupLocation != null || groupWeather != null || (isQuestion && said == 0),
''', "direct event response")

change(recap,
'''    fun request(ta: CompanionEntity, userName: String, recap: String?, batch: List<MessageEntity>, zone: ZoneId): List<ApiMessage> {
''',
'''    fun request(ta: CompanionEntity, userName: String, recap: String?, batch: List<MessageEntity>,
                zone: ZoneId, groupSpeakers: Map<Long, String> = emptyMap()): List<ApiMessage> {
''', "recap request signature")
change(recap,
'''            append("你和${them}一直在手机上聊天。聊得久了，早先的聊天记录不会再原样给你看，你靠自己记的「前情提要」接着聊。")
''',
'''            if (groupSpeakers.isNotEmpty()) {
                append("这是你与其他成员参与的多人群聊。前情提要属于整个群，不是你个人说过的话。")
                append("记录已标注真实发言角色，严格区分不同成员。旧摘要没写发言者的，不要猜测是谁说的。")
            } else {
                append("你和${them}一直在手机上聊天。聊得久了，早先的聊天记录不会再原样给你看，你靠自己记的「前情提要」接着聊。")
            }
''', "recap group instruction")
change(recap,
'''            append("- 用「我」称呼自己，用「$them」称呼对方。\\n")
''',
'''            if (groupSpeakers.isNotEmpty())
                append("- 用真实角色姓名区分发言者；用「$them」称呼对方，不要全都写成「我」。\\n")
            else append("- 用「我」称呼自己，用「$them」称呼对方。\\n")
''', "recap attribution guideline")
change(recap,
'''            append("【接下来的聊天记录】\\n").append(transcript(batch, zone))
''',
'''            append("【接下来的聊天记录】\\n").append(transcript(batch, zone, groupSpeakers))
''', "recap transcript forwarding")
change(recap,
'''    fun transcript(batch: List<MessageEntity>, zone: ZoneId): String = buildString {
''',
'''    fun transcript(batch: List<MessageEntity>, zone: ZoneId,
                   groupSpeakers: Map<Long, String> = emptyMap()): String = buildString {
''', "transcript signature")
change(recap,
'''            val line = lineOf(m) ?: continue
''',
'''            val line = lineOf(m, groupSpeakers) ?: continue
''', "transcript line mapping")
change(recap,
'''    private fun lineOf(m: MessageEntity): String? {
''',
'''    private fun lineOf(m: MessageEntity, groupSpeakers: Map<Long, String>): String? {
''', "line speaker parameter")
change(recap,
'''            m.role == "assistant" && m.content.isNotBlank() -> "我$phone：${answering(m)}${said(m.content)}"
''',
'''            m.role == "assistant" && m.content.isNotBlank() -> {
                val sender = if (groupSpeakers.isEmpty()) "我" else
                    groupSpeakers[m.senderCompanionId] ?: "未标明角色"
                "$sender$phone：${answering(m)}${said(m.content)}"
            }
''', "attributed group transcript")
change(recap,
'''            client.stream(ApiEndpoint(ta.apiBaseUrl, key, ta.apiModel), Recap.request(ta, s.userName, conversation.recap, batch, zone()))
''',
'''            val groupSpeakers = if (conversation.isGroup)
                db.companions().all().associate { it.id to it.name.trim().ifEmpty { "角色" } }
                else emptyMap()
            client.stream(ApiEndpoint(ta.apiBaseUrl, key, ta.apiModel),
                Recap.request(ta, s.userName, conversation.recap, batch, zone(), groupSpeakers))
''', "group speaker map in recap job")

# Exactly one character per user turn receives operational tools, even if it
# refuses to speak. This avoids duplicate todos, calendar events, music commands
# and external actions from several group members independently obeying one request.
change(chat,
'''        var said = 0
        for (ta in candidates.take(GroupChats.MAX_MEMBERS)) {
''',
'''        var said = 0
        var toolExecutorReserved = false
        for (ta in candidates.take(GroupChats.MAX_MEMBERS)) {
''', "one side-effect executor per group turn")
change(chat,
'''            show(StreamingReply(conversationId, "", thinking = false, activity = "''',
'''            val canOperateTools = allowToolCalls && !toolExecutorReserved
            if (canOperateTools) toolExecutorReserved = true
            show(StreamingReply(conversationId, "", thinking = false, activity = "''', "reserve group tool actor")
change(chat,
'''                    allowToolCalls = allowToolCalls,
''',
'''                    allowToolCalls = canOperateTools,
''', "prevent duplicate group operations")

# A manual continuation now means a *bounded* short conversation rather than
# requiring another click after 45 seconds for each line. All turns still count
# against the group's actual per-day API budget and remain cancellable.
change(chat,
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
            start(conversationId) { groupReply(conversationId, convo, continued = true) }
        }
    }
''',
'''    /** A user-invoked, cancellable continuation; never an endless background chat. */
    private val groupContinuationAt = ConcurrentHashMap<Long, Long>()

    fun continueGroup(conversationId: Long) {
        scope.launch {
            val convo = db.conversations().get(conversationId) ?: return@launch
            if (!convo.isGroup || convo.groupMode == 2) return@launch
            val now = System.currentTimeMillis()
            val previous = groupContinuationAt[conversationId] ?: 0L
            if (now - previous < 10_000) return@launch
            groupContinuationAt[conversationId] = now
            start(conversationId) {
                repeat(3) { round ->
                    currentCoroutineContext().ensureActive()
                    val current = db.conversations().get(conversationId) ?: return@start
                    if (!current.isGroup || current.groupMode == 2) return@start
                    val before = db.messages().newest(conversationId, 16).firstOrNull {
                        it.role == "assistant" && it.senderCompanionId != null &&
                            it.error == null && it.content.isNotBlank()
                    }?.id
                    groupReply(conversationId, current, continued = true)
                    val after = db.messages().newest(conversationId, 16).firstOrNull {
                        it.role == "assistant" && it.senderCompanionId != null &&
                            it.error == null && it.content.isNotBlank()
                    }?.id
                    if (after == before) return@start
                    if (round < 2) delay(1900)
                }
            }
        }
    }
''', "bounded natural continuation")

from shutil import copyfile
here = Path(__file__).resolve().parent
unit = root / "app/src/test/java/com/cleo/cleos/ai/GroupRecapIdentityTest.kt"
unit.parent.mkdir(parents=True, exist_ok=True)
copyfile(here / "GroupRecapIdentityTest.kt", unit)
print("0.37.35 group reaction routing and recap identity applied")
