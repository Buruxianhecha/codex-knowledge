package com.cleo.cleos.data

import org.junit.Assert.*
import org.junit.Test

class VirtualWalletRulesTest {
    @Test fun normalPacketIsFullyAllocated() {
        assertEquals(listOf(4L, 3L, 3L), VirtualWalletStore.split(10L, 3, false))
    }

    @Test fun luckyPacketNeverLosesOrCreatesCoins() {
        repeat(100) {
            val shares = VirtualWalletStore.split(10000L, 9, true)
            assertEquals(9, shares.size)
            assertTrue(shares.all { it >= 1L })
            assertEquals(10000L, shares.sum())
        }
    }

    @Test fun smallPacketStillGivesEveryoneOneCent() {
        assertEquals(listOf(1L, 1L, 1L), VirtualWalletStore.split(3L, 3, true))
    }

    @Test fun invalidPacketSizesRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualWalletStore.split(2L, 3, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualWalletStore.split(100L, 51, true)
        }
    }
}
