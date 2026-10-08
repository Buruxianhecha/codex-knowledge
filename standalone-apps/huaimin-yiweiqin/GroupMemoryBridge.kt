package com.cleo.cleos.ai

import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.SharedMessageRow

/** Real, permission-filtered snippets of another TA's private conversation. */
object GroupMemoryBridge {
    private const val MAX_LINES = 14
    private const val MAX_CHARACTERS = 3150

    fun isHistoryQuestion(userText: String): Boolean =
        listOf("私聊", "私下", "聊天记录", "之前聊", "聊过", "聊了什么", "说过什么",
            "能看到", "看得到", "互通", "聊天内容", "聊的内容", "和谁聊", "看我们聊")
            .any { it in userText }

    fun orderedTargets(text: String, candidates: List<CompanionEntity>): List<CompanionEntity> =
        candidates.distinctBy { it.id }
            .sortedWith(compareByDescending<CompanionEntity> { ta ->
                val name = ta.name.trim()
                when {
                    name.isEmpty() -> -1
                    "和" + name in text || "跟" + name in text || "与" + name in text -> 3
                    "关于" + name in text || "看" + name in text -> 2
                    else -> 1
                }
            }.thenBy { it.id }).take(3)

    fun directExcerpt(
        companion: CompanionEntity,
        rows: List<SharedMessageRow>,
        all: List<CompanionEntity>,
        userName: String,
    ): String? {
        if (rows.isEmpty()) return null
        val who = all.associate { it.id to it.name.trim().ifEmpty { "TA" } }
        val me = userName.trim().ifEmpty { "我" }
        val seen = mutableSetOf<Pair<Long, Long>>()
        val chosen = mutableListOf<String>()
        var used = 0
        for (m in rows.sortedWith(compareByDescending<SharedMessageRow> { it.createdAt }.thenByDescending { it.id })) {
            if (!seen.add(m.conversationId to m.id)) continue
            val speaker = if (m.role == "user") me else who[m.senderCompanionId ?: m.ownerCompanionId] ?: "TA"
            val title = m.conversationTitle.trim().ifEmpty { "私聊" }
            val line = "[$title] $speaker：${m.content.trim().replace(Regex("\\s+"), " ").take(250)}"
            if (m.content.isBlank() || used + line.length > MAX_CHARACTERS) continue
            chosen.add(line)
            used += line.length
            if (chosen.size >= MAX_LINES) break
        }
        if (chosen.isEmpty()) return null
        return """
【你确实可以阅读的、来自本机数据库的授权私聊摘录：${companion.name.trim()}】
${chosen.asReversed().joinToString("\n")}
这些是其他会话的真实聊天记录片段，不是推测，也不是你自己的回忆。用户问起时，可以自然地承认你已经读到，并引用实际内容回答；不要笼统声称“私聊绝对看不见”。只知道本段展示的内容，未展示的消息不要编造；记录可能只是完整私聊的一部分。
""".trim()
    }
}
