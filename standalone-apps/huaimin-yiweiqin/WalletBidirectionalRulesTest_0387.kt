package com.cleo.cleos.ui.chat

import com.cleo.cleos.data.WalletBook
import com.cleo.cleos.data.WalletPacket
import com.cleo.cleos.data.WalletClaim
import com.cleo.cleos.data.WalletPendingTransfer
import com.cleo.cleos.data.WalletMovement
import com.cleo.cleos.ai.WalletChatBridge
import org.junit.Assert.*
import org.junit.Test

class WalletBidirectionalRulesTest {
    private val clock = System.currentTimeMillis()

    @Test fun senderCanJoinOnlyExplicitLuckyGroupPacket() {
        val p = WalletPacket(id="my-lucky", sender=0L, recipients=listOf(1L,0L),
            shares=listOf(250L, 750L), random=true, conversationId=99L,
            allowSenderClaim=true, createdAt=clock)
        val item = chatWalletItems(WalletBook(packets=listOf(p)),99L).single()
        assertTrue(item.canClaim)
        assertFalse(item.muted)
        val normal = p.copy(id="not-lucky", allowSenderClaim=false, random=false)
        assertFalse(chatWalletItems(WalletBook(packets=listOf(normal)),99L).single().canClaim)
    }
    @Test fun incomingAiPacketIsVisibleAndClickableByUser() {
        val p = WalletPacket(id="ai-gift", sender=42L, recipients=listOf(0L),
            shares=listOf(660L), random=false, conversationId=11L,
            createdAt=clock)
        val item = chatWalletItems(WalletBook(packets=listOf(p)),11L).single()
        assertEquals(42L,item.sender)
        assertTrue(item.canClaim)
        assertEquals(660L,item.amount)
    }
    @Test fun claimedOrExhaustedEnvelopeIsDimmed() {
        val p = WalletPacket(id="all-claimed", sender=42L, recipients=listOf(0L),
            shares=listOf(660L), random=false, conversationId=11L,
            claims=listOf(WalletClaim(0L,660L,clock)),createdAt=clock)
        val item = chatWalletItems(WalletBook(packets=listOf(p)),11L).single()
        assertTrue(item.muted)
        assertFalse(item.canClaim)
        assertTrue(item.status.contains("已领完"))
    }
    @Test fun aiTransferStartsPendingThenDimsAfterConfirmation() {
        val pending = WalletPendingTransfer(id="ai-transfer",from=12L,to=0L,
            amount=100L,conversationId=99L,createdAt=clock)
        val first = chatWalletItems(WalletBook(transfers=listOf(pending)),99L).single()
        assertTrue(first.canReceiveTransfer)
        assertFalse(first.muted)
        val accepted = chatWalletItems(WalletBook(transfers=listOf(
            pending.copy(state="accepted",completedAt=clock))),99L).single()
        assertTrue(accepted.muted)
        assertFalse(accepted.canReceiveTransfer)
        assertEquals("已收款",accepted.status)
    }
    @Test fun aiHasAccurateWalletStateWithoutCrossChatLeaks() {
        val pending = WalletPendingTransfer(id="incoming",from=0L,to=33L,
            amount=100L,conversationId=12L,createdAt=clock)
        val other = pending.copy(id="other-chat",conversationId=13L)
        val text = WalletChatBridge.contextFor(
            WalletBook(balances=mapOf(33L to 888L),transfers=listOf(pending,other)),12L,33L)
        assertTrue(text.contains("incoming"))
        assertFalse(text.contains("other-chat"))
        assertTrue(text.contains("pending"))
        assertTrue(text.contains("8.88"))
    }
}
