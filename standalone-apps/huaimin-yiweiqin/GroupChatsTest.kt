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

    @Test fun partialNameMustNotTargetAnotherMember() {
        val a = ta(1, "阿")
        val b = ta(2, "阿弦")
        assertEquals(listOf(b), GroupChats.mentioned("@阿弦 你觉得呢", listOf(a, b)))
        assertTrue(GroupChats.mentioned("@阿弦的一句话", listOf(a, b)).isEmpty())
    }

    @Test fun stableIdsSurviveRenamesAndSelectExactPerson() {
        val renamed = ta(1, "新名字")
        val other = ta(2, "阿弦")
        assertEquals(listOf(renamed), GroupChats.targeted("@旧名字 你怎么看", "1", listOf(renamed, other)))
        assertEquals(listOf(other), GroupChats.targeted("@阿弦 你怎么看", "2,999", listOf(renamed, other)))
    }

    @Test fun allMembersAndLegacyTextStillWork() {
        val a = ta(1, "阿弦")
        val b = ta(2, "小艺")
        assertEquals(listOf(a, b), GroupChats.targeted("@所有人 周末好", "1", listOf(a, b)))
        assertEquals(listOf(b), GroupChats.targeted("@小艺 周末好", null, listOf(a, b)))
        assertTrue(GroupChats.targeted("没有任何@", "", listOf(a, b)).isEmpty())
    }

    @Test fun mentionRequiresWholeMemberName() {
        val a = ta(1, "阿弦")
        val b = ta(2, "阿弦子")
        assertEquals(listOf(b), GroupChats.mentioned("@阿弦子 你怎么看？", listOf(a, b)))
        assertEquals(listOf(a), GroupChats.mentioned("@阿弦：你呢", listOf(a, b)))
    }

    @Test fun pickerSelectionsAreCheckedAgainstActualText() {
        val chosen = mapOf(1L to "阿弦", 2L to "小许")
        assertEquals(setOf(1L), GroupChats.selectedMentionIds("你好 @阿弦 我想问你", chosen))
        assertTrue(GroupChats.selectedMentionIds("你好，已经删掉了 @ 标签", chosen).isEmpty())
    }

    @Test fun storedMemberIdSurvivesRenamingAndNeverSelectsExMembers() {
        val renamed = ta(9, "小月")
        val other = ta(5, "阿弦")
        assertEquals(listOf(renamed), GroupChats.targeted("@阿弦 你怎么看", "9", listOf(renamed,other)))
        assertTrue(GroupChats.targeted("@阿弦", "9", listOf(other)).contains(other).not())
        assertEquals(listOf(other), GroupChats.targeted("@阿弦", null, listOf(other)))
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
        assertTrue(prompt.contains("最多三条"))
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

    @Test fun contextKeepsNewestRealExcerptUnderTightBudget() {
        val old = (1..30).map { i ->
            SharedMessageRow(i.toLong(), 10, "旧对话", 1, 1, "assistant",
                "过去的记录" + "旧".repeat(260), i.toLong())
        }
        val newest = SharedMessageRow(100, 10, "新对话", 1, 1, "assistant",
            "刚刚讨论的新问题", 100L)
        val result = GroupChats.sharedContext(old + newest, listOf(ta(1, "阿弦")), "我")!!
        assertTrue(result.contains("刚刚讨论的新问题"))
        assertTrue(result.length < 4000)
    }

    @Test fun skipRecognitionIsStrictEnough() {
        assertTrue(GroupChats.isSkip("SKIP"))
        assertTrue(GroupChats.isSkip("SKIP：现在不用说"))
        assertFalse(GroupChats.isSkip("我觉得还是说一句"))
    }
}
