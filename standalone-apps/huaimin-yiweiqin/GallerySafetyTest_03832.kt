package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class GallerySafetyTest {
    private fun photo(id: Long) = GalleryPhoto(id,"test_$id.jpg","Camera",1720000000000L,1024)
    @Test fun toolMetadataMustNotPretendToHaveSeenImages() {
        val text=GalleryResultFormatter.display(listOf(photo(101)),GalleryAccess.All)
        assertTrue(text.contains("不代表已经看过"))
        assertTrue(text.contains("元数据"))
        assertTrue(text.contains("inspect_gallery_photos"))
        assertTrue(text.contains("ID=101"))
    }
    @Test fun selectedPermissionDoesNotBecomeFullAccess() {
        val text=GalleryResultFormatter.display(listOf(photo(10)),GalleryAccess.Selected)
        assertTrue(text.contains("部分已选照片"))
    }
    @Test fun nothingFoundIsNotMisreportedAsAccessToEveryone() {
        val text=GalleryResultFormatter.display(emptyList(),GalleryAccess.Selected)
        assertTrue(text.contains("未检索到"))
    }
    @Test fun cleanupOnlyCreatesNonPersistentProposalAndDeduplicates() {
        try {
            GalleryReviewQueue.propose(listOf(1,2,2,3))
            assertEquals(listOf(1L,2L,3L),GalleryReviewQueue.ids.value)
        } finally {
            GalleryReviewQueue.clear()
        }
        assertEquals(emptyList<Long>(),GalleryReviewQueue.ids.value)
    }
    @Test fun modelCannotProposeMoreThanFiftyPhotosAtOnce() {
        try {
            GalleryReviewQueue.propose((1L..300L).toList())
            assertEquals(50,GalleryReviewQueue.ids.value.size)
        } finally {
            GalleryReviewQueue.clear()
        }
    }
}
