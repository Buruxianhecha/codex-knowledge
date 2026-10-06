package com.cleo.cleos.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantBubbleSplitterTest {
    @Test fun oneParagraphStaysOneBubble() {
        assertEquals(listOf("只有一句。"), AssistantBubbleSplitter.split("只有一句。", 8))
    }

    @Test fun blankLinesBecomeIndependentBubbles() {
        assertEquals(
            listOf("第一句。", "第二句。", "第三句。"),
            AssistantBubbleSplitter.split("第一句。\n\n第二句。\n\n第三句。", 8),
        )
    }

    @Test fun windowsNewlinesSplitTheSameWay() {
        assertEquals(
            listOf("第一条", "第二条"),
            AssistantBubbleSplitter.split("第一条\r\n\r\n第二条", 8),
        )
    }

    @Test fun aSingleLineBreakDoesNotFakeExtraMessages() {
        assertEquals(
            listOf("第一行\n第二行"),
            AssistantBubbleSplitter.split("第一行\n第二行", 8),
        )
    }

    @Test fun blankLinesInsideCodeFenceStayInOneBubble() {
        val text = "先看代码：\n\n```kotlin\nval a = 1\n\nval b = 2\n```\n\n最后一句。"
        assertEquals(
            listOf("先看代码：", "```kotlin\nval a = 1\n\nval b = 2\n```", "最后一句。"),
            AssistantBubbleSplitter.split(text, 8),
        )
    }

    @Test fun overLimitKeepsAllTextInTheLastBubble() {
        val text = (1..10).joinToString("\n\n") { "第${it}条" }
        val out = AssistantBubbleSplitter.split(text, 8)
        assertEquals(8, out.size)
        assertEquals("第1条", out.first())
        assertEquals("第8条\n\n第9条\n\n第10条", out.last())
    }
}
