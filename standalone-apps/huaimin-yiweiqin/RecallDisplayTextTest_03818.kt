package com.cleo.cleos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallDisplayTextTest {
    @Test fun defaultAndBlank() {
        assertEquals("你撤回了一条消息", RecallDisplayText.clean(""))
        assertEquals("你撤回了一条消息", RecallDisplayText.clean("  \n "))
    }
    @Test fun oneLineAndLengthLimit() {
        assertEquals("我撤回\n了".replace("\n", " "), RecallDisplayText.clean("我撤回\n了"))
        assertEquals(80, RecallDisplayText.clean("测".repeat(100)).length)
    }
    @Test fun originalMarkerAndPresets() {
        assertEquals(RecallDisplayText.DEFAULT, RecallDisplayText.presets.first())
        assertTrue(RecallDisplayText.presets.any { it.contains("因为有错别字") })
    }
}
