package com.cleo.cleos.ai

import com.cleo.cleos.data.WalletBook
import com.cleo.cleos.data.WalletMovement
import com.cleo.cleos.data.WalletPacket
import org.junit.Assert.*
import org.junit.Test

class WalletChatBridgeTest {
    private val book = WalletBook(
        initialized = true,
        balances = mapOf(0L to 98000L, 11L to 200L, 12L to 0L),
        movements = listOf(
            WalletMovement(id="current",kind="transfer",from=0L,to=11L,amount=200L,
                conversationId=77L,at=123L),
            WalletMovement(id="otherchat",kind="transfer",from=0L,to=11L,amount=500L,
                conversationId=88L,at=124L),
            WalletMovement(id="otherperson",kind="transfer",from=0L,to=12L,amount=300L,
                conversationId=77L,at=125L)
        ),
        packets = listOf(
            WalletPacket(id="red-a",sender=0L,recipients=listOf(11L,12L),
                shares=listOf(100L,100L),random=false,conversationId=77L),
            WalletPacket(id="red-b",sender=0L,recipients=listOf(11L),
                shares=listOf(100L),random=false,conversationId=88L),
        ),
    )

    @Test fun seesOnlyCurrentConversationWalletEntries() {
        val visible = WalletChatBridge.contextFor(book,77L,11L)
        assertTrue(visible.contains("red-a"))
        assertTrue(visible.contains("current"))
        assertFalse(visible.contains("red-b"))
        assertFalse(visible.contains("otherchat"))
    }

    @Test fun groupAiCannotSeePrivateTransferToOtherCompanion() {
        val visible = WalletChatBridge.contextFor(book,77L,12L)
        assertTrue(visible.contains("otherperson"))
        assertFalse(visible.contains("用户转给你；金额=2.00"))
        assertFalse(visible.contains("id=current"))
    }

    @Test fun noTransactionDoesNotInventWalletMessage() {
        assertEquals("", WalletChatBridge.contextFor(book,999L,11L))
    }

    @Test fun packetNotAddressedToAiIsNotClaimableInContext() {
        val other = WalletBook(packets=listOf(WalletPacket(
            id="excluded",sender=0L,recipients=listOf(12L),shares=listOf(500L),
            random=false,conversationId=77L)))
        val visible = WalletChatBridge.contextFor(other,77L,11L)
        assertTrue(visible.contains("无资格领取"))
    }
}
