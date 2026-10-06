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

    @Test fun aSingleLineBreakAlsoBecomesIndependentMessages() {
        assertEquals(
            listOf("第一行", "第二行"),
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

    @Test fun threeSentencesWithoutAnyNewlineBecomeThreeMessages() {
        assertEquals(listOf("第一句话。", "第二句话！", "第三句话？"), AssistantBubbleSplitter.split("第一句话。第二句话！第三句话？", 8))
    }

    @Test fun screenshotReplyHonoursAnExplicitThreeMessageRequest() {
        val text = "GitHub 已连上，我刚试着读取账号信息，成功了。当前授权账号是 Buruxianhecha，显示名“怀民亦未寝”，有 6 个公开仓库。"
        val requested = AssistantBubbleSplitter.requestedCount("不是让你分三次连续发吗？")
        val parts = AssistantBubbleSplitter.split(text, 8, requested)
        assertEquals(3, parts.size)
        assertEquals(text, parts.joinToString(""))
    }

    @Test fun explicitDigitsAndChineseCountsAreRecognised() {
        assertEquals(3, AssistantBubbleSplitter.requestedCount("请分成3条消息"))
        assertEquals(2, AssistantBubbleSplitter.requestedCount("分两次发"))
        assertEquals(3, AssistantBubbleSplitter.requestedCount("三句话分三次发送"))
        assertEquals(null, AssistantBubbleSplitter.requestedCount("今天跑了三次步"))
    }

    @Test fun englishSentenceBoundariesAlsoSplit() {
        assertEquals(listOf("Hello!", "How are you?", "I am here."), AssistantBubbleSplitter.split("Hello! How are you? I am here.", 8))
    }

    @Test fun versionNumbersAndDomainsDoNotSplit() {
        assertEquals(listOf("现在是 0.37.18，接口 https://www.sui-xiang.net/v1。", "可以继续。"), AssistantBubbleSplitter.split("现在是 0.37.18，接口 https://www.sui-xiang.net/v1。可以继续。", 8))
    }

    @Test fun aDecimalDoesNotSplit() {
        assertEquals(listOf("The value is 3.14.", "Done."), AssistantBubbleSplitter.split("The value is 3.14. Done.", 8))
    }

    @Test fun commonEnglishTitlesStayWithTheName() {
        assertEquals(listOf("Dr. Lin is here.", "Hello."), AssistantBubbleSplitter.split("Dr. Lin is here. Hello.", 8))
    }

    @Test fun inlineCodePunctuationDoesNotSplit() {
        assertEquals(listOf("运行 `print('hello!'); run()`。", "然后继续。"), AssistantBubbleSplitter.split("运行 `print('hello!'); run()`。然后继续。", 8))
    }

    @Test fun quotedSentencesStayInsideTheirQuote() {
        assertEquals(listOf("他说“别走。等等！”然后笑了。", "我在。"), AssistantBubbleSplitter.split("他说“别走。等等！”然后笑了。我在。", 8))
    }

    @Test fun markdownListsStayComplete() {
        val list = "- 第一项。还有说明。\n- 第二项！"
        assertEquals(listOf(list, "我发完了。"), AssistantBubbleSplitter.split(list + "\n\n我发完了。", 8))
    }

    @Test fun markdownTablesStayComplete() {
        val table = "| 名称 | 说明 |\n| --- | --- |\n| 甲 | 第一项。第二项。 |"
        assertEquals(listOf(table), AssistantBubbleSplitter.split(table, 8))
    }

    @Test fun aFenceWithoutBlankLinesIsStillAWholeBlock() {
        val code = "```text\nHello!\n\nNext.\n```"
        assertEquals(listOf("看这个。", code, "下一句。"), AssistantBubbleSplitter.split("看这个。\n" + code + "\n下一句。", 8))
    }

    @Test fun shorterMarkersDoNotCloseALongerFence() {
        val code = "````text\n```\n第一句。第二句。\n````"
        assertEquals(listOf(code), AssistantBubbleSplitter.split(code, 8))
    }

    @Test fun anUnclosedFenceNeverLosesOrSplitsCode() {
        val code = "~~~text\n第一句。\n\n第二句。"
        assertEquals(listOf(code), AssistantBubbleSplitter.split(code, 8))
    }

    @Test fun trailingEmojiStaysWithItsSentence() {
        assertEquals(listOf("收到啦。🥺", "抱抱你。❤️"), AssistantBubbleSplitter.split("收到啦。🥺抱抱你。❤️", 8))
    }

    @Test fun emptyResponseHasNoMessages() {
        assertEquals(emptyList<String>(), AssistantBubbleSplitter.split(" \r\n ", 8))
    }

    @Test fun askingForThreeDoesNotCutANameOrInventText() {
        assertEquals(listOf("Buruxianhecha"), AssistantBubbleSplitter.split("Buruxianhecha", 8, 3))
    }

    @Test fun explicitCountMergesExtraSentencesWithoutLosingThem() {
        val text = "一句。二句。三句。四句。五句。"
        val parts = AssistantBubbleSplitter.split(text, 8, 3)
        assertEquals(3, parts.size)
        assertEquals(text, parts.joinToString("").replace("\n", ""))
    }

    @Test fun ordinaryCommasDoNotFragmentEveryClause() {
        assertEquals(listOf("我在这里，听你说，你慢慢来。"), AssistantBubbleSplitter.split("我在这里，听你说，你慢慢来。", 8))
    }
}
