package com.cleo.cleos.ai

import com.cleo.cleos.data.*
import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.MessageEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReactionPromptTest {
    private val ta = CompanionEntity(id = 1, name = "星", apiBaseUrl = "", apiModel = "", createdAt = 0)
    private val now = ZonedDateTime.of(2026, 10, 5, 23, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
    private fun user(id: Long = 1, text: String = "你好") = MessageEntity(id = id, conversationId = 3, role = "user", content = text, createdAt = id)
    private fun answer() = user(2, "没关系，想好了再说，我在呢。").copy(role = "assistant")
    private fun event(target: MessageEntity = answer(), emoji: String = "🥺", at: Long = 3) = ReactionEvents.change(target, emoji, at)!!.event!!.copy(id = at)
    private fun prompt(history: List<MessageEntity>) = Prompt.messages(AppSettings(), ta, history, now, images = true)

    @Test fun standaloneMenuReactionBecomesANewUserTurnWithoutAnyTextSend() {
        val out = prompt(listOf(user(), answer(), event()))
        assertEquals(listOf("system", "user", "assistant", "user"), out.map { it.role })
        assertTrue(out.last().content.contains("用 🥺 回应了你说的「${answer().content}」"))
        assertTrue(out.last().content.contains("自然简短地回复"))
    }

    @Test fun targetOutsideTheLiveWindowStillHasItsContext() {
        val old = answer().copy(id = 500, content = "那本日记是写给你的")
        val out = prompt(listOf(event(old)))
        assertEquals(listOf("system", "user"), out.map { it.role })
        assertTrue(out.last().content.contains(old.content))
    }

    @Test fun severalQuickReactionsAreMergedWithoutDroppingEither() {
        val out = prompt(listOf(user(), answer(), event(), event(emoji = "❤️", at = 4)))
        assertTrue(out.last().content.contains("🥺"))
        assertTrue(out.last().content.contains("❤️"))
        assertEquals(2, Regex("对方用").findAll(out.last().content).count())
    }

    @Test fun nextTextDoesNotAnnounceTheSameNewReactionTwice() {
        val reacted = answer().copy(reactions = MessageReactions.encode(listOf(MessageReaction("🥺", 3))))
        val out = prompt(listOf(user(), reacted, event(), user(4, "谢谢你")))
        assertEquals(1, out.sumOf { Regex("用 🥺 回应了").findAll(it.content).count() })
        assertFalse(out.last().content.contains("贴了"))
        assertTrue(out.last().content.endsWith("谢谢你"))
    }

    @Test fun oldBackupReactionsStillUseTheLegacyNextMessageFallback() {
        val old = answer().copy(reactions = MessageReactions.encode(listOf(MessageReaction("🥺", 3))))
        val out = prompt(listOf(user(), old, user(4, "谢谢你")))
        assertTrue(out.last().content.contains("贴了 🥺"))
        assertTrue(out.last().content.endsWith("谢谢你"))
    }

    @Test fun answeredReactionRemainsInHistoryWithoutBeingReinjectedAtTheEnd() {
        val out = prompt(listOf(user(), answer(), event(), user(4, "抱抱你").copy(role = "assistant"), user(5, "晚安")))
        assertEquals(1, out.count { it.content.contains("用 🥺 回应了") })
        assertFalse(out.last().content.contains("回应了"))
        assertTrue(out.last().content.endsWith("晚安"))
    }

    @Test fun cancelledPendingEventAndRemovedChipAreNotReported() {
        val out = prompt(listOf(user(), answer().copy(reactions = null), user(4, "晚安")))
        assertFalse(out.any { it.content.contains("用 🥺 回应了") || it.content.contains("贴了 🥺") })
    }

    @Test fun reactingToAStickerDoesNotAttachAnImageOrTreatItAsANewStickerSend() {
        val target = answer().copy(content = StickerText.token("亲亲"))
        val out = prompt(listOf(event(target)))
        assertTrue(out.last().content.contains("表情包「亲亲」"))
        assertTrue(out.all { it.images.isEmpty() })
        assertFalse(out.last().content.contains("[[sticker:"))
    }

    @Test fun reactingToVoiceUsesItsWordsWithoutSendingAudioMetadata() {
        val target = answer().copy(audio = "private-file")
        val out = prompt(listOf(event(target)))
        assertTrue(out.last().content.contains("你的语音"))
        assertTrue(out.last().content.contains(target.content))
        assertFalse(out.last().content.contains("private-file"))
    }

    @Test fun recapRetainsWhichMessageTheEmojiRespondedTo() {
        val out = Recap.request(ta, "我", null, listOf(event()), now.zone)
        assertTrue(out.last().content.contains("用 🥺 回应了"))
        assertTrue(out.last().content.contains(answer().content))
    }
}
