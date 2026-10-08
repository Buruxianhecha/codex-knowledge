package com.cleo.cleos.ai

import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.SharedMessageRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Actual saved messages from permitted conversations. Never manufacture deleted content. */
object ChatHistorySearch {
    const val PAGE_MAX = 30
    const val PAGE_DEFAULT = 20
    const val CHAR_MAX = 15_000
    private const val PER_MESSAGE_MAX = 1_600
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    fun wanted(text: String): Boolean {
        val phrase = text.replace(Regex("\\s+"), "")
        return listOf("私聊", "私下聊", "聊天记录", "历史消息", "历史记录", "聊过", "聊了什么", "说过什么",
            "上次聊", "以前聊", "之前聊", "查看聊天", "互通", "看得到", "能看到", "聊天内容",
            "说了什么", "聊些什么", "之前说", "以前说", "原始消息", "打开记录", "继续查记录", "更早的消息")
            .any { it in phrase }
    }

    fun dayBounds(day: String): Pair<Long, Long>? = try {
        val date = LocalDate.parse(day)
        val first = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        first to end
    } catch (_: Exception) { null }

    fun label(row: SharedMessageRow, names: Map<Long, String>, userName: String): String {
        val from = if (row.role == "user") userName.trim().ifEmpty { "我" }
            else names[row.senderCompanionId ?: row.ownerCompanionId] ?: "角色"
        val time = dateFormat.format(Instant.ofEpochMilli(row.createdAt).atZone(ZoneId.systemDefault()))
        return "[$time | 会话#${row.conversationId}「${row.conversationTitle}」| 消息#${row.id}] $from："
    }

    /** Order responses chronologically while DB paging remains newest-first. */
    fun page(rows: List<SharedMessageRow>, total: Int, offset: Int, names: Map<Long,String>, userName: String): String {
        if (rows.isEmpty()) return if (total > 0)
            "这一页没有更多可读取的消息。共 $total 条，已到末尾；请调整 offset 或关键词。"
            else "当前条件下未找到允许共享的原始消息。无法据此确定从未聊过、消息已被删除还是共享权限关闭；不要编造聊天内容。"
        val lines = mutableListOf<String>()
        var used = 0
        for (row in rows.asReversed()) {
            val head = label(row, names, userName)
            val body = row.content
            val shortened = body.take(PER_MESSAGE_MAX)
            val suffix = if (body.length > PER_MESSAGE_MAX) "…【本条过长，请用 read_chat_message(message_id=${row.id}) 分段读原文】" else ""
            val line = head + shortened + suffix
            if (used + line.length > CHAR_MAX) {
                lines += "【本页受上下文长度限制；如需继续，缩小 limit 或使用关键词、日期筛选】"
                break
            }
            lines += line; used += line.length
        }
        val next = offset + rows.size
        val more = if (next < total) "还有更早的消息，下一页 offset=$next。" else "已到查询结果末尾。"
        return "【实时原始消息查询·不是摘要】共 $total 条符合条件；本次按最新优先取第 ${offset+1}—$next 条。$more\n" +
            lines.joinToString("\n") +
            "\n仅依据以上真正读取的消息回答；如需更早/别的关键词，继续调用 search_chat_history。不可推断已删除的内容。"
    }

    fun fullMessage(row: SharedMessageRow, start: Int, max: Int): String {
        val from = start.coerceIn(0, row.content.length)
        val until = (from.toLong()+max.coerceIn(1,12_000)).coerceAtMost(row.content.length.toLong()).toInt()
        val body = row.content.substring(from, until)
        val more = if (until < row.content.length) "还有剩余原文，请使用 start=$until 继续读取。" else "这条消息已读完。"
        return "【原始消息 #${row.id}，会话 #${row.conversationId}「${row.conversationTitle}」】正文总长度 ${row.content.length} 字符，本次位置 [$from,$until)：\n$body\n$more"
    }
}
