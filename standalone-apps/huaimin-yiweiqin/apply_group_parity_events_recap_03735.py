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
print("0.37.35 group reaction routing and recap identity applied")
