package com.cleo.cleos.ai

import com.cleo.cleos.data.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class PatToolTest {
    private val pats = ArrayList<Pair<Long, String>>()
    private val box = ToolBox(unused(), unused(), unused(), patBack = { id, suffix -> pats += id to suffix })

    @Test
    fun theTaPatsAndTheChatShowsTheLineNotTheTool() = runBlocking {
        val out = box.run(ToolCall("a", "pat_user", """{"suffix":"的头"}"""), AppSettings(), conversationId = 7)
        // The pat is the line; the tool leaves none of its own, nor the "在…" while it runs.
        assertEquals("", out.note)
        assertTrue("pat_user" in ToolSpecs.quiet)
        assertEquals(listOf(7L to "的头"), pats)
        // No words at all is just a pat; too many are cut.
        box.run(ToolCall("b", "pat_user", "{}"), AppSettings(), conversationId = 7)
        box.run(ToolCall("c", "pat_user", """{"suffix":"一二三四五六七八九十一二三四五"}"""), AppSettings(), conversationId = 7)
        assertEquals(listOf("的头", "", "一二三四五六七八九十一二"), pats.map { it.second })
    }

    @Test
    fun itIsOnlyOfferedAndAllowedWhileItsSwitchIsOn() = runBlocking {
        assertTrue(ToolGroup.Pat in AppSettings().tools)
        assertTrue(ToolSpecs.offered(setOf(ToolGroup.Pat)).any { it.name == "pat_user" })
        assertFalse(ToolSpecs.offered(setOf(ToolGroup.Todos)).any { it.name == "pat_user" })
        val off = AppSettings(tools = emptySet())
        val refused = box.run(ToolCall("d", "pat_user", "{}"), off, conversationId = 7)
        // Quiet in failure too: the chat line *is* the pat, so "拍一拍没成：设置里关着" would be a
        // line about something that never happened. The model is told, and nothing is patted.
        assertEquals("", refused.note)
        assertTrue(refused.result, refused.result.contains("关掉"))
        assertTrue(pats.isEmpty())
    }

    private inline fun <reified T> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> error("not used") } as T
}
