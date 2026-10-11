package com.cleo.cleos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallDisplayTextTest {
    @Test fun includesAllThirteenReferencesInOrder() {
        val suffixes = listOf(
            "因为有错别字。",
            "无",
            "并坏笑了一下。",
            "你猜猜撤回了什么。",
            "并祝你新春快乐 🐮",
            "你品，你细品~",
            "并抱了你一下 ❤️",
            "保持高贵的沉默。",
            "试图萌混过关 😉",
            "并假装无事发生。",
            "并对你施了遗忘咒。",
            "就是辣么神秘 😎",
            "并祝你节日快乐",
        )
        assertEquals(13, RecallDisplayText.presets.size)
        assertEquals(13, RecallDisplayText.presets.toSet().size)
        assertEquals(
            suffixes,
            RecallDisplayText.presets.map {
                if (it == RecallDisplayText.DEFAULT) "无"
                else it.removePrefix(RecallDisplayText.DEFAULT).removePrefix("，")
            },
        )
        assertTrue(RecallDisplayText.presets.all { it.startsWith(RecallDisplayText.DEFAULT) })
    }

    @Test fun defaultAndBlank() {
        assertEquals(RecallDisplayText.DEFAULT, RecallDisplayText.clean(""))
        assertEquals(RecallDisplayText.DEFAULT, RecallDisplayText.clean("  \n "))
    }

    @Test fun normalizesSingleLineAndCapsAtEighty() {
        assertEquals("我撤回 了", RecallDisplayText.clean("我撤回\n了"))
        assertEquals(80, RecallDisplayText.clean("测".repeat(100)).length)
    }
}
