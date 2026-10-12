package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class GalleryToolBudgetTest {
    private fun g(offset: Int) = listOf("list_gallery_photos" to """{"offset":$offset}""")
    @Test fun albumCanPageHundredsOfTimes() {
        val budget = GalleryToolBudget()
        for (i in 0 until 500) assertTrue("offset=$i", budget.acceptCalls(g(i)))
        assertEquals(500, budget.galleryCalls)
        assertEquals(0, budget.ordinaryRounds)
    }
    @Test fun thousandsOfDifferentPhotoOperationsAreAllowed() {
        val budget = GalleryToolBudget()
        for (i in 0 until 2048) assertTrue(budget.acceptCalls(g(i)))
        assertEquals(2048, budget.galleryCalls)
        assertFalse(budget.acceptCalls(g(2048)))
    }
    @Test fun photoInspectionGetsIndependentAllowance() {
        val budget = GalleryToolBudget()
        repeat(25) { n ->
            assertTrue(budget.acceptCalls(listOf("inspect_gallery_photos" to """{"ids":[$n]}""")))
        }
    }
    @Test fun selfAvatarSelectionCountsAsPhotoOperation() {
        val budget = GalleryToolBudget()
        repeat(20) { n ->
            assertTrue(budget.acceptCalls(listOf("set_my_avatar" to """{"image":"#$n-1"}""")))
        }
    }
    @Test fun photoSendingAndCleanupAreAlsoPhotoOperations() {
        val budget = GalleryToolBudget()
        assertTrue(budget.acceptCalls(listOf("send_gallery_photos" to """{"ids":[1]}""")))
        assertTrue(budget.acceptCalls(listOf("prepare_gallery_cleanup" to """{"ids":[2]}""")))
        assertEquals(2, budget.galleryCalls)
    }
    @Test fun ordinaryToolsKeepExistingLimit() {
        val budget = GalleryToolBudget()
        repeat(5) { assertTrue(budget.acceptCalls(listOf("list_todos" to "{}"))) }
        assertFalse(budget.acceptCalls(listOf("list_todos" to "{}")))
    }
    @Test fun repeatedIdenticalGalleryCallsStop() {
        val budget = GalleryToolBudget()
        repeat(3) { assertTrue(budget.acceptCalls(g(0))) }
        assertFalse(budget.acceptCalls(g(0)))
        assertTrue(budget.lastError.contains("重复"))
    }
    @Test fun changingOffsetResetsRepeatedCallDetector() {
        val budget = GalleryToolBudget()
        repeat(3) { assertTrue(budget.acceptCalls(g(0))) }
        assertTrue(budget.acceptCalls(g(30)))
        assertTrue(budget.acceptCalls(g(0)))
    }
    @Test fun mixedRoundsConsumeOrdinaryQuota() {
        val budget = GalleryToolBudget(ordinaryRoundLimit=1)
        assertTrue(budget.acceptCalls(listOf("list_gallery_photos" to "{}",
            "list_todos" to "{}")))
        assertFalse(budget.acceptCalls(listOf("count_gallery_photos" to "{}",
            "list_todos" to "{}")))
    }
    @Test fun batchCallsCountIndividually() {
        val budget = GalleryToolBudget(galleryCallLimit=3)
        assertTrue(budget.acceptCalls(listOf(
            "list_gallery_photos" to "one", "inspect_gallery_photos" to "two")))
        assertTrue(budget.acceptCalls(g(2)))
        assertFalse(budget.acceptCalls(g(3)))
    }
    @Test fun invalidUnknownToolDoesNotReceiveGalleryBudget() {
        assertFalse(GalleryToolBudget.isGalleryOperation("delete_all_photos"))
    }
    @Test fun countPhotosRecognizedAsGalleryTool() {
        assertTrue(GalleryToolBudget.isGalleryOperation("count_gallery_photos"))
    }
}
