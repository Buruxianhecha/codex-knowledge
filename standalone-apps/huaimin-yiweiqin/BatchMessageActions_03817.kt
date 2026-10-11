package com.cleo.cleos.ui.chat

import androidx.room.withTransaction
import com.cleo.cleos.AppContainer
import com.cleo.cleos.data.MessageAudios
import com.cleo.cleos.data.MessageImages
import com.cleo.cleos.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Shared single/group chat batch actions. Forwarding writes stored copies, never an AI-send request. */
internal object BatchMessageActions {
    const val MAX_SELECTED = 100
    const val MERGED_PREFIX = "【合并转发的聊天记录】"

    fun selectable(m: MessageEntity): Boolean =
        (m.role == "user" || m.role == "assistant") && m.note == null && m.call == null &&
        m.error == null && (m.content.isNotBlank() || m.images != null || m.audio != null)

    fun isMerged(content: String) = content.startsWith(MERGED_PREFIX)

    fun ordered(rows: List<MessageEntity>, ids: Set<Long>, conversationId: Long): List<MessageEntity> {
        require(ids.isNotEmpty() && ids.size <= MAX_SELECTED) { "每次只能选择 1～100 条消息" }
        val selected = rows.filter { it.id in ids && it.conversationId == conversationId && selectable(it) }
            .sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.id })
        require(selected.size == ids.size) { "所选消息发生变化，请重新选择" }
        return selected
    }

    private fun transcript(rows: List<MessageEntity>, names: Map<Long, String>, source: String): String =
        buildString {
            append(MERGED_PREFIX).append('\n').append(source).append(" · ")
                .append(rows.size).append(" 条\n")
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            rows.forEach { m ->
                append('\n').append(if (m.role == "user") "我" else names[m.senderCompanionId] ?: "TA")
                append("  ").append(format.format(java.util.Date(m.createdAt))).append('\n')
                append(m.content.ifBlank { if (m.audio != null) "［语音］" else "［图片］" })
                val count = MessageImages.decode(m.images).size
                if (count > 0) append("\n［附图 ").append(count).append(" 张］")
                append('\n')
            }
        }

    private fun cloneFile(c: AppContainer, filename: String, created: MutableList<String>): String {
        require(filename == java.io.File(filename).name && filename != "." && filename != "..") { "附件路径异常" }
        val src = c.images.file(filename)
        require(src.isFile) { "原附件丢失，无法转发" }
        val copy = "batch-" + UUID.randomUUID().toString() + "." + src.extension.ifBlank { "bin" }
        src.copyTo(c.images.file(copy), overwrite = false)
        created.add(copy)
        return copy
    }

    suspend fun forward(
        c: AppContainer, sourceId: Long, destinationId: Long, ids: Set<Long>,
        merged: Boolean, names: Map<Long, String>, sourceTitle: String,
    ): Int {
        val original = ordered(c.db.messages().byIds(ids), ids, sourceId)
        require(c.db.conversations().get(destinationId) != null) { "目标会话不存在" }
        if (merged) require(original.none { it.audio != null }) {
            "含语音的消息请使用逐条转发，以保留可播放录音"
        }
        val created = mutableListOf<String>()
        try {
            val copies = withContext(Dispatchers.IO) {
                if (merged) {
                    val pictures = original.flatMap { m -> MessageImages.decode(m.images).map {
                        it.copy(file = cloneFile(c, it.file, created))
                    } }
                    listOf(MessageEntity(conversationId = destinationId, role = "user",
                        content = transcript(original, names, sourceTitle),
                        createdAt = System.currentTimeMillis(),
                        images = MessageImages.encode(pictures)))
                } else original.mapIndexed { index, m ->
                    val pictures = MessageImages.decode(m.images).map {
                        it.copy(file = cloneFile(c, it.file, created))
                    }
                    val audio = MessageAudios.decode(m.audio)?.let {
                        it.copy(file = cloneFile(c, it.file, created))
                    }
                    val name = if (m.role == "user") "我" else names[m.senderCompanionId] ?: "TA"
                    m.copy(id = 0, conversationId = destinationId, role = "user",
                        content = if (m.role == "assistant") "转发自" + name + "：\n" + m.content else m.content,
                        createdAt = System.currentTimeMillis() + index,
                        error = null, toolCalls = null, reasoning = null, toolCallId = null,
                        note = null, images = MessageImages.encode(pictures),
                        audio = audio?.let(MessageAudios::encode), thought = null, quote = null,
                        proactive = false, reactions = null, call = null,
                        senderCompanionId = null, mentionedCompanionIds = null)
                }
            }
            c.db.withTransaction {
                require(c.db.conversations().get(destinationId) != null) { "目标会话已删除" }
                require(ordered(c.db.messages().byIds(ids), ids, sourceId) == original) { "原消息已变化" }
                copies.forEach { c.db.messages().insert(it) }
                c.db.conversations().touch(destinationId, System.currentTimeMillis())
            }
            return original.size
        } catch (error: Exception) {
            withContext(Dispatchers.IO) { c.images.delete(created) }
            throw error
        }
    }

    suspend fun delete(c: AppContainer, conversationId: Long, ids: Set<Long>): Int {
        val original = ordered(c.db.messages().byIds(ids), ids, conversationId)
        val files = original.flatMap { m -> MessageImages.decode(m.images).map { it.file } +
            listOfNotNull(MessageAudios.decode(m.audio)?.file) }
        c.db.withTransaction {
            require(ordered(c.db.messages().byIds(ids), ids, conversationId) == original) { "原消息已变化" }
            original.forEach { c.db.messages().delete(it.id) }
        }
        withContext(Dispatchers.IO) { c.images.delete(files) }
        return original.size
    }
}
