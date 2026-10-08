package com.cleo.cleos.ai

import com.cleo.cleos.data.db.MessageEntity

/** Pure rules: keep a burst of consecutive user messages as ONE group request. */
object GroupTurnRecovery {
    private const val USER_BURST_MS = 90_000L
    private const val MAX_USER_MESSAGES = 6

    fun activeUserMessages(history: List<MessageEntity>): List<MessageEntity> {
        val last = history.lastOrNull { it.role == "user" && it.note == null && it.error == null }
            ?: return emptyList()
        // A *real* reply from a group member ends a burst; a system/error row does not.
        val lastAnswer = history.indexOfLast {
            it.role == "assistant" && it.senderCompanionId != null &&
                it.error == null && it.content.isNotBlank()
        }
        return history.drop(lastAnswer + 1).filter { m ->
            m.role == "user" && m.note == null && m.error == null &&
                m.createdAt <= last.createdAt &&
                last.createdAt - m.createdAt in 0..USER_BURST_MS
        }.takeLast(MAX_USER_MESSAGES)
    }

    fun text(messages: List<MessageEntity>): String =
        messages.map { it.content.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    fun mentionIds(messages: List<MessageEntity>): String? =
        messages.flatMap { it.mentionedCompanionIds.orEmpty().split(",") }
            .mapNotNull { it.trim().toLongOrNull() }
            .distinct().take(6).takeIf { it.isNotEmpty() }?.joinToString(",")

    fun isQuestion(text: String): Boolean =
        text.contains('？') || text.contains('?') ||
            listOf("能不能", "怎么看", "怎么回事", "告诉我", "说说", "你们觉得", "你觉得", "聊过什么", "聊了什么", "回复我")
                .any { it in text }
}
