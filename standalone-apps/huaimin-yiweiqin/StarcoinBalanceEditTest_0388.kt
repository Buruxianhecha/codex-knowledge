package com.cleo.cleos.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StarcoinBalanceEditTest {
    @Test fun legacyWalletWithoutNewSupplyFieldKeepsOneThousand() {
        val old = """{"version":1,"initialized":true,"balances":{"0":100000},"movements":[],"packets":[],"transfers":[]}"""
        val book = Json.decodeFromString<WalletBook>(old)
        assertEquals(100_000L, book.totalIssuedCents)
        assertEquals(100_000L, book.balances[0L])
    }

    @Test fun manualBalanceCanBeSetRepeatedlyIncludingZero() {
        val first = VirtualWalletStore.adjustedBook(WalletBook(), 2_000_000L)
        val second = VirtualWalletStore.adjustedBook(first, 10L)
        val third = VirtualWalletStore.adjustedBook(second, 0L)
        assertTrue(first.initialized)
        assertEquals(2_000_000L, first.totalIssuedCents)
        assertEquals(10L, second.totalIssuedCents)
        assertEquals(0L, third.totalIssuedCents)
        assertEquals(0L, third.balances[0L])
        assertEquals("balance_lower", third.movements.last().kind)
    }

    @Test fun editingMyBalanceDoesNotTouchAiOrPendingEscrow() {
        val old = WalletBook(
            initialized=true, totalIssuedCents=100_000L,
            balances=mapOf(0L to 75_000L, 31L to 5_000L),
            transfers=listOf(WalletPendingTransfer(id="test",from=0L,to=31L,
                amount=20_000L,conversationId=1L))
        )
        val next = VirtualWalletStore.adjustedBook(old, 50_000L)
        assertEquals(5_000L, next.balances[31L])
        assertEquals(old.transfers, next.transfers)
        assertEquals(75_000L, next.totalIssuedCents)
        assertEquals(75_000L, next.balances.values.sum() + next.transfers.sumOf { it.amount })
    }

    @Test fun parseStarsPreservesDecimalPrecision() {
        assertEquals(0L, VirtualWalletStore.parseBalanceInput("0"))
        assertEquals(1L, VirtualWalletStore.parseBalanceInput("0.01"))
        assertEquals(123456789L, VirtualWalletStore.parseBalanceInput("1234567.89"))
        assertEquals(999999999L, VirtualWalletStore.parseBalanceInput("9999999.99"))
    }

    @Test fun invalidStarBalanceRejected() {
        listOf("-1","10000000","1.001","12e2","1,000","", "abcd").forEach {
            assertNull(it, VirtualWalletStore.parseBalanceInput(it))
        }
    }
}
