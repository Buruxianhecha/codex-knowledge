package com.cleo.cleos.ai

import com.cleo.cleos.data.db.MemoryEntity
import org.junit.Assert.*
import org.junit.Test

class PersonalityGrowthConfigTest {
    @Test fun defaultsMatchOriginalVersion() {
        val c = PersonalityGrowthConfig()
        assertTrue(c.enabled)
        assertEquals(14, c.minMessages)
        assertEquals(6, c.minHours)
        assertTrue(c.manualEnabled)
    }
    @Test fun eachSettingCanChangeSeparately() {
        val c = PersonalityGrowthConfig().copy(enabled=false, minMessages=5, minHours=0, manualEnabled=false)
        assertFalse(c.enabled)
        assertFalse(c.manualEnabled)
        assertEquals(5, c.minMessages)
        assertEquals(0, c.minHours)
    }
    @Test fun oneMessageIsAllowed() {
        assertEquals(1, PersonalityGrowthConfig(minMessages=1).normalized().minMessages)
    }
    @Test fun hundredMessagesAreAllowed() {
        assertEquals(100, PersonalityGrowthConfig(minMessages=100).normalized().minMessages)
    }
    @Test fun zeroHourCooldownIsAllowed() {
        assertEquals(0, PersonalityGrowthConfig(minHours=0).normalized().minHours)
    }
    @Test fun fortyEightHoursAreAllowed() {
        assertEquals(48, PersonalityGrowthConfig(minHours=48).normalized().minHours)
    }
    @Test fun outOfRangeValuesAreClamped() {
        val c = PersonalityGrowthConfig(minMessages=9999,minHours=-3).normalized()
        assertEquals(100,c.minMessages)
        assertEquals(0,c.minHours)
    }
    @Test fun customizedThresholdActuallyChangesDueCalculation() {
        assertTrue(PersonalityGrowthRules.due(5, null, 1L, minimum=5, intervalMs=0L))
        assertFalse(PersonalityGrowthRules.due(4, null, 1L, minimum=5, intervalMs=0L))
    }
    @Test fun customizedCooldownActuallyChangesDueCalculation() {
        assertTrue(PersonalityGrowthRules.due(14, 1000L, 4600000L, intervalMs=3600000L))
        assertFalse(PersonalityGrowthRules.due(14, 1000L, 100000L, intervalMs=3600000L))
    }
    @Test fun configRowCannotBeMistakenForAIGrowth() {
        val row = MemoryEntity(companionId=7,kind="self",
            name=PersonalityGrowthConfigStore.NAME,summary="{}",
            source=MemoryEntity.SOURCE_ME,createdAt=1,updatedAt=1)
        assertTrue(PersonalityGrowthConfigStore.isConfig(row))
        assertNull(PersonalityGrowthRules.learned(listOf(row)))
        assertTrue(PersonalityGrowthRules.forPrompt(listOf(row),true).isEmpty())
        assertTrue(PersonalityGrowthRules.forPrompt(listOf(row),false).isEmpty())
    }
    @Test fun anotherCharactersMemoryCannotMatchConfigNameWithoutOwnerSource() {
        val row = MemoryEntity(companionId=8,kind="self",
            name=PersonalityGrowthConfigStore.NAME,summary="{}",
            source=MemoryEntity.SOURCE_AI,createdAt=1,updatedAt=1)
        assertFalse(PersonalityGrowthConfigStore.isConfig(row))
    }
    @Test fun originalDefaultDueSemanticsStillWork() {
        assertFalse(PersonalityGrowthRules.due(13, null, 1000L))
        assertTrue(PersonalityGrowthRules.due(14, null, 1000L))
    }
}
