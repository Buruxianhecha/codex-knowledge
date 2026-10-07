package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FreeTopicRulesTest {
    private fun at(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, 7, hour, minute, 0, 0, ZoneId.of("Asia/Shanghai"))

    @Test fun personalityIsDefaultAndHasBroadWindow() {
        val p = FreeTopicRules.level(999)
        assertEquals(FreeTopicRules.PERSONALITY, p.id)
        assertEquals(20, p.minMinutes)
        assertEquals(240, p.maxMinutes)
        assertEquals(24, p.dailyMax)
    }

    @Test fun manualLevelsMatchProductIntervals() {
        assertEquals(listOf(240, 30, 10), listOf(0, 1, 2).map { FreeTopicRules.level(it).minMinutes })
        assertEquals(listOf(360, 60, 20), listOf(0, 1, 2).map { FreeTopicRules.level(it).maxMinutes })
        assertEquals(listOf(3, 24, 60), listOf(0, 1, 2).map { FreeTopicRules.level(it).dailyMax })
    }

    @Test fun quietHoursHandleNormalCrossMidnightAndAllDay() {
        assertTrue(FreeTopicRules.quiet(at(23, 30), true, 1380, 480))
        assertTrue(FreeTopicRules.quiet(at(7, 59), true, 1380, 480))
        assertFalse(FreeTopicRules.quiet(at(8), true, 1380, 480))
        assertFalse(FreeTopicRules.quiet(at(16), false, 480, 480))
        assertTrue(FreeTopicRules.quiet(at(16), true, 480, 480))
    }

    @Test fun nextOpportunitySkipsQuietWindow() {
        val next = FreeTopicRules.next(at(22, 55), FreeTopicRules.level(2), true, 1380, 480, 0.0)
        val resume = java.time.Instant.ofEpochMilli(next).atZone(ZoneId.of("Asia/Shanghai"))
        assertEquals(8, resume.hour)
        assertEquals(0, resume.minute)
        assertEquals(at(22).toLocalDate().plusDays(1), resume.toLocalDate())
    }

    @Test fun localGatesAvoidModelCalls() {
        val now = 1_000_000L
        assertNotNull(FreeTopicRules.held(false, false, false, now - 99_999, now, 0, 0, 24))
        assertNotNull(FreeTopicRules.held(true, true, false, now - 99_999, now, 0, 0, 24))
        assertNotNull(FreeTopicRules.held(true, false, true, now - 99_999, now, 0, 0, 24))
        assertNotNull(FreeTopicRules.held(true, false, false, null, now, 0, 0, 24))
        assertNotNull(FreeTopicRules.held(true, false, false, now - FreeTopicRules.IDLE_MS + 1, now, 0, 0, 24))
        assertNotNull(FreeTopicRules.held(true, false, false, now - FreeTopicRules.IDLE_MS, now, LaterRules.UNANSWERED_MAX, 0, 24))
        assertNotNull(FreeTopicRules.held(true, false, false, now - FreeTopicRules.IDLE_MS, now, 0, 24, 24))
        assertNull(FreeTopicRules.held(true, false, false, now - FreeTopicRules.IDLE_MS, now, 0, 0, 24))
    }

    @Test fun promptMakesPersonalityAndSafetyPartOfDecision() {
        assertTrue(FreeTopicRules.instruction.contains("要不要主动"))
        assertTrue(FreeTopicRules.instruction.contains("性格"))
        assertTrue(FreeTopicRules.instruction.contains("SKIP"))
        assertTrue(FreeTopicRules.instruction.contains("怎么不回我"))
        assertTrue(FreeTopicRules.instruction.contains("最多 3 个短气泡"))
        assertTrue(FreeTopicRules.instruction.contains("send_message"))
    }
}
