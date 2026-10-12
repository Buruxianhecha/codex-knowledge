package com.cleo.cleos.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class ChatTablesTest {
    private val sample = ChatTable(
        "搜索与研究",
        listOf("应用", "官方 / 第三方", "核心能力"),
        listOf(listOf("Brave Search", "✅ 官方", "隐私搜索"),
            listOf("Tavily / Exa", "✅ 官方", "AI 搜索"))
    )
    @Test fun structuredPayloadRoundTrips() {
        assertEquals(sample, ChatTables.decode(ChatTables.encode(sample)))
    }
    @Test fun invalidPayloadFallsBackSafely() {
        assertNull(ChatTables.decode("CLEOS_TABLE_V1:{broken"))
        assertNull(ChatTables.decode("hello"))
    }
    @Test fun realJsonToolArgumentsBecomeCells() {
        val args = Json.parseToJsonElement(
            """{"title":"测试","columns":["应用","能力"],"rows":[["小红书","搜索"],["微博","发布"]]}"""
        ).jsonObject
        val table = ChatTables.fromArgs(args)
        assertEquals("测试", table?.title)
        assertEquals(2, table?.rows?.size)
        assertEquals("发布", table?.rows?.get(1)?.get(1))
    }
    @Test fun mismatchedColumnCountFailsRatherThanMakingMess() {
        val args = Json.parseToJsonElement(
            """{"columns":["应用","说明"],"rows":[["只有一列"]]}"""
        ).jsonObject
        assertNull(ChatTables.fromArgs(args))
    }
    @Test fun rejectsInvalidLargeOrEmptyTables() {
        assertNull(ChatTables.validate(ChatTable(columns=listOf("单列"),rows=listOf(listOf("x")))))
        assertNull(ChatTables.validate(ChatTable(columns=listOf("A","B"),rows=emptyList())))
        assertNull(ChatTables.validate(ChatTable(columns=listOf("A","B"),rows=List(121){listOf("x","y")})))
    }
    @Test fun oldMarkdownBecomesRealGrid() {
        val s = "| 应用 | 类型 | 能力 |\n|---|---|---|\n| Brave Search | 官方 | 搜索 |\n| Tavily | 官方 | AI 搜索 |"
        val p = ChatTables.parts(s)
        assertEquals(1,p.size)
        val table = (p.first() as ChatTablePart.Grid).table
        assertEquals(3,table.columns.size)
        assertEquals("Tavily",table.rows.last().first())
    }
    @Test fun aParagraphAndTwoTablesRemainInCorrectOrder() {
        val s = "整理如下：\n\n| 应用 | 能力 |\n|---|---|\n| 小红书 | 搜索 |\n\n| 工具 | 用途 |\n|---|---|\n| Calendar | 日程 |\n谢谢"
        val p = ChatTables.parts(s)
        assertEquals(5,p.size)
        assertTrue(p[0] is ChatTablePart.Words)
        assertTrue(p[1] is ChatTablePart.Grid)
        assertTrue(p[2] is ChatTablePart.Words)
        assertTrue(p[3] is ChatTablePart.Grid)
        assertTrue(p[4] is ChatTablePart.Words)
    }
    @Test fun rawTextAndIncompleteTableAreNotCorrupted() {
        val s = "| col1 | col2 |\n|---|---|\n没有数据"
        assertEquals(s,(ChatTables.parts(s).single() as ChatTablePart.Words).text)
    }
    @Test fun pipesInsideCodeFenceStayPlainText() {
        val s = "```md\n| A | B |\n|---|---|\n| x | y |\n```"
        assertTrue(ChatTables.parts(s).none { it is ChatTablePart.Grid })
    }
    @Test fun escapedPipeStaysInsideCell() {
        val s = "| A | B |\n|---|---|\n| a\\\\|b | value |"
        val table = (ChatTables.parts(s).first() as ChatTablePart.Grid).table
        assertEquals("a|b",table.rows[0][0])
    }
    @Test fun alignedSeparatorIsRecognized() {
        val s = "| A | B |\n|:---|---:|\n| one | two |"
        assertTrue(ChatTables.parts(s).first() is ChatTablePart.Grid)
    }
    @Test fun tsvCopiesCellsNotMarkdownBars() {
        val t = ChatTables.tsv(sample)
        assertTrue(t.contains("应用\t官方 / 第三方\t核心能力"))
        assertTrue(t.contains("Brave Search\t✅ 官方\t隐私搜索"))
    }
}
