package com.cleo.cleos.ai

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One recall at a time per conversation; fence new replies until the old job has stopped. */
internal class RecallCoordinator(
    private val pause: (Long) -> Unit,
    private val stop: suspend (Long) -> Unit,
    private val resume: (Long) -> Unit,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun perform(conversationId: Long, eligible: suspend () -> Boolean, commit: suspend () -> Unit): Boolean =
        locks.getOrPut(conversationId) { Mutex() }.withLock {
            if (!eligible()) return@withLock false
            pause(conversationId)
            try {
                stop(conversationId)
                commit()
                true
            } finally {
                resume(conversationId)
            }
        }
}
