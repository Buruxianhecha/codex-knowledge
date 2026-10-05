package com.cleo.cleos.ai

import com.cleo.cleos.data.AppSettings
import com.cleo.cleos.data.PatRecord
import com.cleo.cleos.data.Pats
import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PatPromptTest {
    private val ta = CompanionEntity(id = 1, name = "星", apiBaseUrl = "", apiModel = "", createdAt = 0)
    private val now = LocalDateTime.of(2026, 10, 5, 15, 0).atZone(ZoneId.of("Asia/Shanghai"))

    private fun said(id: Long, role: String, text: String) =
        MessageEntity(id = id, conversationId = 1, role = role, content = text, createdAt = id)

    private fun pat(id: Long, record: PatRecord) =
        MessageEntity(id = id, conversationId = 1, role = "pat", content = Pats.encode(record), createdAt = id)

    @Test
    fun pattingTaIsAUserTurnOfItsOwn() {
        val history = listOf(
            said(1, "user", "在吗"),
            said(2, "assistant", "在呀"),
            pat(3, PatRecord(Pats.AI, 1, suffix = "的小脑袋")),
        )
        val out = Prompt.messages(AppSettings(), ta, history, now)
        assertEquals(listOf("system", "user", "assistant", "user"), out.map { it.role })
        assertTrue(out.last().content.contains("拍了拍你的小脑袋"))
        assertTrue(out.last().content.contains("自然回应"))
    }

    @Test
    fun repeatedPatsAreOneTurnAndKeepTheirCount() {
        val history = listOf(
            said(1, "user", "在吗"),
            said(2, "assistant", "在呀"),
            pat(3, PatRecord(Pats.AI, 4, suffix = "的肩膀")),
        )
        val out = Prompt.messages(AppSettings(), ta, history, now)
        assertEquals(listOf("system", "user", "assistant", "user"), out.map { it.role })
        assertTrue(out.last().content.contains("连拍了你的肩膀 4 下"))
    }

    @Test
    fun aPatAlreadyAnsweredIsNotRepeatedWithTheNextMessage() {
        val history = listOf(
            said(1, "user", "在吗"),
            said(2, "assistant", "在呀"),
            pat(3, PatRecord(Pats.AI)),
            said(4, "assistant", "干嘛呀"),
            said(5, "user", "嘿嘿"),
        )
        val out = Prompt.messages(AppSettings(), ta, history, now)
        assertEquals(1, out.count { it.content.contains("拍了拍你") })
        assertTrue(out.last().content.endsWith("嘿嘿"))
    }

    @Test
    fun selfPatStillWaitsAndRidesWithTheNextMessage() {
        val history = listOf(
            said(1, "user", "在吗"),
            said(2, "assistant", "在呀"),
            pat(3, PatRecord(Pats.ME, 2)),
        )
        val beforeSpeaking = Prompt.messages(AppSettings(), ta, history, now)
        assertEquals(listOf("system", "user", "assistant"), beforeSpeaking.map { it.role })
        assertFalse(beforeSpeaking.any { it.content.contains("连拍") })

        val afterSpeaking = Prompt.messages(AppSettings(), ta, history + said(4, "user", "嘿嘿"), now)
        assertEquals(listOf("system", "user", "assistant", "user"), afterSpeaking.map { it.role })
        assertTrue(afterSpeaking.last().content.contains("（对方连拍了自己 2 下）\n嘿嘿"))
    }

    @Test
    fun taOwnPatIsNotFedBackAsUserInput() {
        val history = listOf(
            said(1, "user", "在吗"),
            pat(2, PatRecord(Pats.FROM_AI)),
            said(3, "assistant", "在"),
            said(4, "user", "好"),
        )
        assertFalse(Prompt.messages(AppSettings(), ta, history, now).any { it.content.contains("拍了拍") })
    }
}
