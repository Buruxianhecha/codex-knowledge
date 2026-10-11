package com.cleo.cleos.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Pure runtime checks: physical bounds, foreground window selection, and
 * dispatch completion. No Android accessibility objects are mocked here. */
class KugouRuntimeRulesTest {
    private val pkg = "com.kugou.android"
    private val keyboardPkg = "com.android.inputmethod.latin"
    private fun bounds(left: Int, top: Int, right: Int, bottom: Int) =
        KugouScreenRules.Bounds(left, top, right, bottom)

    @Test fun highDensityWideTitleIsAcceptedUsingWindowAndDpLimits() {
        val window = bounds(0, 0, 1440, 3200)
        assertTrue(KugouScreenRules.safeTap(
            bounds(80, 950, 980, 1085), window, 3.5f, "song"))
        assertTrue("A single-line full-width title is not an oversized container",
            KugouScreenRules.safeTap(bounds(0, 950, 1440, 1085), window, 3.5f, "song"))
    }

    @Test fun containerEmptyAndOutsideWindowBoundsAreRejected() {
        val window = bounds(0, 0, 1440, 3200)
        assertFalse(KugouScreenRules.safeTap(window, window, 3.5f, "song"))
        assertFalse(KugouScreenRules.safeTap(bounds(100, 800, 100, 900), window, 3.5f, "song"))
        assertFalse(KugouScreenRules.safeTap(bounds(-100, 900, 300, 1040), window, 3.5f, "song"))
        assertFalse(KugouScreenRules.safeTap(bounds(1200, 900, 1650, 1040), window, 3.5f, "song"))
        assertFalse(KugouScreenRules.safeTap(bounds(100, 3100, 900, 3400), window, 3.5f, "song"))
    }

    @Test fun applicationWindowClippedByKeyboardRejectsOccludedTarget() {
        val availableApplication = bounds(0, 0, 1440, 1800)
        assertTrue(KugouScreenRules.safeTap(
            bounds(100, 900, 1100, 1040), availableApplication, 3.5f, "song"))
        assertFalse(KugouScreenRules.safeTap(
            bounds(100, 2150, 1100, 2290), availableApplication, 3.5f, "song"))
    }

    @Test fun gestureBoundsOnlySupportVerifiedMusicTargets() {
        val window = bounds(0, 0, 1440, 3200)
        val tab = bounds(310, 300, 420, 440)
        assertTrue(KugouScreenRules.safeTap(tab, window, 3.5f, "songs"))
        assertFalse(KugouScreenRules.safeTap(tab, window, 3.5f, "search"))
        assertFalse(KugouScreenRules.safeTap(tab, window, 3.5f, "submit"))
    }

    @Test fun directActiveKugouRootIsRetained() {
        assertEquals(10, KugouScreenRules.applicationWindow(10, pkg, emptyList()))
    }

    @Test fun activeKeyboardCanUseFocusedKugouApplication() {
        val windows = listOf(
            KugouScreenRules.Window(20, keyboardPkg, inputMethod = true, active = true),
            KugouScreenRules.Window(10, pkg, application = true, focused = true),
        )
        assertEquals(10, KugouScreenRules.applicationWindow(20, keyboardPkg, windows))
    }

    @Test fun activeApplicationHasPriorityOverOtherFocusedWindow() {
        val windows = listOf(
            KugouScreenRules.Window(20, keyboardPkg, inputMethod = true, active = true),
            KugouScreenRules.Window(10, pkg, application = true, active = true),
            KugouScreenRules.Window(30, "com.other.app", application = true, focused = true),
        )
        assertEquals(10, KugouScreenRules.applicationWindow(20, keyboardPkg, windows))
    }

    @Test fun anotherForegroundAppNeverUsesBackgroundKugouWindow() {
        val windows = listOf(
            KugouScreenRules.Window(30, "com.other.app", application = true, active = true, focused = true),
            KugouScreenRules.Window(10, pkg, application = true),
        )
        assertNull(KugouScreenRules.applicationWindow(30, "com.other.app", windows))
    }

    @Test fun keyboardOverAnotherAppCannotSelectInactiveKugou() {
        val windows = listOf(
            KugouScreenRules.Window(20, keyboardPkg, inputMethod = true, active = true),
            KugouScreenRules.Window(30, "com.other.app", application = true, focused = true),
            KugouScreenRules.Window(10, pkg, application = true),
        )
        assertNull(KugouScreenRules.applicationWindow(20, keyboardPkg, windows))
    }

    @Test fun foregroundSystemWindowDoesNotAuthorizeBackgroundAppClicks() {
        val windows = listOf(
            KugouScreenRules.Window(40, "com.android.systemui", active = true),
            KugouScreenRules.Window(10, pkg, application = true, focused = true),
        )
        assertNull(KugouScreenRules.applicationWindow(40, "com.android.systemui", windows))
    }

    @Test fun completedGestureIsSuccessful() = runBlocking {
        assertTrue(KugouGestureCompletion.await { callback ->
            callback(true)
            true
        })
    }

    @Test fun dispatchAcceptanceWithoutCompletionIsNotSuccess() = runBlocking {
        assertFalse(KugouGestureCompletion.await(timeoutMillis = 20) { true })
    }

    @Test fun cancelledAndRejectedGesturesFail() = runBlocking {
        assertFalse(KugouGestureCompletion.await { callback ->
            callback(false)
            true
        })
        assertFalse(KugouGestureCompletion.await { false })
    }

    @Test fun duplicateAndLateCallbacksAreSafe() = runBlocking {
        assertTrue(KugouGestureCompletion.await { callback ->
            callback(true)
            callback(false)
            callback(true)
            true
        })
        var late: ((Boolean) -> Unit)? = null
        assertFalse(KugouGestureCompletion.await(timeoutMillis = 20) { callback ->
            late = callback
            true
        })
        late!!(true)
        late!!(false)
    }
    @Test fun keyboardUnflaggedAppStillFindsVisibleKugou() {
        val windows = listOf(
            KugouScreenRules.Window(20, keyboardPkg, inputMethod = true, active = true, layer = 30),
            KugouScreenRules.Window(10, pkg, application = true, layer = 12),
        )
        assertEquals(10, KugouScreenRules.applicationWindow(20, keyboardPkg, windows))
    }
    @Test fun keyboardNeverChoosesBackgroundKugouBelowOtherApp() {
        val windows = listOf(
            KugouScreenRules.Window(20, keyboardPkg, inputMethod = true, active = true, layer = 50),
            KugouScreenRules.Window(10, pkg, application = true, layer = 8),
            KugouScreenRules.Window(30, "com.other.app", application = true, layer = 15),
        )
        assertNull(KugouScreenRules.applicationWindow(20, keyboardPkg, windows))
    }
}
