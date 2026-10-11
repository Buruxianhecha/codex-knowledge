package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class GallerySendSafetyTest {
    @Test fun suggestedTrashOnlyVisibleInItsConversation() {
        try {
            GalleryReviewQueue.propose(listOf(9L, 9L, 8L))
            GalleryReviewQueue.assign(2024L)
            assertEquals(listOf(9L, 8L), GalleryReviewQueue.ids.value)
            assertEquals(2024L, GalleryReviewQueue.owner.value)
        } finally {
            GalleryReviewQueue.clear()
        }
    }

    @Test fun clearingProposedTrashAlsoRemovesConversationOwner() {
        GalleryReviewQueue.propose(listOf(15L))
        GalleryReviewQueue.assign(3L)
        GalleryReviewQueue.clear()
        assertTrue(GalleryReviewQueue.ids.value.isEmpty())
        assertNull(GalleryReviewQueue.owner.value)
    }

    @Test fun invalidConversationDoesNotLeakIntoOtherChats() {
        try {
            GalleryReviewQueue.propose(listOf(1L))
            GalleryReviewQueue.assign(0L)
            assertNull(GalleryReviewQueue.owner.value)
        } finally { GalleryReviewQueue.clear() }
    }

    @Test fun suggestingMoreThanFiftyNeverExpandsCleanupQueue() {
        try {
            GalleryReviewQueue.propose((1L..80L).toList())
            GalleryReviewQueue.assign(8L)
            assertEquals(50, GalleryReviewQueue.ids.value.size)
        } finally { GalleryReviewQueue.clear() }
    }

    @Test fun newProposalCannotAccidentallyKeepOldConversation() {
        try {
            GalleryReviewQueue.propose(listOf(1L))
            GalleryReviewQueue.assign(7L)
            GalleryReviewQueue.propose(listOf(2L))
            assertNull(GalleryReviewQueue.owner.value)
            assertEquals(listOf(2L), GalleryReviewQueue.ids.value)
        } finally { GalleryReviewQueue.clear() }
    }
}
