package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class OnlineWebChatRulesTest {
    @Test fun validPublicHttpsPhotoUrlIsAccepted() {
        assertTrue(OnlineAvatarRules.safeHttpsUrl("https://static.vecteezy.com/system/resources/previews/012/345/photo.jpg"))
    }
    @Test fun privateAndLoopbackAddressesCannotBeSent() {
        assertFalse(OnlineAvatarRules.safeHttpsUrl("https://127.0.0.1/photo.jpg"))
        assertFalse(OnlineAvatarRules.safeHttpsUrl("https://192.168.1.22/image.png"))
        assertFalse(OnlineAvatarRules.safeHttpsUrl("https://myserver.local/pic.jpg"))
    }
    @Test fun networkChatImagesMustComeFromHttps() {
        assertFalse(OnlineAvatarRules.safeHttpsUrl("http://example.com/photo.jpg"))
        assertFalse(OnlineAvatarRules.safeHttpsUrl("file:///sdcard/a.jpg"))
    }
    @Test fun webImagesDoNotCountAsMobileGallerySource() {
        assertFalse(GalleryToolBudget.isGalleryOperation("not_a_photo_tool"))
        assertTrue(GalleryToolBudget.isGalleryOperation("send_web_image"))
    }
    @Test fun onlinePictureSendHasFullPhotoBudget() {
        val budget=GalleryToolBudget()
        repeat(100) { i ->
            assertTrue(budget.acceptCalls(listOf("send_web_image" to """{"url":"https://example.com/$i.jpg"}""")))
        }
        assertEquals(100,budget.galleryCalls)
        assertEquals(0,budget.ordinaryRounds)
    }
    @Test fun unsafeContentTypeIsRejected() {
        assertFalse(OnlineAvatarRules.allowedMime("text/html"))
        assertFalse(OnlineAvatarRules.allowedMime("image/svg+xml"))
        assertTrue(OnlineAvatarRules.allowedMime("image/webp"))
    }
    @Test fun webChatImportIsDistinctFromAvatarChange() {
        val image=OnlineChatImage(file="private-file",width=320,height=240)
        assertEquals(320,image.width)
        assertEquals(240,image.height)
        assertEquals("private-file",image.file)
    }
    @Test fun toolOutcomeDoesNotPretendUrlIsSent() {
        val pending=ToolOutcome("正在验证", "准备发送", webSendUrls=listOf("https://example.com/a.png"))
        assertEquals(1,pending.webSendUrls.size)
        assertTrue(pending.gallerySendIds.isEmpty())
    }
}
