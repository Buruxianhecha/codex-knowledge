package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class FollowUpRulesTest {
    @Test fun onlyUnchangedReplyCanBeFollowed() {
        assertTrue(FollowUpRules.eligible(true, 10, 10, 1000, 1000, false))
        assertFalse(FollowUpRules.eligible(true, 10, 11, 1000, 1000, false))
        assertFalse(FollowUpRules.eligible(true, null, 10, 1000, 1000, false))
    }
    @Test fun disabledBusyAndOverdueStayQuiet() {
        assertFalse(FollowUpRules.eligible(false, 10, 10, 1000, 1000, false))
        assertFalse(FollowUpRules.eligible(true, 10, 10, 1000, 1000, true))
        assertTrue(FollowUpRules.eligible(true, 10, 10, 1000, 1000 + FollowUpRules.GRACE_MS, false))
        assertFalse(FollowUpRules.eligible(true, 10, 10, 1000, 1001 + FollowUpRules.GRACE_MS, false))
    }
    @Test fun settingsHaveStableOptions() {
        assertEquals(60, FollowUpRules.seconds(-1))
        assertEquals(30, FollowUpRules.seconds(30))
        assertEquals(180, FollowUpRules.seconds(180))
    }
    @Test fun promptAllowsSilenceAndForbidsPressure() {
        assertTrue(FollowUpRules.instruction.contains("SKIP"))
        assertTrue(FollowUpRules.instruction.contains("不要催回复"))
        assertTrue(FollowUpRules.instruction.contains("最多 3 个气泡"))
    }
}
