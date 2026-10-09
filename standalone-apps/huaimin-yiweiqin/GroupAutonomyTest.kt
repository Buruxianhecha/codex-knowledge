package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class GroupAutonomyTest {
    private val now=2_000_000_000L
    @Test fun onlyExplicitOptInAndDaytime() {
        assertFalse(GroupAutonomy.allowed(0,12,now-7200000,now-4000000,0,now,8))
        assertFalse(GroupAutonomy.allowed(3,23,now-7200000,now-4000000,0,now,8))
        assertTrue(GroupAutonomy.allowed(3,12,now-7200000,now-4000000,0,now,8))
    }
    @Test fun noBlindUnansweredOrExpiredConversation() {
        assertFalse(GroupAutonomy.allowed(3,12,now-7200000,now-4000000,2,now,8))
        assertFalse(GroupAutonomy.allowed(3,12,now-90000000,now-4000000,0,now,8))
        assertFalse(GroupAutonomy.allowed(3,12,now-7200000,now-400000,0,now,8))
        assertFalse(GroupAutonomy.allowed(3,12,now-7200000,now-4000000,0,now,0))
    }
}
