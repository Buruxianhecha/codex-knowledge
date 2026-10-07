package com.cleo.cleos.data

import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.MessageEntity
import org.junit.Assert.*
import org.junit.Test

class MessageEditsTest {
    private val original = MessageEntity(id = 3, conversationId = 7, role = "user", content = "开了", createdAt = 100)
    private val conversation = ConversationEntity(id = 7, title = "我们的聊天", companionId = 8, createdAt = 10, updatedAt = 200)
    private val image = MessageImages.encode(listOf(MessageImage("photo.jpg", 240, 320)))
    private fun rejected(block: () -> Unit) {
        try { block(); fail("expected edit rejection") } catch (_: MessageEditException) { }
    }

    @Test fun ordinaryOwnTextCanBeEdited() {
        assertTrue(MessageEdits.canEdit(original))
        assertTrue(MessageEdits.canSubmit(original, "权限已经开了"))
    }

    @Test fun otherRolesNotesAndPhoneTurnsHaveNoEditEntry() {
        for (role in listOf("assistant", "tool", "note", "request", "recalled", "recall_event"))
            assertFalse(MessageEdits.canEdit(original.copy(role = role)))
        assertFalse(MessageEdits.canEdit(original.copy(note = "已授权")))
        assertFalse(MessageEdits.canEdit(original.copy(call = 99)))
    }

    @Test fun VoiceAndStickerOnlyDoNotBecomeFakeTextMessages() {
        assertFalse(MessageEdits.canEdit(original.copy(audio = MessageAudios.encode(MessageAudio("voice.wav", 3000)))))
        assertFalse(MessageEdits.canEdit(original.copy(content = "[[sticker:亲亲]]")))
        assertFalse(MessageEdits.canEdit(original.copy(content = "")))
    }

    @Test fun imageCaptionsCanBeAddedChangedOrRemoved() {
        val photo = original.copy(images = image, content = "")
        assertTrue(MessageEdits.canEdit(photo))
        assertTrue(MessageEdits.canSubmit(photo, "这张图是什么？"))
        assertTrue(MessageEdits.canSubmit(photo.copy(content = "看图"), ""))
        assertFalse(MessageEdits.canSubmit(photo, " "))
    }

    @Test fun emptyOrUnchangedTextNeverTriggersAnotherReply() {
        assertFalse(MessageEdits.canSubmit(original, " 开了 \n"))
        assertFalse(MessageEdits.canSubmit(original, " \n"))
        rejected { MessageEdits.prefix(listOf(original), original, "开了") }
    }

    @Test fun equalTimestampUsesIdsAndExcludesAllLaterTurns() {
        val before = original.copy(id = 2, role = "assistant", content = "你开一下权限")
        val after = original.copy(id = 4, role = "assistant", content = "旧的回答")
        val newest = original.copy(id = 5, createdAt = 120, content = "下一个话题")
        val foreign = before.copy(id = 1, conversationId = 99)
        assertEquals(listOf(before, original), MessageEdits.prefix(listOf(newest, foreign, after, original, before), original, "已经开了"))
    }

    @Test fun staleContentAndMissingOrForeignTargetsAreRejected() {
        rejected { MessageEdits.prefix(listOf(original.copy(content = "撤回过")), original, "重发") }
        rejected { MessageEdits.prefix(emptyList(), original, "重发") }
        rejected { MessageEdits.prefix(listOf(original.copy(conversationId = 99)), original, "重发") }
    }

    @Test fun staleAttachmentQuoteAndRecallAreRejected() {
        for (changed in listOf(original.copy(images = image), original.copy(quote = "{}"), Recalls.withdrawn(original)))
            rejected { MessageEdits.prefix(listOf(changed), original, "重发") }
    }

    @Test fun copiesHaveNewIdentityAndDoNotMutateTheOriginal() {
        val copy = MessageEdits.copyRow(original, 10, emptyMap(), emptyMap())
        assertEquals(0L, copy.id)
        assertEquals(10L, copy.conversationId)
        assertEquals("开了", original.content)
        assertEquals(7L, original.conversationId)
        assertEquals(3L, original.id)
    }

    @Test fun quotesPointToTheCopiedMessageAndKeepTheirExcerpt() {
        val quote = MessageQuote(2, "assistant", "你开一下权限")
        val row = original.copy(quote = MessageQuotes.encode(quote))
        val copy = MessageEdits.copyRow(row, 10, mapOf(2L to 22L), emptyMap())
        assertEquals(quote.copy(id = 22), MessageQuotes.decode(copy.quote))
        assertNull(MessageEdits.copyRow(row, 10, emptyMap(), emptyMap()).quote)
    }

    @Test fun previousCallTurnsStayInsideTheCopiedCall() {
        val said = original.copy(id = 2, call = 1, audio = null)
        assertEquals(20L, MessageEdits.copyRow(said, 10, mapOf(1L to 20L), emptyMap()).call)
        rejected { MessageEdits.copyRow(said, 10, emptyMap(), emptyMap()) }
    }

    @Test fun photoAndRecordingReferencesUseIndependentFiles() {
        val row = original.copy(images = image, audio = MessageAudios.encode(MessageAudio("voice.wav", 3200)))
        val copy = MessageEdits.copyRow(row, 10, emptyMap(), mapOf("photo.jpg" to "edit-photo.jpg", "voice.wav" to "edit-voice.wav"))
        assertEquals(listOf(MessageImage("edit-photo.jpg", 240, 320)), MessageImages.decode(copy.images))
        assertEquals(MessageAudio("edit-voice.wav", 3200), MessageAudios.decode(copy.audio))
        assertEquals("photo.jpg", MessageImages.decode(row.images).single().file)
        assertEquals("voice.wav", MessageAudios.decode(row.audio)?.file)
    }

    @Test fun previousRecallEventsReferToTheirNewIds() {
        val event = Recalls.event(original, 110)
        val copy = MessageEdits.copyRow(event, 10, mapOf(3L to 33L), emptyMap())
        assertEquals(33L, Recalls.decode(copy.content)?.messageId)
        assertEquals(Recalls.describe(event.content), Recalls.describe(copy.content))
    }

    @Test fun reactionsStillMatchTheirCopiedAssistantTurn() {
        val assistant = original.copy(role = "assistant")
        val event = ReactionEvents.change(assistant, "🥺", 110)!!.event!!
        val copy = MessageEdits.copyRow(event, 10, mapOf(3L to 33L), emptyMap())
        assertTrue(Triple(33L, "🥺", 110L) in ReactionEvents.covered(listOf(copy)))
        assertEquals(ReactionEvents.describe(event.content), ReactionEvents.describe(copy.content))
    }

    @Test fun brokenOldEventsAndDeletedTargetsRemainReadableWithoutCrashing() {
        val broken = original.copy(role = "reaction_event", content = "broken json")
        assertEquals(broken.content, MessageEdits.copyRow(broken, 10, emptyMap(), emptyMap()).content)
        val event = Recalls.event(original, 110)
        assertEquals(event.content, MessageEdits.copyRow(event, 10, emptyMap(), emptyMap()).content)
    }

    @Test fun recapStrictlyBeforeEditIsRetainedWithNewBoundaryId() {
        val source = conversation.copy(recap = "之前聊过天气", recapUntilAt = 90, recapUntilId = 2)
        val copy = MessageEdits.copyConversation(source, original, mapOf(2L to 22L), 200)
        assertEquals(source.recap, copy.recap)
        assertEquals(22L, copy.recapUntilId)
        assertEquals(8L, copy.companionId)
        assertEquals(0L, copy.id)
        assertEquals("我们的聊天 · 编辑续聊", copy.title)
    }

    @Test fun recapIncludingEditedWordsOrLaterReplyIsCleared() {
        for ((at, id) in listOf(100L to 3L, 100L to 4L, 150L to 2L)) {
            val source = conversation.copy(recap = "旧消息的总结", recapUntilAt = at, recapUntilId = id)
            val copy = MessageEdits.copyConversation(source, original, mapOf(id to 99L), 200)
            assertNull(copy.recap)
            assertNull(copy.recapUntilAt)
            assertNull(copy.recapUntilId)
            assertEquals("旧消息的总结", source.recap)
        }
    }

    @Test fun sameTimeEarlierRecapIsAllowedButMissingBoundaryIsCleared() {
        val source = conversation.copy(title = "我们的聊天 · 编辑续聊", recap = "前文", recapUntilAt = 100, recapUntilId = 2)
        assertEquals("前文", MessageEdits.copyConversation(source, original, mapOf(2L to 22L), 200).recap)
        val copy = MessageEdits.copyConversation(source, original, emptyMap(), 200)
        assertNull(copy.recap)
        assertEquals("我们的聊天 · 编辑续聊", copy.title)
        assertNull(MessageEdits.copyConversation(source.copy(recap = null), original, mapOf(2L to 22L), 200).recapUntilAt)
    }
}
