package com.cleo.cleos.ai

import com.cleo.cleos.data.*
import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.MessageEntity
import com.cleo.cleos.data.db.StickerEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RecallPromptTest {
    private val ta = CompanionEntity(id = 1, name = "星", apiBaseUrl = "", apiModel = "", createdAt = 0)
    private val now = ZonedDateTime.of(2026, 10, 5, 22, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
    private fun user(id: Long = 1, text: String = "已撤回的原文") =
        MessageEntity(id = id, conversationId = 1, role = "user", content = text, createdAt = id)
    private fun prompt(history: List<MessageEntity>) = Prompt.messages(AppSettings(), ta, history, now, images = true)

    @Test fun firstMessageWithdrawalStillCreatesARealUserTurn() {
        val original = user()
        val out = prompt(listOf(Recalls.withdrawn(original), Recalls.event(original, 3)))
        assertEquals(listOf("system", "user"), out.map { it.role })
        assertTrue(out.last().content.contains("撤回了一条文字消息"))
        assertTrue(out.last().content.contains("自然简短回应"))
        assertFalse(out.any { it.content.contains(original.content) })
    }

    @Test fun withdrawalAfterAnAnswerIsAnotherUserTurn() {
        val original = user()
        val answer = user(2, "好呀").copy(role = "assistant")
        val out = prompt(listOf(Recalls.withdrawn(original), answer, Recalls.event(original, 3)))
        assertEquals("user", out.last().role)
        assertTrue(out.last().content.contains("不要继续执行"))
    }

    @Test fun recalledPicturesAreNeverAttached() {
        val original = user(text = "").copy(images = MessageImages.encode(listOf(MessageImage("secret.jpg", 100, 100))))
        val out = prompt(listOf(Recalls.withdrawn(original), Recalls.event(original, 3)))
        assertTrue(out.all { it.images.isEmpty() })
        assertTrue(out.last().content.contains("图片消息"))
        assertFalse(out.any { it.content.contains("secret.jpg") })
    }

    @Test fun recalledStickerDoesNotReachTheVisionEncoder() {
        val original = user(text = StickerText.token("亲亲"))
        val book = StickerBook(listOf(StickerEntity(id = 1, name = "亲亲", file = "private.png", width = 50, height = 50, createdAt = 0)))
        val out = Prompt.messages(AppSettings(), ta, listOf(Recalls.withdrawn(original), Recalls.event(original, 3)), now,
            images = true, stickers = book)
        assertTrue(out.all { it.images.isEmpty() })
        assertTrue(out.last().content.contains("表情包消息"))
        assertFalse(out.last().content.contains("private.png"))
    }

    @Test fun recalledVoiceDoesNotExposeItsTranscript() {
        val original = user(text = "语音中的秘密").copy(audio = "voice metadata")
        val out = prompt(listOf(Recalls.withdrawn(original), Recalls.event(original, 3)))
        assertTrue(out.last().content.contains("语音消息"))
        assertFalse(out.any { it.content.contains(original.content) })
    }

    @Test fun consecutiveWithdrawalsMergeWithoutLosingEitherEvent() {
        val one = user()
        val two = user(2, "另一条秘密")
        val out = prompt(listOf(Recalls.withdrawn(one), Recalls.withdrawn(two), Recalls.event(one, 3), Recalls.event(two, 4)))
        assertEquals(listOf("system", "user"), out.map { it.role })
        assertEquals(2, Regex("撤回了一条文字消息").findAll(out.last().content).count())
    }

    @Test fun answeredWithdrawalIsNotReinjectedWithTheNextMessage() {
        val one = user()
        val out = prompt(listOf(Recalls.withdrawn(one), Recalls.event(one, 2), user(3, "撤回也没关系").copy(role = "assistant"), user(4, "晚安")))
        assertEquals(1, out.count { it.content.contains("撤回了一条文字消息") })
        assertTrue(out.last().content.endsWith("晚安"))
    }

    @Test fun recapSeesTheEventButNotTheOriginalPayload() {
        val one = user()
        val out = Recap.request(ta, "我", null, listOf(Recalls.withdrawn(one), Recalls.event(one, 2)), now.zone)
        assertTrue(out.last().content.contains("撤回了一条文字消息"))
        assertFalse(out.any { it.content.contains(one.content) })
    }
}
