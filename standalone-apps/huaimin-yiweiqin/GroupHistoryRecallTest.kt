package com.cleo.cleos.ai

import com.cleo.cleos.data.db.SharedMessageRow
import org.junit.Assert.*
import org.junit.Test

class GroupHistoryRecallTest {
    @Test fun onlyExplicitHistoricalQuestionsTriggerTheLookup() {
        assertTrue(GroupHistoryRecall.requested("上次我们聊过什么"))
        assertTrue(GroupHistoryRecall.requested("还记得群里说的台风吗"))
        assertFalse(GroupHistoryRecall.requested("今天天气怎么样"))
    }

    @Test fun historyIsMarkedAsExactSpeakerAndMessageIdentity() {
        val rows = listOf(
            SharedMessageRow(2, 3, "群聊", 11, 22, "assistant", "我记得台风", 1000),
            SharedMessageRow(1, 3, "群聊", 11, null, "user", "那个台风", 500),
        )
        val result = GroupHistoryRecall.format(rows, mapOf(22L to "小许"), "用户")!!
        assertTrue(result.contains("消息#1 | 用户"))
        assertTrue(result.contains("消息#2 | 小许"))
        assertTrue(result.contains("不是长期记忆摘要"))
    }

    @Test fun emptyOrRepeatedRowsNeverInventMessages() {
        assertNull(GroupHistoryRecall.format(emptyList(), emptyMap(), "我"))
        val row = SharedMessageRow(5, 3, "群聊", 11, null, "user", "记得", 1000)
        val text = GroupHistoryRecall.format(listOf(row, row), emptyMap(), "我")!!
        assertEquals(1, Regex("消息#5").findAll(text).count())
    }

    @Test fun autoModeRequiresHumanOptInAndResponse() {
        assertFalse(GroupAutoMode.shouldContinue(0, true, true, 40, 0))
        assertFalse(GroupAutoMode.shouldContinue(3, false, true, 40, 0))
        assertFalse(GroupAutoMode.shouldContinue(3, true, false, 40, 0))
        assertTrue(GroupAutoMode.shouldContinue(3, true, true, 40, 0))
        assertFalse(GroupAutoMode.shouldContinue(3, true, true, 0, 0))
        assertFalse(GroupAutoMode.shouldContinue(3, true, true, 40, GroupAutoMode.EXTRA_ROUNDS))
    }
}
