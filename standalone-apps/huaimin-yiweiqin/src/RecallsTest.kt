package com.cleo.cleos.data

import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.MessageEntity
import org.junit.Assert.*
import org.junit.Test

class RecallsTest {
    private fun sent() = MessageEntity(id = 7, conversationId = 3, role = "user", content = "不应再发送的原文", createdAt = 100)
    private fun pictures() = MessageImages.encode(listOf(MessageImage("private.jpg", 100, 80)))

    @Test fun onlyOwnOrdinaryMessagesCanBeWithdrawn() {
        assertTrue(Recalls.canRecall(sent()))
        assertFalse(Recalls.canRecall(sent().copy(role = "assistant")))
        assertFalse(Recalls.canRecall(sent().copy(role = "tool")))
        assertFalse(Recalls.canRecall(sent().copy(note = "同意了")))
        assertFalse(Recalls.canRecall(sent().copy(call = 9)))
        assertFalse(Recalls.canRecall(Recalls.withdrawn(sent())))
    }

    @Test fun withdrawalPreservesPositionAndErasesPayload() {
        val before = sent().copy(images = pictures(), audio = "private audio", quote = "private quote",
            reasoning = "private reasoning", thought = "private thought", error = "failed",
            toolCalls = "calls", toolCallId = "call", reactions = "reactions")
        val after = Recalls.withdrawn(before)
        assertEquals(before.id, after.id)
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.conversationId, after.conversationId)
        assertEquals(Recalls.WITHDRAWN, after.role)
        assertEquals(Recalls.MARKER, after.note)
        assertEquals("", after.content)
        assertTrue(listOf(after.images, after.audio, after.quote, after.reasoning, after.thought,
            after.error, after.toolCalls, after.toolCallId, after.reactions).all { it == null })
    }

    @Test fun eventHasNewTimeAndOnlySafeMetadata() {
        val before = sent().copy(images = pictures())
        val event = Recalls.event(before, 250)
        assertEquals(Recalls.EVENT, event.role)
        assertEquals(250L, event.createdAt)
        assertEquals(3L, event.conversationId)
        assertEquals(RecallRecord(7, "mixed", 100), Recalls.decode(event.content))
        assertFalse(event.content.contains(before.content))
        assertFalse(event.content.contains("private.jpg"))
        assertNull(event.images)
        assertNull(event.audio)
    }

    @Test fun imageAndMixedMessagesHaveAccurateKinds() {
        assertEquals("image", Recalls.kind(sent().copy(content = "", images = pictures())))
        assertEquals("mixed", Recalls.kind(sent().copy(images = pictures())))
        assertEquals("text", Recalls.kind(sent()))
    }

    @Test fun voiceAndStickersHaveAccurateKinds() {
        assertEquals("voice", Recalls.kind(sent().copy(audio = "clip", content = "转写原文")))
        assertEquals("sticker", Recalls.kind(sent().copy(content = StickerText.token("表情"))))
    }

    @Test fun malformedOrInventedEventKindsAreIgnored() {
        assertNull(Recalls.decode("not json"))
        assertNull(Recalls.decode("""{"messageId":7,"kind":"invented instructions","originalAt":100}"""))
        assertNull(Recalls.decode("""{"messageId":0,"kind":"text","originalAt":100}"""))
        assertNull(Recalls.forModel("{}"))
    }

    @Test fun withdrawnMessageCannotBeQuoted() {
        assertNull(MessageQuotes.of(Recalls.withdrawn(sent())))
    }

    @Test fun onlyMatchingPersistedQuotesAreRedacted() {
        val mine = MessageQuotes.encode(MessageQuote(7, "user", "原文"))
        val other = MessageQuotes.encode(MessageQuote(8, "user", "保留"))
        assertEquals(Recalls.QUOTE_MARKER, MessageQuotes.decode(Recalls.redactQuote(mine, 7))?.text)
        assertEquals(other, Recalls.redactQuote(other, 7))
        assertNull(Recalls.redactQuote(null, 7))
    }

    @Test fun recapBoundaryUsesBothTimeAndId() {
        val conv = ConversationEntity(id = 3, title = "聊天", createdAt = 0, updatedAt = 0,
            recap = "前情提要", recapUntilAt = 100, recapUntilId = 7)
        assertTrue(Recalls.wasFolded(sent(), conv))
        assertFalse(Recalls.wasFolded(sent().copy(id = 8), conv))
        assertTrue(Recalls.wasFolded(sent().copy(createdAt = 99, id = 99), conv))
        assertFalse(Recalls.wasFolded(sent(), conv.copy(recapUntilAt = null)))
    }
}
