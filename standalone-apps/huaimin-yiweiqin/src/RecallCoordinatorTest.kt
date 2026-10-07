package com.cleo.cleos.ai

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RecallCoordinatorTest {
    @Test fun commitWaitsForTheOldReplyAndTheFenceStaysUp() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        var paused = false
        var committed = false
        val flow = RecallCoordinator({ paused = true }, { entered.complete(Unit); release.await() }, { paused = false })
        val job = launch { flow.perform(1, { true }) { committed = true; assertTrue(paused) } }
        entered.await()
        assertTrue(paused)
        assertFalse(committed)
        release.complete(Unit)
        job.join()
        assertTrue(committed)
        assertFalse(paused)
    }

    @Test fun duplicateRecallRechecksEligibilityAfterTheFirstCommit() = runBlocking {
        var eligible = true
        var commits = 0
        val flow = RecallCoordinator({}, { yield() }, {})
        val jobs = List(2) { launch { flow.perform(1, { eligible }) { commits++; eligible = false } } }
        jobs.joinAll()
        assertEquals(1, commits)
    }

    @Test fun failedCommitAlwaysReleasesTheFence() = runBlocking {
        var resumed = false
        val flow = RecallCoordinator({}, {}, { resumed = true })
        try { flow.perform(1, { true }) { error("transaction failed") }; fail("expected failure") }
        catch (e: IllegalStateException) { assertEquals("transaction failed", e.message) }
        assertTrue(resumed)
        assertTrue(flow.perform(1, { true }) {})
    }

    @Test fun cancellationDuringStopReleasesTheFenceWithoutCommitting() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var committed = false
        var resumed = false
        val flow = RecallCoordinator({}, { entered.complete(Unit); awaitCancellation() }, { resumed = true })
        val job = launch { flow.perform(1, { true }) { committed = true } }
        entered.await()
        job.cancelAndJoin()
        assertFalse(committed)
        assertTrue(resumed)
    }

    @Test fun oneConversationDoesNotBlockAnother() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val flow = RecallCoordinator({}, { id -> if (id == 1L) { entered.complete(Unit); release.await() } }, {})
        val first = launch { flow.perform(1, { true }) {} }
        entered.await()
        assertTrue(withTimeout(1000) { flow.perform(2, { true }) {} })
        release.complete(Unit)
        first.join()
    }
}
