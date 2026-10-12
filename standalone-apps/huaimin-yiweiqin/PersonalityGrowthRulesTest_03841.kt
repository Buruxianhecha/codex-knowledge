package com.cleo.cleos.ai

import com.cleo.cleos.data.db.MemoryEntity
import org.junit.Assert.*
import org.junit.Test

class PersonalityGrowthRulesTest {
    private fun note(
        id: Long = 4,
        companionId: Long = 1,
        name: String = PersonalityGrowthRules.NAME,
        pinned: Boolean = false,
        source: String = MemoryEntity.SOURCE_AI,
    ) = MemoryEntity(
        id = id, companionId = companionId, name = name,
        kind = "self", summary = "我逐渐喜欢更沉稳的谈话方式。",
        createdAt = 100, updatedAt = 200, pinned = pinned, source = source,
    )

    @Test fun existingManualPersonalityIsNeverAStorageTarget() {
        assertEquals("AI 自主成长的性格", PersonalityGrowthRules.NAME)
    }
    @Test fun onlyAiGrowthRowIsRecognized() {
        assertNull(PersonalityGrowthRules.learned(listOf(note(name="用户自己的记忆"))))
        assertNull(PersonalityGrowthRules.learned(listOf(note(source=MemoryEntity.SOURCE_ME))))
        assertNotNull(PersonalityGrowthRules.learned(listOf(note())))
    }
    @Test fun memoryOffStillIncludesSelfGrowth() {
        assertEquals(1, PersonalityGrowthRules.forPrompt(listOf(note(), note(name="朋友兴趣")), false).size)
    }
    @Test fun memoryOnKeepsNormalMemories() {
        assertEquals(2, PersonalityGrowthRules.forPrompt(listOf(note(), note(name="读书兴趣")), true).size)
    }
    @Test fun fewMessagesDoNotCreateBillableReflection() {
        assertFalse(PersonalityGrowthRules.due(4, null, 123456L))
    }
    @Test fun enoughHistoryPermitsFirstReflection() {
        assertTrue(PersonalityGrowthRules.due(PersonalityGrowthRules.MIN_NEW_MESSAGES, null, 123456L))
    }
    @Test fun cooldownStopsTooFrequentGrowth() {
        assertFalse(PersonalityGrowthRules.due(100, 1000L, 2000L))
    }
    @Test fun oldGrowthWithNewConversationsBecomesDue() {
        assertTrue(PersonalityGrowthRules.due(18, 1000L, PersonalityGrowthRules.MIN_INTERVAL_MS+1001L))
    }
    @Test fun rejectsExtremelyShortOrOverlongNewPersona() {
        assertNull(PersonalityGrowthRules.clean("喜欢聊天"))
        assertNull(PersonalityGrowthRules.clean("喜".repeat(1101)))
    }
    @Test fun normalDistinctPersonalityCanBeSaved() {
        assertNotNull(PersonalityGrowthRules.clean("我更喜欢慢慢组织思路，会依据对方的语气调整节奏，温和而不敷衍。群聊时会主动听别人的意见，不轻易插嘴。遇到有趣的技术问题时会追问原理，也愿意承认自己的不确定之处。"))
    }
    @Test fun neverReplaceUserAuthoredPresetInstruction() {
        val p = PersonalityGrowthRules.prompt("喜欢安静、明确表达","更会听别人说话")
        assertTrue(p.contains("喜欢安静、明确表达"))
        assertTrue(p.contains("更会听别人说话"))
        assertTrue(p.contains("不许覆盖"))
    }
}
