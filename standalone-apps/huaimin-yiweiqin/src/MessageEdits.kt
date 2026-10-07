package com.cleo.cleos.data

import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.MessageEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class MessageEditException(message: String) : Exception(message)

/** Editing continues at the original turn, in a new conversation; the original stays intact. */
object MessageEdits {
    fun canEdit(message: MessageEntity): Boolean = message.role == "user" && message.note == null &&
        message.call == null && message.audio == null &&
        (StickerText.plain(message.content).isNotBlank() || MessageImages.decode(message.images).isNotEmpty()) &&
        StickerText.only(message.content) == null

    fun canSubmit(message: MessageEntity, text: String): Boolean = canEdit(message) &&
        text.trim() != message.content.trim() &&
        (text.isNotBlank() || MessageImages.decode(message.images).isNotEmpty())

    /** Reject stale dialogs; order by both columns so messages with equal timestamps stay in order. */
    fun prefix(history: List<MessageEntity>, expected: MessageEntity, text: String): List<MessageEntity> {
        if (!canSubmit(expected, text)) throw MessageEditException("请修改消息内容后再重新发送。")
        val current = history.singleOrNull { it.id == expected.id && it.conversationId == expected.conversationId }
        if (current != expected) throw MessageEditException("原消息已经变化，请重新打开编辑。")
        return history.filter { it.conversationId == expected.conversationId &&
            (it.createdAt < expected.createdAt || (it.createdAt == expected.createdAt && it.id <= expected.id)) }
            .sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.id })
    }

    /** Every reference and owned attachment belongs to the new conversation, including past calls. */
    fun copyRow(message: MessageEntity, conversationId: Long, ids: Map<Long, Long>, files: Map<String, String>): MessageEntity {
        val quote = MessageQuotes.decode(message.quote)?.let { q ->
            ids[q.id]?.let { MessageQuotes.encode(q.copy(id = it)) }
        }
        val content = when (message.role) {
            Recalls.EVENT, ReactionEvents.EVENT -> remapEvent(message.content, ids)
            else -> message.content
        }
        val pictures = MessageImages.decode(message.images).map { it.copy(file = files[it.file] ?: it.file) }
        val audio = MessageAudios.decode(message.audio)?.let { it.copy(file = files[it.file] ?: it.file) }
        return message.copy(id = 0, conversationId = conversationId, content = content,
            quote = quote, images = MessageImages.encode(pictures), audio = audio?.let(MessageAudios::encode),
            call = message.call?.let { ids[it] ?: throw MessageEditException("电话记录不完整，暂时无法从这里重新继续。") })
    }

    private fun remapEvent(raw: String, ids: Map<Long, Long>): String = runCatching {
        val obj = Json.parseToJsonElement(raw) as? JsonObject ?: return raw
        val old = (obj["messageId"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return raw
        val next = ids[old] ?: return raw
        JsonObject(obj + ("messageId" to JsonPrimitive(next))).toString()
    }.getOrDefault(raw)

    /** Never reuse a recap which already summarized the words being edited or the later reply. */
    fun copyConversation(source: ConversationEntity, target: MessageEntity, ids: Map<Long, Long>, at: Long): ConversationEntity {
        val boundary = source.recapUntilAt
        val marker = source.recapUntilId
        val before = source.recap != null && boundary != null && marker != null &&
            (boundary < target.createdAt || (boundary == target.createdAt && marker < target.id)) && ids[marker] != null
        val title = source.title.removeSuffix(" · 编辑续聊").take(40).ifBlank { "对话" } + " · 编辑续聊"
        return source.copy(id = 0, title = title, createdAt = at, updatedAt = at,
            recap = if (before) source.recap else null,
            recapUntilAt = if (before) boundary else null,
            recapUntilId = if (before) marker?.let(ids::get) else null)
    }
}
