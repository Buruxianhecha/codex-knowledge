package com.cleo.cleos.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantBubbleDeliveryTest {
    @Test fun threeSentencesAreThreeSeparateSendsWithTwoShortGaps() = runBlocking {
        data class Row(val id: Int, val content: String, val createdAt: Long)
        val rows = ArrayList<Row>()
        var clock = 1000L
        AssistantBubbleDelivery.deliver(AssistantBubbleSplitter.split("第一句。第二句。第三句。", 8), pause = { clock += it }) { _, words ->
            rows += Row(rows.size + 1, words, clock)
        }
        assertEquals(listOf("第一句。", "第二句。", "第三句。"), rows.map { it.content })
        assertEquals(listOf(1, 2, 3), rows.map { it.id })
        assertEquals(listOf(1000L, 1550L, 2100L), rows.map { it.createdAt })
    }

    @Test fun firstMessageIsImmediateThenTypingPauseAndNextSend() = runBlocking {
        val events = ArrayList<String>()
        AssistantBubbleDelivery.deliver(listOf("你好。", "我在。"), waiting = { events += "typing" }, pause = { events += "pause" }) { index, _ -> events += "send$index" }
        assertEquals(listOf("send0", "typing", "pause", "send1"), events)
    }

    @Test fun stopDuringTheGapPreservesOnlyAlreadySentMessages() = runBlocking {
        val sent = ArrayList<String>()
        try {
            AssistantBubbleDelivery.deliver(listOf("第一句。", "第二句。", "第三句。"), pause = { throw CancellationException("stop") }) { _, words -> sent += words }
        } catch (_: CancellationException) { }
        assertEquals(listOf("第一句。"), sent)
    }

    @Test fun failedPersistenceDoesNotSendTheRemainingTail() = runBlocking {
        val sent = ArrayList<String>()
        try {
            AssistantBubbleDelivery.deliver(listOf("第一句。", "第二句。", "第三句。"), pause = {}) { index, words ->
                if (index == 1) throw IllegalStateException("storage failed")
                sent += words
            }
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("第一句。"), sent)
    }

    @Test fun cancelBeforeStartingSendsNothing() = runBlocking {
        val sent = ArrayList<String>()
        coroutineScope {
            val job = launch(start = CoroutineStart.LAZY) {
                AssistantBubbleDelivery.deliver(listOf("不应发出。"), pause = {}) { _, words -> sent += words }
            }
            job.cancel()
            job.join()
        }
        assertTrue(sent.isEmpty())
    }

    @Test fun longerTextStillHasAQuickBoundedGap() {
        assertTrue(AssistantBubbleDelivery.gapAfter("短") in 550L..900L)
        assertTrue(AssistantBubbleDelivery.gapAfter("很长的说明".repeat(100)) in 550L..900L)
    }

    @Test fun structuredCodeIsOneSendWithoutArtificialTypingPauses() = runBlocking {
        val code = "```kotlin\nprintln(1); println(2)\n```"
        var pauses = 0
        val sent = ArrayList<String>()
        AssistantBubbleDelivery.deliver(AssistantBubbleSplitter.split(code, 8), pause = { pauses++ }) { _, words -> sent += words }
        assertEquals(listOf(code), sent)
        assertEquals(0, pauses)
    }
}
