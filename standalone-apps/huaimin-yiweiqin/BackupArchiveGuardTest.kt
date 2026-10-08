package com.cleo.cleos.data

import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.ConversationMemberEntity
import com.cleo.cleos.data.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupArchiveGuardTest {
    private fun group() = ConversationEntity(id = 5, title = "一起聊天", createdAt = 1, updatedAt = 1, companionId = 1, isGroup = true)
    private fun links() = listOf(ConversationMemberEntity(5, 1, 0), ConversationMemberEntity(5, 2, 1))
    private fun message() = MessageEntity(id = 4, conversationId = 5, role = "assistant", content = "你好", createdAt = 3, senderCompanionId = 2)

    @Test fun ordinaryGroupAndMessagesPassPreflight() {
        BackupArchiveGuard.validateRows(listOf(1, 2), listOf(group()), links(), listOf(message()))
    }

    @Test fun oldSingleChatArchiveWithoutMembersStillRestores() {
        val single = group().copy(isGroup = false)
        BackupArchiveGuard.validateRows(listOf(1), listOf(single), emptyList(), listOf(message()))
    }

    @Test fun missingOrMisdirectedMemberIsRejected() {
        val bad = listOf(ConversationMemberEntity(5, 55, 0))
        try {
            BackupArchiveGuard.validateRows(listOf(1, 2), listOf(group()), bad, listOf(message()))
            throw AssertionError("missing companion was accepted")
        } catch (_: BackupException) {}
        try {
            BackupArchiveGuard.validateRows(listOf(1, 2), listOf(group().copy(isGroup = false)), links(), emptyList())
            throw AssertionError("single conversation accepted group member records")
        } catch (_: BackupException) {}
    }

    @Test fun duplicatesAndUnownedMessagesAreRejected() {
        try {
            BackupArchiveGuard.validateRows(listOf(1, 2), listOf(group()), links() + links().first(), listOf(message()))
            throw AssertionError("duplicate member accepted")
        } catch (_: BackupException) {}
        try {
            BackupArchiveGuard.validateRows(listOf(1, 2), listOf(group()), links(), listOf(message().copy(conversationId = 999)))
            throw AssertionError("dangling message accepted")
        } catch (_: BackupException) {}
    }

    @Test fun legacyOneMemberGroupCanBeRecoveredButEmptyGroupCannot() {
        BackupArchiveGuard.validateRows(listOf(1, 2), listOf(group()), links().take(1), emptyList())
        try {
            BackupArchiveGuard.validateRows(listOf(1, 2), listOf(group()), emptyList(), emptyList())
            throw AssertionError("empty group accepted")
        } catch (_: BackupException) {}
    }

    @Test fun imagePathsCannotEscapeOrOverwriteWithNestedAliases() {
        assertEquals("avatar_12.webp", BackupArchiveGuard.imageName("images/avatar_12.webp"))
        for (entry in listOf("images/../secret", "images/a/b", "images/.hidden", "images/", "other/file")) {
            try {
                BackupArchiveGuard.imageName(entry)
                throw AssertionError("unsafe path accepted: $entry")
            } catch (_: BackupException) {}
        }
    }

    @Test fun oversizedZipEntryIsRejectedBeforeOverflowWrite() {
        val out = ByteArrayOutputStream()
        try {
            BackupArchiveGuard.copyLimited(ByteArrayInputStream(ByteArray(12)), out, maximum = 10)
            throw AssertionError("overlarge entry accepted")
        } catch (_: BackupException) {}
        assertTrue(out.size() <= 10)
        val valid = ByteArrayOutputStream()
        assertEquals(6L, BackupArchiveGuard.copyLimited(ByteArrayInputStream(ByteArray(6)), valid, maximum = 10))
        assertEquals(6, valid.size())
    }
}
