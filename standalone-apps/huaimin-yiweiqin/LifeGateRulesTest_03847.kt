package com.cleo.cleos.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeGateRulesTest {
    @Test fun cursorKeepsNewSameTimestampEventPending() {
        assertTrue(LifeGateRules.atOrBefore(100, 7, 100, 7))
        assertTrue(LifeGateRules.atOrBefore(99, 1000, 100, 7))
        assertFalse(LifeGateRules.atOrBefore(100, 8, 100, 7))
        assertFalse(LifeGateRules.atOrBefore(101, 1, 100, 7))
    }

    @Test fun unrelatedOrInvalidGoalTokenCannotResumeAnOldGoal() {
        assertEquals("goal-uuid" to 3, LifeGateRules.goalToken("goal:goal-uuid:3"))
        assertNull(LifeGateRules.goalToken("reply"))
        assertNull(LifeGateRules.goalToken("goal::3"))
        assertNull(LifeGateRules.goalToken("goal:goal-uuid:0"))
        assertNull(LifeGateRules.goalToken("goal:goal-uuid:x"))
        assertNull(LifeGateRules.goalToken("wake:goal-uuid:3"))
    }

    @Test fun nextAvailableBoundaryWinsAndLongOrMissingBoundaryHasFallback() {
        val now = 100_000L
        assertEquals(now + 5000, LifeGateRules.nextWake(now,
            listOf(now - 1, now + 10_000, now + 5000)))
        assertEquals(now + 900_000, LifeGateRules.nextWake(now, emptyList()))
        assertEquals(now + 900_000, LifeGateRules.nextWake(now, listOf(now + 9_000_000)))
    }

    @Test fun completionMetadataKeepsActorAndExactSameTimestampMessageId() {
        assertEquals(Triple(12L, 100L, 7L), LifeGateRules.receiptToken("life-cursor:12:100:7"))
        assertEquals(Triple(13L, 100L, 8L), LifeGateRules.receiptToken("life-cursor:13:100:8"))
        assertNull(LifeGateRules.receiptToken("weather-call-1"))
        assertNull(LifeGateRules.receiptToken("life-cursor:12:100"))
        assertNull(LifeGateRules.receiptToken("life-cursor:0:100:7"))
        assertNull(LifeGateRules.receiptToken("life-cursor:12:100:-1"))
        assertNull(LifeGateRules.receiptToken("life-cursor:12:x:7"))
    }
}
