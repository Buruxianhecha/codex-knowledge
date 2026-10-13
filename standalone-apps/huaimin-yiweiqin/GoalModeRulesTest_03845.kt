package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class GoalModeRulesTest {
    private fun sample(status:String="RUNNING",inFlight:GoalAction?=null)=GoalRecord(
        id="g-1",conversationId=7,companionId=9,description="做研究",constraints="",
        criteria=listOf(GoalCriterion("列出真实来源")),status=status,inFlight=inFlight
    )
    @Test fun goalHasAllNineRequiredStates() {
        assertEquals(9,GoalRules.states.size)
        assertTrue(GoalRules.states.containsAll(listOf("PENDING","RUNNING","VERIFYING",
            "WAITING_INPUT","PAUSED","BLOCKED","COMPLETED","FAILED","CANCELED")))
    }
    @Test fun noEvidenceMeansNeverCompleted() {
        assertFalse(GoalRules.canComplete(listOf(GoalCriterion("交付真实文件"))))
        assertFalse(GoalRules.canComplete(emptyList()))
    }
    @Test fun everyRequiredAcceptanceMustPass() {
        assertFalse(GoalRules.canComplete(listOf(
            GoalCriterion("一",status="PASSED",evidence="tool:123"),
            GoalCriterion("二",status="UNVERIFIED"))))
        assertTrue(GoalRules.canComplete(listOf(
            GoalCriterion("一",status="PASSED",evidence="tool:123"),
            GoalCriterion("二",status="PASSED",evidence="用户手动确认"))))
    }
    @Test fun appRestartPausesRunningGoal() {
        val g=GoalRules.resumeState(sample())
        assertEquals("PAUSED",g.status)
        assertNull(g.runningSince)
    }
    @Test fun appRestartRequiresReconciliationOfInFlightExternalCall() {
        val action=GoalAction("mcp_send", "ab12",5)
        val g=GoalRules.resumeState(sample(inFlight=action))
        assertEquals("BLOCKED",g.status)
        assertEquals(action,g.inFlight)
    }
    @Test fun stoppedAndCompletedDoNotRestart() {
        assertEquals("CANCELED",GoalRules.resumeState(sample("CANCELED")).status)
        assertEquals("COMPLETED",GoalRules.resumeState(sample("COMPLETED")).status)
        assertEquals("PAUSED",GoalRules.resumeState(sample("PAUSED")).status)
    }
    @Test fun budgetEnforcesSafeBounds() {
        assertEquals(1,GoalBudget(0,0).safe().maxRounds)
        assertEquals(200,GoalBudget(999,999).safe().maxRounds)
        assertEquals(240,GoalBudget(999,999).safe().maxMinutes)
    }
    @Test fun fingerprintsUseExactToolAndArguments() {
        val a=GoalRules.fingerprint("send_message", """{"text":"hi"}""")
        assertEquals(a,GoalRules.fingerprint("send_message", """{"text":"hi"}"""))
        assertNotEquals(a,GoalRules.fingerprint("send_message", """{"text":"bye"}"""))
        assertNotEquals(a,GoalRules.fingerprint("send_web_image", """{"text":"hi"}"""))
        assertEquals(24,a.length)
    }
    @Test fun newUnknownToolRequiresUserAuthorization() {
        assertTrue(GoalRules.sideEffect("unknown_mcp_tool",true))
        assertTrue(GoalRules.sideEffect("pay",false))
        assertTrue(GoalRules.sideEffect("send_message"))
        assertTrue(GoalRules.sideEffect("send_web_image"))
    }
    @Test fun knownReadToolsDoNotRequireExtraGoalPermission() {
        assertFalse(GoalRules.sideEffect("get_weather"))
        assertFalse(GoalRules.sideEffect("read_calendar"))
        assertFalse(GoalRules.sideEffect("search_web_avatar_images"))
    }
    @Test fun modelCannotOverrideTerminalStateByString() {
        val g=sample("CANCELED")
        assertTrue(g.status in GoalRules.terminal)
        assertFalse(GoalRules.canComplete(g.criteria))
    }
    @Test fun goalTracksConversationAndCharacterSeparately() {
        val one=sample()
        assertEquals(7L,one.conversationId)
        assertEquals(9L,one.companionId)
        assertNotEquals(one.copy(companionId=10L),one)
        assertNotEquals(one.copy(conversationId=8L),one)
    }
    @Test fun budgetTracksCallsNotFakeTokenCosts() {
        val g=sample()
        assertEquals(0,g.rounds)
        assertEquals(0L,g.elapsedMs)
        assertEquals(18,g.budget.maxRounds)
    }
    @Test fun stateHasVersionAndImmutableReceipts() {
        val g=sample()
        assertEquals(1,g.version)
        assertTrue(g.receipts.isEmpty())
        assertTrue(g.events.isEmpty())
        assertNull(g.inFlight)
    }
}
