package com.cleo.cleos.ai

import com.cleo.cleos.data.db.MessageEntity
import org.junit.Assert.*
import org.junit.Test

class WalletReactiveEventsTest {
    @Test fun transactionNotificationHasExplicitSystemOrigin() {
        val text = WalletReactiveEvents.messageText(true, "receipt-123")
        assertTrue(text.contains("发送星币红包"))
        assertTrue(text.contains("真实扣账成功"))
        assertTrue(text.contains("立即"))
        assertFalse(text.contains("自动领取成功"))
    }

    @Test fun transferNotificationDoesNotExposePrivatePayeeOrMoney() {
        val text = WalletReactiveEvents.messageText(false, "receipt-234")
        assertTrue(text.contains("星币转账"))
        assertFalse(text.contains("100.00"))
        assertFalse(text.contains("目标角色"))
    }

    @Test fun onlyDurableSystemTaggedRowsAreHiddenFromUi() {
        val event = MessageEntity(conversationId = 7L, role = "user",
            content = WalletReactiveEvents.messageText(true, "123"), createdAt = 1L)
        assertTrue(WalletReactiveEvents.isEvent(event))
        assertFalse(WalletReactiveEvents.isEvent(event.copy(content = "我转账了")))
        assertFalse(WalletReactiveEvents.isEvent(event.copy(role = "assistant")))
    }

    @Test fun targetedGroupRecipientsAreStrictlyValidated() {
        assertEquals(setOf(11L, 13L), WalletReactiveEvents.targetIds("11,13,999,11", setOf(11L, 12L, 13L)))
        assertEquals(emptySet<Long>(), WalletReactiveEvents.targetIds("999,-1,x", setOf(11L, 12L)))
    }
}
