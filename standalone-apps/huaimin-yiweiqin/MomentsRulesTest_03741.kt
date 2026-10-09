package com.cleo.cleos.data

import org.junit.Assert.*
import org.junit.Test

class MomentsRulesTest {
    @Test fun textOnlyPostAndPhotoOnlyPostAreValid() {
        MomentsRules.validatePost("今天的天空很好",0)
        MomentsRules.validatePost("",9)
    }
    @Test fun rejectEmptyPostTooLongTextAndTooManyImages() {
        assertThrows(IllegalArgumentException::class.java) { MomentsRules.validatePost(" ",0) }
        assertThrows(IllegalArgumentException::class.java) { MomentsRules.validatePost("x".repeat(2001),0) }
        assertThrows(IllegalArgumentException::class.java) { MomentsRules.validatePost("文字",10) }
    }
    @Test fun commentsMustBeRealAndBounded() {
        MomentsRules.validateReply("不错！")
        assertThrows(IllegalArgumentException::class.java) { MomentsRules.validateReply("") }
        assertThrows(IllegalArgumentException::class.java) { MomentsRules.validateReply("x".repeat(501)) }
    }
    @Test fun onlyPlainOwnedImageFileNamesAreValid() {
        assertTrue(MomentsRules.validImageName("moments-c1212.jpg"))
        assertTrue(MomentsRules.validImageName("moments-c1212.png"))
        assertFalse(MomentsRules.validImageName("../private.txt"))
        assertFalse(MomentsRules.validImageName("a/b.jpg"))
        assertFalse(MomentsRules.validImageName(".hidden.jpg"))
        assertFalse(MomentsRules.validImageName(""))
    }
    @Test fun defaultSnapshotAndSerializedPostRemainBackwardCompatible() {
        val snapshot=MomentsSnapshot()
        assertEquals(1,snapshot.version)
        assertTrue(snapshot.posts.isEmpty())
        val post=MomentPost(text="朋友圈内容")
        assertEquals(0L,post.authorId)
        assertFalse(post.liked)
        assertTrue(post.comments.isEmpty())
    }
}
