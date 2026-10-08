package com.cleo.cleos.data

import com.cleo.cleos.data.db.MessageEntity

/** AI may react to the last real group message using a strictly validated token.
 * Actor IDs are immutable companion IDs; never infer a reactor from message sender or UI state.
 */
object GroupReactionRules {
    private val token = Regex("^<react:([^<>\\s]{1,16})>$")

    fun parse(text: String): String? = token.matchEntire(text.trim())?.groupValues?.get(1)
        ?.takeIf { it in MessageReactions.ALL }

    fun target(history: List<MessageEntity>, actorId: Long): MessageEntity? =
        history.asReversed().firstOrNull { m ->
            m.id > 0 && m.error == null && m.note == null && m.call == null &&
                m.content.isNotBlank() && m.role in setOf("user", "assistant") &&
                m.senderCompanionId != actorId
        }

    /** Idempotent per (actor, emoji), preserving prior user's actor-less legacy reactions. */
    fun add(existing: List<MessageReaction>, emoji: String, actorId: Long, at: Long): List<MessageReaction> {
        if (actorId <= 0 || emoji !in MessageReactions.ALL || existing.size >= 50 ||
            existing.any { it.emoji == emoji && it.actorCompanionId == actorId }) return existing
        return existing + MessageReaction(emoji, at, actorCompanionId = actorId)
    }
}
