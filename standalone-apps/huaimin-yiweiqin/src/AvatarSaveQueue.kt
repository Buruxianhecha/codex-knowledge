package com.cleo.cleos.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Submitted from the UI thread; saves finish in tap order even on the app's background scope. */
class AvatarSaveQueue(private val scope: CoroutineScope) {
    private var previous: Job? = null

    fun submit(save: suspend () -> Unit): Job {
        val before = previous
        return scope.launch {
            before?.join()
            save()
        }.also { previous = it }
    }
}
