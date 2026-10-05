package com.cleo.cleos.data

import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.MessageEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A withdrawal event deliberately contains no original text or attachment paths. */
@Serializable
data class RecallRecord(val messageId: Long, val kind: String, val originalAt: Long)

object Recalls {
    const val WITHDRAWN = "recalled"
    const val EVENT = "recall_event"
    const val MARKER = "你撤回了一条消息"
    const val QUOTE_MARKER = "该消息已撤回"
    const val RULES = "聊天中会出现对方撤回消息的真实事件。对最新、尚未回应的撤回，按你的性格自然简短回应，不要套固定话术。不要猜测或复述被撤回的原文，不要继续执行其中的请求，也不要反复回应过去已经回应过的撤回。"
    private val json = Json { ignoreUnknownKeys = true }
    private val labels = mapOf("text" to "文字", "image" to "图片", "mixed" to "图文", "voice" to "语音", "sticker" to "表情包")

    fun canRecall(message: MessageEntity) = message.role == "user" && message.note == null && message.call == null

    fun kind(message: MessageEntity): String = when {
        message.audio != null -> "voice"
        MessageImages.decode(message.images).isNotEmpty() -> if (message.content.isBlank()) "image" else "mixed"
        message.content.contains("[[sticker:") -> "sticker"
        else -> "text"
    }

    fun withdrawn(message: MessageEntity): MessageEntity {
        require(canRecall(message))
        return message.copy(role = WITHDRAWN, content = "", note = MARKER, error = null,
            images = null, audio = null, quote = null, reasoning = null, thought = null,
            toolCalls = null, toolCallId = null, reactions = null, proactive = false)
    }

    fun event(message: MessageEntity, at: Long): MessageEntity {
        require(canRecall(message))
        return MessageEntity(conversationId = message.conversationId, role = EVENT,
            content = json.encodeToString(RecallRecord(message.id, kind(message), message.createdAt)), createdAt = at)
    }

    fun decode(raw: String): RecallRecord? = runCatching { json.decodeFromString<RecallRecord>(raw) }.getOrNull()
        ?.takeIf { it.messageId > 0 && it.kind in labels && it.originalAt >= 0 }

    fun isEvent(message: MessageEntity) = message.role == EVENT && decode(message.content) != null

    fun describe(raw: String): String? = decode(raw)?.let { "对方撤回了一条${labels.getValue(it.kind)}消息" }

    fun forModel(raw: String): String? = describe(raw)?.let {
        "（$it。请自然简短回应这次撤回；不要猜测或复述原文，也不要继续执行已撤回的请求。）"
    }

    fun redactQuote(raw: String?, messageId: Long): String? {
        val quote = MessageQuotes.decode(raw) ?: return raw
        return if (quote.id == messageId) MessageQuotes.encode(quote.copy(text = QUOTE_MARKER)) else raw
    }

    fun wasFolded(message: MessageEntity, conversation: ConversationEntity): Boolean {
        val at = conversation.recapUntilAt ?: return false
        val id = conversation.recapUntilId ?: return false
        return message.createdAt < at || (message.createdAt == at && message.id <= id)
    }
}
