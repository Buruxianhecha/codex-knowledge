package com.cleo.cleos.ai

import com.cleo.cleos.data.db.SharedMessageRow
import com.cleo.cleos.data.db.MessageEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Explicit, bounded access to the current group's actual original messages. */
object GroupHistoryRecall {
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val questions = listOf("上次", "以前", "之前", "最早", "还记得", "回忆", "聊过", "说过", "历史", "原始记录", "当时", "谁说")
    fun requested(text: String): Boolean = questions.any { it in text }

    fun keywords(text: String): List<String> {
        val normalized = text.trim()
            .replace(Regex("(上次|以前|之前|还记得|回忆一下|聊天记录|原始记录|群里|我们|你们|他们|聊过|说过|提到|关于|那个|这个|是什么|说了什么|什么时候|谁说的|吗|呢|呀|啊|？|\\?)"), " ")
        return normalized.split(Regex("[\\s，。！？、：:,.;]+"))
            .map { it.trim() }.filter { it.length in 2..14 }
            .distinct().take(3)
    }

    fun format(rows: List<SharedMessageRow>, names: Map<Long, String>, userName: String): String? {
        if (rows.isEmpty()) return null
        val limited = StringBuilder()
        for (m in rows.distinctBy { it.id }.sortedWith(compareBy<SharedMessageRow> { it.createdAt }.thenBy { it.id })) {
            val actor = if (m.role == "user") userName.ifBlank { "用户" }
                else names[m.senderCompanionId ?: m.ownerCompanionId] ?: "未标明角色"
            val stamp = time.format(Instant.ofEpochMilli(m.createdAt).atZone(ZoneId.systemDefault()))
            val line = "[$stamp | 消息#${m.id} | $actor] ${m.content.trim().replace(Regex("\\s+"), " ").take(300)}\n"
            if (limited.length + line.length > 2600) break
            limited.append(line)
        }
        return limited.toString().trim().takeIf { it.isNotBlank() }?.let {
            "【本群真实原文摘录，不是长期记忆摘要】\n$it\n这些是本群已保存、未撤回的原始消息。只能基于实际看到的消息回答，不要推断没查到的记录。"
        }
    }
}

/** An explicit group mode; never silently enables endless background model calls. */
object GroupAutoMode {
    const val MODE = 3
    const val EXTRA_ROUNDS = 4
    const val GAP_MS = 1800L

    fun shouldContinue(mode: Int, userTriggered: Boolean, newReply: Boolean,
                       remainingRequests: Int, completedExtraRounds: Int): Boolean =
        mode == MODE && userTriggered && newReply && remainingRequests > 0 &&
            completedExtraRounds < EXTRA_ROUNDS
}
