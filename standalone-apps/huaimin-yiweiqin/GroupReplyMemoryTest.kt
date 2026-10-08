package com.cleo.cleos.ai

import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.MessageEntity
import com.cleo.cleos.data.db.SharedMessageRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupReplyMemoryTest {
    private fun message(id: Long, role: String, content: String, at: Long, mention: String? = null, speaker: Long? = null) =
        MessageEntity(id = id, conversationId = 10, role = role, content = content, createdAt = at,
            mentionedCompanionIds = mention, senderCompanionId = speaker)
    private fun ta(id: Long, name: String) =
        CompanionEntity(id = id, name = name, apiBaseUrl = "x", apiModel = "m", createdAt = 0)

    @Test fun aQuickBurstRetainsQuestionMentionAndFinalQuestionMark() {
        val history = listOf(
            message(1, "user", "你能看到我和小艺的私聊记录吗？", 1_000L),
            message(2, "user", "@小许", 2_000L, "7"),
            message(3, "user", "?", 3_000L),
        )
        val current = GroupTurnRecovery.activeUserMessages(history)
        assertEquals(3, current.size)
        assertTrue(GroupTurnRecovery.text(current).contains("小艺"))
        assertEquals("7", GroupTurnRecovery.mentionIds(current))
        assertTrue(GroupTurnRecovery.isQuestion(GroupTurnRecovery.text(current)))
    }

    @Test fun successfulGroupReplyEndsThePreviousBurst() {
        val history = listOf(
            message(1, "user", "@小许 帮我", 1_000L, "7"),
            message(2, "assistant", "刚刚回复了", 3_000L, speaker = 7),
            message(3, "user", "新的问题", 4_000L),
        )
        val current = GroupTurnRecovery.activeUserMessages(history)
        assertEquals(listOf(3L), current.map { it.id })
        assertEquals(null, GroupTurnRecovery.mentionIds(current))
    }

    @Test fun oldMentionIsNotCarriedToAnUnrelatedLaterMessage() {
        val history = listOf(
            message(1, "user", "@小许", 1_000L, "7"),
            message(2, "user", "今天怎么样", 200_000L),
        )
        assertEquals(listOf(2L), GroupTurnRecovery.activeUserMessages(history).map { it.id })
    }

    @Test fun unaddressedNaturalConversationIsNotAlwaysForced() {
        assertFalse(GroupTurnRecovery.isQuestion("我先去忙了"))
        assertTrue(GroupTurnRecovery.isQuestion("你们怎么看这件事？"))
    }

    @Test fun privateHistoryRequestDetection() {
        assertTrue(GroupMemoryBridge.isHistoryQuestion("小许，你能看到我和小艺的私下聊天记录吗？"))
        assertTrue(GroupMemoryBridge.isHistoryQuestion("我和小艺聊过什么"))
        assertFalse(GroupMemoryBridge.isHistoryQuestion("今天有点累"))
    }

    @Test fun referencedPrivateChatRanksAheadOfAddressee() {
        val xu = ta(7, "小许")
        val yi = ta(8, "小艺")
        assertEquals(yi.id, GroupMemoryBridge.orderedTargets(
            "小许，你能看到我和小艺的私下聊天记录吗？ @小许", listOf(xu, yi)
        ).first().id)
    }

    @Test fun realDirectPrivateExcerptHasActualNamesAndConversations() {
        val yi = ta(8, "小艺")
        val xu = ta(7, "小许")
        val rows = listOf(
            SharedMessageRow(1, 50, "和小艺的私聊", 8, null, "user", "周末想去海边", 1),
            SharedMessageRow(2, 50, "和小艺的私聊", 8, 8, "assistant", "记得带水", 2),
        )
        val result = GroupMemoryBridge.directExcerpt(yi, rows, listOf(xu, yi), "我")!!
        assertTrue(result.contains("和小艺的私聊"))
        assertTrue(result.contains("我：周末想去海边"))
        assertTrue(result.contains("小艺：记得带水"))
        assertTrue(result.contains("真实聊天记录"))
        assertTrue(result.indexOf("周末想去海边") < result.indexOf("记得带水"))
    }

    @Test fun emptyAuthorizedRecordsMustNotBeInvented() {
        assertEquals(null, GroupMemoryBridge.directExcerpt(ta(8, "小艺"), emptyList(), listOf(ta(8, "小艺")), "我"))
    }

    @Test fun veryLongDirectHistoryRemainsBounded() {
        val yi = ta(8, "小艺")
        val rows = (1..40).map { n ->
            SharedMessageRow(n.toLong(), 50, "和小艺的私聊", 8, 8, "assistant", "聊天" + "文".repeat(300), n.toLong())
        }
        val result = GroupMemoryBridge.directExcerpt(yi, rows, listOf(yi), "我")!!
        assertTrue(result.length < 4000)
        assertTrue(result.contains("聊天"))
    }
}
