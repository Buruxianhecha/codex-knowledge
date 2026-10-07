package com.cleo.cleos.data

import com.cleo.cleos.data.db.MessageEntity
import org.junit.Assert.*
import org.junit.Test

class ReactionEventsTest {
    private fun said() = MessageEntity(id = 7, conversationId = 3, role = "assistant", content = "没关系，想好了再说，我在呢。", createdAt = 100)
    private fun event(emoji: String = "🥺", at: Long = 250) = ReactionEvents.change(said(), emoji, at)!!.event!!

    @Test fun menuEmojiIsRestrictedToSuccessfulAssistantMessages() {
        for (emoji in MessageReactions.OFFERED) assertNotNull(ReactionEvents.change(said(), emoji, 250))
        assertNull(ReactionEvents.change(said().copy(role = "user"), "🥺", 250))
        assertNull(ReactionEvents.change(said().copy(call = 9), "🥺", 250))
        assertNull(ReactionEvents.change(said().copy(error = "stopped"), "🥺", 250))
        assertNull(ReactionEvents.change(said(), "invented", 250))
        assertNull(ReactionEvents.change(said().copy(content = ""), "🥺", 250))
    }

    @Test fun additionCreatesItsOwnCurrentTurnWithTargetContext() {
        val change = ReactionEvents.change(said(), "🥺", 250)!!
        val event = requireNotNull(change.event)
        assertEquals(listOf(MessageReaction("🥺", 250)), MessageReactions.decode(change.reactions))
        assertEquals(ReactionEvents.EVENT, event.role)
        assertEquals(3L, event.conversationId)
        assertEquals(250L, event.createdAt)
        assertEquals(ReactionEventRecord(7, "🥺", "text", said().content), ReactionEvents.decode(event.content))
    }

    @Test fun removingAnEmojiDoesNotCreateAnotherTurn() {
        val before = said().copy(reactions = MessageReactions.encode(listOf(MessageReaction("🥺", 250))))
        val change = ReactionEvents.change(before, "🥺", 300)!!
        assertNull(change.event)
        assertNull(change.reactions)
    }

    @Test fun differentEmojiIsKeptWhenAnotherIsRemoved() {
        val before = said().copy(reactions = MessageReactions.encode(listOf(MessageReaction("🥺", 250), MessageReaction("❤️", 251))))
        val change = ReactionEvents.change(before, "🥺", 300)!!
        assertEquals(listOf(MessageReaction("❤️", 251)), MessageReactions.decode(change.reactions))
        assertNull(change.event)
    }

    @Test fun voiceAndStickerTargetsAreIdentifiedWithoutPrivateMediaPaths() {
        val voice = ReactionEvents.change(said().copy(audio = "private-voice-path"), "❤️", 250)!!.event!!
        assertEquals("voice", ReactionEvents.decode(voice.content)!!.kind)
        assertFalse(voice.content.contains("private-voice-path"))
        assertNull(voice.audio)
        val sticker = ReactionEvents.change(said().copy(content = StickerText.token("亲亲")), "🥺", 250)!!.event!!
        assertEquals("sticker", ReactionEvents.decode(sticker.content)!!.kind)
        assertTrue(ReactionEvents.describe(sticker.content)!!.contains("表情包「亲亲」"))
    }

    @Test fun excerptsAreBoundedWithoutBreakingSupplementaryCharacters() {
        val raw = "🙂".repeat(300)
        val e = ReactionEvents.change(said().copy(content = raw, images = "private-picture-path"), "🥺", 250)!!.event!!
        assertEquals("🙂".repeat(240) + "…", ReactionEvents.decode(e.content)!!.excerpt)
        assertNull(e.images)
        assertFalse(e.content.contains("private-picture-path"))
    }

    @Test fun invalidEventsCannotInjectAnInventedEmojiOrKind() {
        assertNull(ReactionEvents.decode("broken"))
        assertNull(ReactionEvents.decode("""{"messageId":7,"emoji":"invented","kind":"text","excerpt":"hi"}"""))
        assertNull(ReactionEvents.decode("""{"messageId":0,"emoji":"🥺","kind":"text","excerpt":"hi"}"""))
        assertNull(ReactionEvents.decode("""{"messageId":7,"emoji":"🥺","kind":"invented","excerpt":"hi"}"""))
        assertNull(ReactionEvents.forModel("{}"))
    }

    @Test fun legacyDeduplicationKeysIncludeTheAdditionTime() {
        val covered = ReactionEvents.covered(listOf(event(at = 250)))
        assertTrue(Triple(7L, "🥺", 250L) in covered)
        assertFalse(Triple(7L, "🥺", 300L) in covered)
        assertFalse(Triple(8L, "🥺", 250L) in covered)
    }

    @Test fun pendingCancellationIsLimitedToTheSameConversationTargetEmojiAndTime() {
        val events = listOf(event(at = 250).copy(id = 1), event(at = 350).copy(id = 2),
            event("❤️", 360).copy(id = 3), event(at = 370).copy(id = 4, conversationId = 9),
            event(at = 380).copy(id = 5, content = "broken"))
        assertEquals(listOf(2L), ReactionEvents.pendingIds(events, 3, 7, "🥺", 300))
        assertTrue(ReactionEvents.pendingIds(events, 3, 8, "🥺", 300).isEmpty())
    }

    @Test fun alreadyReadEventIsNotCancelledAndReaddingGetsANewIdentity() {
        val old = event(at = 250).copy(id = 1)
        assertTrue(ReactionEvents.pendingIds(listOf(old), 3, 7, "🥺", 250).isEmpty())
        val again = event(at = 400)
        assertEquals(setOf(Triple(7L, "🥺", 250L), Triple(7L, "🥺", 400L)), ReactionEvents.covered(listOf(old, again)))
    }
}
