package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class GalleryCountTest {
    @Test fun fullPermissionGivesActualAccessibleMediaStoreImageCount() {
        val result = GalleryCountText.render(2734, GalleryAccess.All)
        assertTrue(result.contains("2734"))
        assertTrue(result.contains("全部照片"))
        assertTrue(result.contains("不包括视频"))
        assertFalse(result.contains("照片明细"))
    }

    @Test fun selectedPermissionNeverMisreportsAsEntireDeviceCount() {
        val result = GalleryCountText.render(23, GalleryAccess.Selected)
        assertTrue(result.contains("23"))
        assertTrue(result.contains("仅部分照片"))
        assertTrue(result.contains("不是整个相册"))
    }

    @Test fun emptyAlbumIsLegitimateZeroNotAnError() {
        val result = GalleryCountText.render(0, GalleryAccess.All, album = "Camera")
        assertTrue(result.contains("0 张"))
        assertTrue(result.contains("Camera"))
    }

    @Test fun filteredCountMentionsFilter() {
        val result = GalleryCountText.render(8, GalleryAccess.All, query = "IMG", album = "Screenshots")
        assertTrue(result.contains("8 张"))
        assertTrue(result.contains("IMG"))
        assertTrue(result.contains("Screenshots"))
    }

    @Test fun countFormatterRejectsMissingSystemPermission() {
        assertTrue(GalleryCountText.render(0, GalleryAccess.None).contains("尚未授权"))
        assertFalse(GalleryCountText.render(0, GalleryAccess.None).contains("整个相册"))
    }

    @Test fun maliciouslyLargeCountIsNeverOverstated() {
        assertTrue(GalleryCountText.render(0, GalleryAccess.Selected).contains("0 张"))
        assertTrue(GalleryCountText.render(-1, GalleryAccess.All).contains("统计失败"))
    }
}
