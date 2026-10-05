package com.cleo.cleos.data

import com.cleo.cleos.data.db.MessageEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A menu reaction has its own turn, including when its target is outside the live window. */
@Serializable
data class ReactionEventRecord(val messageId: Long, val emoji: String, val kind: String, val excerpt: String)

data class ReactionChange(val reactions: String?, val event: MessageEntity?)

object ReactionEvents {
    const val EVENT = "reaction_event"
    const val EXCERPT_MAX = 240
    const val RULES = "对方给你的某条消息贴小表情，是对那条消息的一次主动回应。对最新、尚未回应的小表情事件，结合对应消息和表情自然简短地回复，不要套固定话术，不要只说明收到了，也不要要求对方再发一条文字。表情含义要结合语境，不要武断猜测情绪；已经回应过的旧事件不要反复回应。"
    private val json = Json { ignoreUnknownKeys = true }
    private val kinds = setOf("text", "voice", "sticker")

    private fun excerpt(text: String): String {
        val plain = text.replace(Regex("\\s+"), " ").trim()
        return if (plain.codePointCount(0, plain.length) <= EXCERPT_MAX) plain
        else plain.substring(0, plain.offsetByCodePoints(0, EXCERPT_MAX)) + "…"
    }

    /** Adding asks for an answer; tapping the same emoji again only removes the chip. */
    fun change(message: MessageEntity, emoji: String, at: Long): ReactionChange? {
        if (message.role != "assistant" || message.call != null || message.error != null ||
            message.content.isBlank() || emoji !in MessageReactions.OFFERED) return null
        val before = MessageReactions.decode(message.reactions)
        val removed = before.any { it.emoji == emoji }
        val next = MessageReactions.toggle(before, emoji, at)
        val event = if (removed) null else {
            val sticker = StickerText.only(message.content)
            val kind = when { sticker != null -> "sticker"; message.audio != null -> "voice"; else -> "text" }
            val record = ReactionEventRecord(message.id, emoji, kind, excerpt(sticker ?: StickerText.plain(message.content)))
            MessageEntity(conversationId = message.conversationId, role = EVENT,
                content = json.encodeToString(record), createdAt = at)
        }
        return ReactionChange(MessageReactions.encode(next), event)
    }

    fun decode(raw: String): ReactionEventRecord? = runCatching { json.decodeFromString<ReactionEventRecord>(raw) }.getOrNull()
        ?.takeIf { it.messageId > 0 && it.emoji in MessageReactions.OFFERED && it.kind in kinds &&
            it.excerpt.isNotBlank() && it.excerpt.length <= EXCERPT_MAX * 2 + 1 }

    fun isEvent(message: MessageEntity) = message.role == EVENT && decode(message.content) != null

    fun describe(raw: String): String? = decode(raw)?.let {
        val target = when (it.kind) { "voice" -> "你的语音"; "sticker" -> "你发的表情包"; else -> "你说的" }
        "对方用 ${it.emoji} 回应了${target}「${it.excerpt}」"
    }

    fun forModel(raw: String): String? = describe(raw)?.let {
        "（$it。这是对方刚刚的小表情回应，请结合那条消息自然简短地回复。）"
    }

    /** New events replace the legacy 'tell it with the next text' path, once per addition. */
    fun covered(history: List<MessageEntity>): Set<Triple<Long, String, Long>> = history.mapNotNull { m ->
        if (m.role != EVENT) null else decode(m.content)?.let { Triple(it.messageId, it.emoji, m.createdAt) }
    }.toSet()

    fun pendingIds(history: List<MessageEntity>, conversationId: Long, messageId: Long, emoji: String,
                   answeredThrough: Long): List<Long> = history.filter { m ->
        m.conversationId == conversationId && m.role == EVENT && m.createdAt > answeredThrough &&
            decode(m.content)?.let { it.messageId == messageId && it.emoji == emoji } == true
    }.map { it.id }
}
