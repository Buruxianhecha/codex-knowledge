package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class QqWindowRulesTest {
    private val qq = "com.tencent.qqmusic"
    private val ime = "com.android.inputmethod.latin"

    @Test fun directQQRootRemainsSelectable() {
        assertEquals(3, QqWindowRules.choose(3, qq, emptyList(), qq))
    }
    @Test fun activeKeyboardCanRecoverQQEvenWithoutApplicationFocusFlags() {
        val windows = listOf(
            QqWindowRules.Window(1, ime, inputMethod = true, active = true, layer = 50),
            QqWindowRules.Window(2, qq, application = true, layer = 20),
        )
        assertEquals(2, QqWindowRules.choose(1, ime, windows, qq))
    }
    @Test fun competingForegroundAppBlocksQQRoot() {
        val windows = listOf(
            QqWindowRules.Window(1, ime, inputMethod = true, active = true, layer = 50),
            QqWindowRules.Window(2, qq, application = true, layer = 10),
            QqWindowRules.Window(3, "com.tencent.mm", application = true, layer = 20),
        )
        assertNull(QqWindowRules.choose(1, ime, windows, qq))
    }
    @Test fun cannotFallbackWithoutActiveInputMethod() {
        val windows = listOf(QqWindowRules.Window(2, qq, application = true, layer = 20))
        assertNull(QqWindowRules.choose(99, "com.unknown", windows, qq))
    }
    @Test fun anotherPlayerCanNeverBeAuthorizedAsQQ() {
        assertNull(QqWindowRules.choose(1, qq, emptyList(), "com.kugou.android"))
    }
}
