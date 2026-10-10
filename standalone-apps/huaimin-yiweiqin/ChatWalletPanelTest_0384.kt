package com.cleo.cleos.ui.chat

import com.cleo.cleos.data.WalletBook
import com.cleo.cleos.data.WalletMovement
import com.cleo.cleos.data.WalletPacket
import org.junit.Assert.*
import org.junit.Test

class ChatWalletPanelTest {
    @Test fun moneyParsingUsesExactIntegerCents() {
        assertEquals(1L, parseMoneyCoins("0.01"))
        assertEquals(888L, parseMoneyCoins("8.88"))
        assertEquals(100000L, parseMoneyCoins("1000"))
    }

    @Test fun invalidAmountNeverBecomesWalletDebit() {
        listOf("0","0.001","1000.01","-2","3e3","1,000","abc","").forEach {
            assertNull("Invalid money accepted: " + it, parseMoneyCoins(it))
        }
    }

    @Test fun ledgerCardsAreScopedToTheirRealConversation() {
        val book = WalletBook(
            initialized = true,
            balances = mapOf(0L to 99700L, 1L to 200L),
            movements = listOf(
                WalletMovement(id = "one", kind = "transfer", from = 0L, to = 1L,
                    amount = 200L, at = 1000L, conversationId = 3L),
                WalletMovement(id = "another", kind = "transfer", from = 0L, to = 2L,
                    amount = 50L, at = 3000L, conversationId = 4L),
            ),
            packets = listOf(
                WalletPacket(id = "red", sender = 0L, recipients = listOf(1L),
                    shares = listOf(50L), random = false, createdAt = 2000L, conversationId = 3L),
            ),
        )
        val items = chatWalletItems(book, 3L)
        assertEquals(listOf("packet:red", "transfer:one"), items.map { it.id })
        assertTrue(chatWalletItems(book, 5L).isEmpty())
        assertTrue(chatWalletItems(book, null).isEmpty())
    }
}
