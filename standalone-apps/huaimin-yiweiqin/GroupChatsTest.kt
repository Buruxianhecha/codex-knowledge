package com.cleo.cleos.ai

import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.MessageEntity
import com.cleo.cleos.data.db.SharedMessageRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatsTest {
    private fun ta(id: Long, name: String) = CompanionEntity(id = id, name = name, apiBaseUrl = "x", apiModel = "m", createdAt = 0)

    @Test fun mentionSelectsOnlyNamedMembers() {
        val a = ta(1, "阿弦")
        val b = ta(2, "怀民")
        assertEquals(listOf(b), GroupChats.mentioned("@怀民 你怎么看", listOf(a, b)))
        assertEquals(listOf(a, b), GroupChats.mentioned("@所有人 今晚吃什么", listOf(a, b)))
        assertTrue(GroupChats.mentioned("你们觉得呢", listOf(a, b)).isEmpty())
    }

    @Test fun otherAssistantBecomesSomeoneElsesTurn() {
        val history = listOf(
            MessageEntity(id = 1, conversationId = 9, role = "user", content = "你们呢", createdAt = 1),
            MessageEntity(id = 2, conversationId = 9, role = "assistant", content = "我觉得可以", createdAt = 2, senderCompanionId = 1),
            MessageEntity(id = 3, conversationId = 9, role = "assistant", content = "我再想想", createdAt = 3, senderCompanionId = 2),
        )
        val shaped = GroupChats.historyFor(history, selfId = 2, names = mapOf(1L to "阿弦", 2L to "怀民"), fallbackSpeakerId = 1)
        assertEquals("user", shaped[1].role)
        assertTrue(shaped[1].content.contains("阿弦"))
        assertEquals("assistant", shaped[2].role)
    }

    @Test fun instructionAllowsNaturalSilenceAndProtectsIdentity() {
        val a = ta(1, "阿弦")
        val b = ta(2, "怀民")
        val prompt = GroupChats.turnInstruction(a, listOf(a, b), targeted = false)
        assertTrue(prompt.contains("SKIP"))
        assertTrue(prompt.contains("不要替别的角色发言"))
        assertTrue(prompt.contains("最多两条"))
        assertFalse(GroupChats.turnInstruction(a, listOf(a, b), targeted = true).contains("为了轮到你而硬说"))
    }

    @Test fun sharedContextLabelsRealSpeakers() {
        val rows = listOf(
            SharedMessageRow(1, 10, "和阿弦的对话", 1, null, "user", "今天有点累", 1),
            SharedMessageRow(2, 10, "和阿弦的对话", 1, 1, "assistant", "那早点休息", 2),
        )
        val text = GroupChats.sharedContext(rows, listOf(ta(1, "阿弦")), "林")!!
        assertTrue(text.contains("林：今天有点累"))
        assertTrue(text.contains("阿弦：那早点休息"))
        assertTrue(text.contains("不要编造"))
    }

    @Test fun skipRecognitionIsStrictEnough() {
        assertTrue(GroupChats.isSkip("SKIP"))
        assertTrue(GroupChats.isSkip("SKIP：现在不用说"))
        assertFalse(GroupChats.isSkip("我觉得还是说一句"))
    }
}
