package com.cleo.cleos.data

import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.ConversationMemberEntity
import com.cleo.cleos.data.db.MessageEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Untrusted portable archives must be checked *before* the restore transaction starts. */
internal object BackupArchiveGuard {
    const val MAX_ENTRIES = 50_000
    const val MAX_JSON_BYTES = 128L * 1024 * 1024
    const val MAX_CONFIG_BYTES = 16L * 1024 * 1024
    const val MAX_MEDIA_BYTES = 256L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

    fun imageName(entry: String): String {
        if (!entry.startsWith("images/")) throw BackupException("备份图片目录异常")
        val name = entry.removePrefix("images/")
        if (name.isEmpty() || name.startsWith(".") || '/' in name || '\\' in name ||
            name == "." || name == ".." || name.any { it.code < 32 }
        ) throw BackupException("备份包含不安全的文件名，没有恢复")
        return name
    }

    fun copyLimited(
        input: InputStream,
        output: OutputStream,
        maximum: Long,
        onChunk: (Int) -> Unit = {},
    ): Long {
        val buffer = ByteArray(32 * 1024)
        var copied = 0L
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            if (n == 0) continue
            copied += n.toLong()
            if (copied > maximum) throw BackupException("备份文件超过安全大小限制，没有恢复")
            onChunk(n)
            output.write(buffer, 0, n)
        }
        return copied
    }

    fun readLimited(input: InputStream, maximum: Long, onChunk: (Int) -> Unit = {}): ByteArray =
        ByteArrayOutputStream().use { output ->
            copyLimited(input, output, maximum, onChunk)
            output.toByteArray()
        }

    /** Never silently substitute an unrelated local image for a backup's same-named file. */
    fun sameFileContent(source: File, destination: File): Boolean {
        if (!source.isFile || !destination.isFile || source.length() != destination.length()) return false
        source.inputStream().buffered().use { a ->
            destination.inputStream().buffered().use { b ->
                val ab = ByteArray(32 * 1024)
                val bb = ByteArray(32 * 1024)
                while (true) {
                    val count = a.read(ab)
                    val other = b.read(bb)
                    if (count != other) return false
                    if (count == -1) return true
                    if (!ab.copyOfRange(0, count).contentEquals(bb.copyOfRange(0, count))) return false
                }
            }
        }
    }

    fun validateRows(
        companionIds: List<Long>,
        conversations: List<ConversationEntity>,
        members: List<ConversationMemberEntity>,
        messages: List<MessageEntity>,
    ) {
        val ids = companionIds.toSet()
        if (ids.size != companionIds.size) throw BackupException("备份包含重复角色，没有恢复")
        val conversationsById = conversations.associateBy { it.id }
        if (conversationsById.size != conversations.size) throw BackupException("备份包含重复会话，没有恢复")
        for (conversation in conversations) {
            if (conversation.companionId !in ids) throw BackupException("会话所属角色缺失，没有恢复")
        }
        val seen = HashSet<Pair<Long, Long>>()
        val byGroup = HashMap<Long, Int>()
        for (member in members) {
            val room = conversationsById[member.conversationId]
            if (room?.isGroup != true || member.companionId !in ids ||
                !seen.add(member.conversationId to member.companionId)
            ) throw BackupException("备份群成员关系不完整或重复，没有恢复")
            byGroup[member.conversationId] = (byGroup[member.conversationId] ?: 0) + 1
        }
        // Accept a legacy one-member group rather than prevent recovery of an older backup.
        if (conversations.any { it.isGroup && (byGroup[it.id] ?: 0) == 0 }) {
            throw BackupException("备份存在没有成员的群聊，没有恢复")
        }
        if (messages.any { it.conversationId !in conversationsById }) {
            throw BackupException("备份包含无所属会话的消息，没有恢复")
        }
    }
}
