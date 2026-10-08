package com.cleo.cleos.ai

import com.cleo.cleos.data.db.SharedMessageRow
import org.junit.Assert.*
import org.junit.Test

class ChatHistorySearchTest {
    private fun row(id: Long, content: String, time: Long = id) =
        SharedMessageRow(id, 50, "小艺的私聊", 8, if (id % 2L == 0L) 8 else null,
            if (id % 2L == 0L) "assistant" else "user", content, time)

    @Test fun recognizesExactHistoryIntent() {
        assertTrue(ChatHistorySearch.wanted("小艺，你能看到我和小旋聊过什么吗？"))
        assertTrue(ChatHistorySearch.wanted("查看聊天记录 @小艺"))
        assertTrue(ChatHistorySearch.wanted("我和小旋以前说了什么"))
        assertFalse(ChatHistorySearch.wanted("今天一起出去吃饭吗？"))
    }
    @Test fun originalTextAndSpeakersArePreserved() {
        val page = ChatHistorySearch.page(listOf(row(2, "第二句原文"), row(1, "第一句原文")), 2, 0, mapOf(8L to "小艺"), "我")
        assertTrue(page.contains("第一句原文"))
        assertTrue(page.contains("第二句原文"))
        assertTrue(page.contains("小艺：第二句原文"))
        assertTrue(page.contains("我：第一句原文"))
        assertTrue(page.contains("实时原始消息查询"))
    }
    @Test fun pagingStatesCountsAndNextOffset() {
        val page = ChatHistorySearch.page(listOf(row(4, "原文")), 100, 20, mapOf(8L to "小艺"), "我")
        assertTrue(page.contains("共 100 条"))
        assertTrue(page.contains("offset=21"))
    }
    @Test fun noResultsNeverClaimDeletedOrNoHistory() {
        val page = ChatHistorySearch.page(emptyList(), 0, 0, emptyMap(), "我")
        assertTrue(page.contains("无法据此确定"))
        assertTrue(page.contains("不要编造"))
    }
    @Test fun longMessageCanBeReadInMultiplePieces() {
        val r = row(1, "啊".repeat(18000))
        val first = ChatHistorySearch.fullMessage(r, 0, 12000)
        val second = ChatHistorySearch.fullMessage(r, 12000, 12000)
        assertTrue(first.contains("start=12000"))
        assertTrue(second.contains("这条消息已读完"))
        assertEquals(18000, r.content.length)
    }
    @Test fun longPageDoesNotPretendToShowWholeMessage() {
        val page = ChatHistorySearch.page(listOf(row(1, "字".repeat(5000))), 1, 0, emptyMap(), "我")
        assertTrue(page.contains("read_chat_message"))
    }
    @Test fun dayBoundsAcceptsOnlyCalendarDates() {
        assertTrue(ChatHistorySearch.dayBounds("2026-10-08") != null)
        assertNull(ChatHistorySearch.dayBounds("2026-13-88"))
        val bounds = ChatHistorySearch.dayBounds("2026-10-08")!!
        assertTrue(bounds.second > bounds.first)
    }
}
