package com.cleo.cleos.ai

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** One suspendable send per bubble; stop cancels the unsent tail. */
object AssistantBubbleDelivery {
    fun gapAfter(words: String): Long = (360L + words.codePointCount(0, words.length) * 12L).coerceIn(550L, 900L)

    suspend fun deliver(
        parts: List<String>,
        waiting: () -> Unit = {},
        pause: suspend (Long) -> Unit = { delay(it) },
        send: suspend (Int, String) -> Unit,
    ) {
        parts.forEachIndexed { index, words ->
            currentCoroutineContext().ensureActive()
            if (index > 0) {
                waiting()
                pause(gapAfter(parts[index - 1]))
                currentCoroutineContext().ensureActive()
            }
            send(index, words)
        }
    }
}
