package com.cleo.cleos.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Pure device-local rules, shared by the service and executable regression tests. */
object KugouScreenRules {
    private const val PLAYER = "com.kugou.android"
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Window(
        val id: Int, val packageName: String, val application: Boolean = false,
        val inputMethod: Boolean = false, val active: Boolean = false,
        val focused: Boolean = false,
        val layer: Int = 0,
    )

    fun applicationWindow(activeId: Int?, activePackage: String?, windows: List<Window>): Int? {
        val foreground = windows.firstOrNull { it.application && it.active }
            ?: windows.firstOrNull { it.application && it.focused }
        // Keyboard focus may clear both flags on the visible app. Only
        // consider its layer when an observed IME owns the active root.
        val topMost = windows.filter { it.application && it.packageName.isNotBlank() }
            .maxByOrNull { it.layer }
        if (activePackage == PLAYER) {
            if (foreground != null && foreground.packageName != PLAYER) return null
            return activeId
        }
        // A background Kugou window never authorizes an action. Only the IME
        // may temporarily own the active root while the focused application is Kugou.
        if (windows.none { it.id == activeId && it.inputMethod }) return null
        return (foreground ?: topMost)?.takeIf { it.packageName == PLAYER }?.id
    }

    fun safeTap(target: Bounds, window: Bounds, density: Float, purpose: String): Boolean {
        if (purpose !in setOf("songs", "song") || !density.isFinite() || density <= 0) return false
        val width = target.right.toLong() - target.left
        val height = target.bottom.toLong() - target.top
        val availableWidth = window.right.toLong() - window.left
        val availableHeight = window.bottom.toLong() - window.top
        if (availableWidth <= 0 || availableHeight <= 0) return false
        val maxHeight = if (purpose == "songs") 110 else 100
        if (width < 4 * density || width > availableWidth || height < 4 * density || height > maxHeight * density) return false
        // The entire label must be visible, including when an IME covers the lower screen.
        return target.left >= window.left && target.right <= window.right &&
            target.top >= window.top && target.bottom <= window.bottom
    }
}

object KugouGestureCompletion {
    suspend fun await(timeoutMillis: Long = 1_500L, start: ((Boolean) -> Unit) -> Boolean): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val complete: (Boolean) -> Unit = { success ->
                    // dispatch and callbacks run on the service main thread; ignore
                    // duplicate or late callbacks after cancellation/timeout.
                    if (continuation.isActive) continuation.resumeWith(Result.success(success))
                }
                if (!start(complete)) complete(false)
            }
        } ?: false
}
