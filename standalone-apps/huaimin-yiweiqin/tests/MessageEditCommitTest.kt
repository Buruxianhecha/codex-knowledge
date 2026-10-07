package com.cleo.cleos.ai

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MessageEditCommitTest {
    @Test fun successStagesCommitsAndAnswersExactlyOnceWithoutCleaningFiles() = runBlocking {
        val events = mutableListOf<String>()
        val id = commitMessageEdit<Long>({ events += "stage" }, { events += "commit"; 12L },
            { events += "rollback" }, { assertEquals(12L, it); events += "answer" })
        assertEquals(12L, id)
        assertEquals(listOf("stage", "commit", "answer"), events)
    }

    @Test fun interruptedFileCopyCleansOnlyTheNewFilesAndNeverCommits() = runBlocking {
        val files = mutableSetOf("original.jpg")
        val entered = CompletableDeferred<Unit>()
        var committed = false
        val job = launch {
            commitMessageEdit({ files += "edit.jpg"; entered.complete(Unit); awaitCancellation() },
                { committed = true }, { files -= "edit.jpg" }, { fail("must not answer") })
        }
        entered.await()
        job.cancelAndJoin()
        assertEquals(setOf("original.jpg"), files)
        assertFalse(committed)
    }

    @Test fun databaseFailureRollsBackStagedFilesAndDoesNotAnswer() = runBlocking {
        var cleaned = false
        try {
            commitMessageEdit({}, { error("Room rolled back") }, { cleaned = true }, { fail("must not answer") })
            fail("expected database failure")
        } catch (e: IllegalStateException) { assertEquals("Room rolled back", e.message) }
        assertTrue(cleaned)
    }

    @Test fun cancellationAfterStagingButBeforeCommitLeavesNoConversation() = runBlocking {
        var committed = false
        var cleaned = false
        val job = launch {
            commitMessageEdit({ currentCoroutineContext().cancel() }, { committed = true },
                { cleaned = true }, { fail("must not answer") })
        }
        job.join()
        assertFalse(committed)
        assertTrue(cleaned)
    }

    @Test fun cancellationDuringCommitKeepsAttachmentsAndStillRegistersTheReply() = runBlocking {
        var cleaned = false
        var answered = 0
        val release = CompletableDeferred<Unit>()
        val inside = CompletableDeferred<Unit>()
        val job = launch {
            commitMessageEdit<Long>({}, { inside.complete(Unit); release.await(); 12L },
                { cleaned = true }, { assertEquals(12L, it); answered++ })
        }
        inside.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, answered)
        assertFalse(cleaned)
    }

    @Test fun registrationFailureNeverDeletesFilesReferencedByACommittedConversation() = runBlocking {
        var cleaned = false
        try {
            commitMessageEdit<Long>({}, { 12L }, { cleaned = true }, { error("schedule failed") })
            fail("expected schedule failure")
        } catch (e: IllegalStateException) { assertEquals("schedule failed", e.message) }
        assertFalse(cleaned)
    }

    @Test fun editWaitsForOldReplyCancellationAndUsesTheSameFenceAsRecall() = runBlocking {
        val trace = mutableListOf<String>()
        val oldStopped = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fence = RecallCoordinator({ trace += "pause" }, { trace += "stop"; oldStopped.complete(Unit); release.await() }, { trace += "resume" })
        val edit = launch {
            fence.perform(7, { true }) {
                commitMessageEdit<Long>({ trace += "stage" }, { trace += "commit"; 12L },
                    { trace += "rollback" }, { trace += "answer" })
            }
        }
        oldStopped.await()
        var recalled = false
        val recall = launch { fence.perform(7, { true }) { recalled = true } }
        yield()
        assertFalse(recalled)
        assertEquals(listOf("pause", "stop"), trace)
        release.complete(Unit)
        edit.join()
        recall.join()
        assertEquals(listOf("pause", "stop", "stage", "commit", "answer", "resume"), trace.take(6))
        assertTrue(recalled)
    }
}
