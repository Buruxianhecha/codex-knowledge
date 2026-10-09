package com.cleo.cleos.ai

import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.MessageEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class GroupRecapIdentityTest {
    private val zone = ZoneId.of("UTC")
    private val messages = listOf(
        MessageEntity(id = 1, conversationId = 77, role = "user", content = "我们聊什么？", createdAt = 1000),
        MessageEntity(id = 2, conversationId = 77, role = "assistant",
            senderCompanionId = 3, content = "聊聊月亮。", createdAt = 2000),
        MessageEntity(id = 3, conversationId = 77, role = "assistant",
            senderCompanionId = 4, content = "我想聊音乐。", createdAt = 3000),
    )
    private val actors = mapOf(3L to "阿弦", 4L to "小艺")
    private val ta = CompanionEntity(id = 3, name = "阿弦",
        apiBaseUrl = "https://example.invalid", apiModel = "test", createdAt = 0)

    @Test fun groupTranscriptUsesExactRealSpeakers() {
        val text = Recap.transcript(messages, zone, actors)
        assertTrue(text.contains("阿弦：聊聊月亮。"))
        assertTrue(text.contains("小艺：我想聊音乐。"))
        assertFalse(text.contains("我：聊聊月亮。"))
    }

    @Test fun singleTranscriptKeepsOriginalFirstPersonFormat() {
        val text = Recap.transcript(messages, zone)
        assertTrue(text.contains("我：聊聊月亮。"))
        assertTrue(text.contains("我：我想聊音乐。"))
    }

    @Test fun groupRecapSystemWarnsNotToMergeIdentities() {
        val system = Recap.request(ta, "林", null, messages, zone, actors).first().content
        assertTrue(system.contains("多人群聊"))
        assertTrue(system.contains("不同成员"))
        assertTrue(system.contains("不要全都写成"))
    }
}
