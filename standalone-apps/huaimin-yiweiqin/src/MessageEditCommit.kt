package com.cleo.cleos.ai

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Cancel before committing freely; after committing, retain files and schedule exactly one reply. */
internal suspend fun <T> commitMessageEdit(
    stage: suspend () -> Unit,
    commit: suspend () -> T,
    rollback: suspend () -> Unit,
    answer: suspend (T) -> Unit,
): T {
    var committed = false
    try {
        stage()
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val result = commit()
            committed = true
            answer(result)
            result
        }
    } finally {
        if (!committed) withContext(NonCancellable) { rollback() }
    }
}
