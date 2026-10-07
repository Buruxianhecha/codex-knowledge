package com.cleo.cleos.ai

import com.cleo.cleos.data.AppSettings
import com.cleo.cleos.data.MessageImage
import com.cleo.cleos.data.MessageImages
import com.cleo.cleos.data.StickerBook
import com.cleo.cleos.data.StickerText
import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.MessageEntity
import com.cleo.cleos.data.db.StickerEntity
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StickerVisionTest {
    private val now = ZonedDateTime.of(2026, 10, 5, 1, 5, 0, 0, ZoneId.of("Asia/Shanghai"))
    private val ta = CompanionEntity(id = 1, apiBaseUrl = "", apiModel = "", createdAt = 0)
    private val sticker = StickerEntity(
        id = 1, name = "表情", file = "sticker-sunset.jpg",
        width = 512, height = 340, createdAt = 1,
    )
    private val other = StickerEntity(
        id = 2, name = "另一张", file = "sticker-other.png",
        width = 100, height = 100, createdAt = 2,
    )
    private val book = StickerBook(listOf(sticker, other))

    private fun user(id: Long, text: String, vararg photos: String) = MessageEntity(
        id = id, conversationId = 1, role = "user", content = text, createdAt = id,
        images = MessageImages.encode(photos.map { MessageImage(it, 100, 100) }),
    )

    private fun assistant(id: Long) = MessageEntity(
        id = id, conversationId = 1, role = "assistant", content = "嗯", createdAt = id,
    )

    private fun prompt(history: List<MessageEntity>, images: Boolean = true, send: Boolean = true, stickers: StickerBook = book) =
        Prompt.messages(AppSettings(), ta, history, now, images = images, stickers = stickers, sendStickers = send)

    @Test
    fun aGenericStickerNameStillAttachesTheActualPicture() {
        val sent = user(1, StickerText.token("表情"))
        val message = prompt(listOf(sent)).last()
        assertNull("The stored sticker is still a token, not a duplicate photo", sent.images)
        assertEquals(listOf("sticker-sunset.jpg"), message.images)
        assertTrue(message.content.contains("附表情图片"))
        assertTrue(message.content.endsWith("[[sticker:表情]]"))
    }

    @Test
    fun sendingStickersIsIndependentOfTheTasPermissionToSendThem() {
        val history = listOf(user(1, StickerText.token("表情")))
        for (send in listOf(false, true)) {
            assertEquals(listOf("sticker-sunset.jpg"), prompt(history, send = send).last().images)
        }
        assertTrue(prompt(history, send = false).last().content.contains("发了一张表情包：表情"))
    }

    @Test
    fun aStickerAloneEnablesVisionButUnsentCollectionPicturesDoNot() {
        assertTrue(MessagePictures.hasImages(listOf(user(1, StickerText.token("表情"))), book))
        assertFalse(MessagePictures.hasImages(listOf(user(1, "你好")), book))
        val messages = prompt(listOf(user(1, StickerText.token("表情"))))
        assertFalse(messages.flatMap { it.images }.contains("sticker-other.png"))
    }

    @Test
    fun oldNamesAndMultipleTokensResolveToTheRightPicturesInOrder() {
        val renamed = sticker.copy(name = "钱桥晚霞", aliases = StickerBook.encodeAliases(listOf("表情")))
        val renamedBook = StickerBook(listOf(renamed, other))
        val sent = user(1, "看 [[sticker:表情]][[sticker:另一张]][[sticker:表情]]")
        assertEquals(
            listOf("sticker-sunset.jpg", "sticker-other.png"),
            prompt(listOf(sent), stickers = renamedBook).last().images,
        )
    }

    @Test
    fun stickerImagesShareTheExistingRecentPictureBudget() {
        val old = user(1, "之前的照片", "a.jpg", "b.jpg", "c.jpg")
        val latest = user(3, "对比 [[sticker:表情]]", "d.jpg", "e.jpg")
        val messages = prompt(listOf(old, assistant(2), latest)).filter { it.role == "user" }
        assertTrue(messages.first().images.isEmpty())
        assertTrue(messages.first().content.contains("早先发的 3 张图"))
        assertEquals(listOf("d.jpg", "e.jpg", "sticker-sunset.jpg"), messages.last().images)
        assertTrue(messages.sumOf { it.images.size } <= Prompt.MAX_IMAGES)
        assertEquals(listOf("sticker-sunset.jpg"), prompt(listOf(user(1, StickerText.token("表情")), assistant(2), user(3, "图里写的什么")))[1].images)
    }

    @Test
    fun aTextOnlyFallbackUsesNamesWithoutClaimingItReceivedThePicture() {
        val history = listOf(user(1, StickerText.token("表情")))
        for (send in listOf(false, true)) {
            val message = prompt(history, images = false, send = send).last()
            assertTrue(message.images.isEmpty())
            assertTrue(message.content.contains("没有附表情图片"))
            assertFalse(message.content.contains("（附表情图片"))
        }
    }

    @Test
    fun missingStickersAssistantTokensAndPrivateNotesDoNotAttachImages() {
        val missing = user(1, StickerText.token("已经删除"))
        assertFalse(MessagePictures.hasImages(listOf(missing), book))
        assertTrue(prompt(listOf(missing)).last().images.isEmpty())
        val theirs = assistant(2).copy(content = StickerText.token("表情"))
        val privateNote = user(3, StickerText.token("表情"), "private.jpg").copy(note = "只给用户看的卡片")
        assertFalse(MessagePictures.hasImages(listOf(theirs, privateNote), book))
        assertTrue(MessagePictures.files(privateNote, book).isEmpty())
    }

    @Test
    fun theWireRequestIncludesImageContentAlongsideTheStickerToken() {
        val imageUrl = "data:image/jpeg;base64,TEST"
        val prepared = prompt(listOf(user(1, StickerText.token("表情")))).map { message ->
            message.copy(images = message.images.map { file ->
                assertEquals("sticker-sunset.jpg", file)
                imageUrl
            })
        }
        val body = requestBody("vision-model", prepared, emptyList())
        val last = body["messages"]!!.jsonArray.last().jsonObject
        val parts = last["content"]!!.jsonArray
        assertEquals("user", last["role"]!!.jsonPrimitive.content)
        assertEquals("text", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(parts[0].jsonObject["text"]!!.jsonPrimitive.content.contains("[[sticker:表情]]"))
        assertEquals("image_url", parts[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(imageUrl, parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }
}
