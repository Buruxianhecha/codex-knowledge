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
    private val now = LocalDateTime.of(2026, 10, 4, 15, 0).atZone(ZoneId.of("Asia/Shanghai"))

    private fun said(id: Long, role: String, text: String) = MessageEntity(id = id, conversationId = 1, role = role, content = text, createdAt = id)
    private fun pat(id: Long, record: PatRecord) = MessageEntity(id = id, conversationId = 1, role = "pat", content = Pats.encode(record), createdAt = id)

    @Test
    fun aPatIsToldWithTheNextMessageAndIsNotAMessageOfItsOwn() {
        val history = listOf(
            said(1, "user", "在吗"),
            said(2, "assistant", "在呀"),
            pat(3, PatRecord(Pats.AI, 2, suffix = "的小脑袋")),
            said(4, "user", "好看吗"),
        )
        val out = Prompt.messages(AppSettings(), ta, history, now)
        assertEquals(listOf("system", "user", "assistant", "user"), out.map { it.role })
        assertTrue(out.last().content.contains("（对方连拍了你的小脑袋 2 下）\n好看吗"))
    }

    @Test
    fun aPatWithNothingAfterItWaitsForTheNextMessage() {
        val history = listOf(said(1, "user", "在吗"), said(2, "assistant", "在呀"), pat(3, PatRecord(Pats.AI)))
        val out = Prompt.messages(AppSettings(), ta, history, now)
        assertEquals(listOf("system", "user", "assistant"), out.map { it.role })
        assertFalse(out.any { it.content.contains("拍了拍") })
    }
}
