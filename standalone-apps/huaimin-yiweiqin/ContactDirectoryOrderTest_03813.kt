package com.cleo.cleos.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactDirectoryOrderTest {
    @Test fun storedOrderSurvivesRelaunchAndAddsNewAIAtEnd() {
        val order = ContactDirectoryOrder.encode(listOf(14, 2, 7))
        assertEquals("14,2,7", order)
        assertEquals(listOf(14L,2L,7L,20L),
            ContactDirectoryOrder.apply(listOf(2,7,14,20),ContactDirectoryOrder.decode(order)))
    }

    @Test fun moveIsBoundedAndStable() {
        val ids=listOf(1L,2L,3L,4L)
        assertEquals(listOf(1L,3L,2L,4L),ContactDirectoryOrder.move(ids,2L,1))
        assertEquals(listOf(2L,1L,3L,4L),ContactDirectoryOrder.move(ids,2L,-1))
        assertEquals(ids,ContactDirectoryOrder.move(ids,1L,-1))
        assertEquals(ids,ContactDirectoryOrder.move(ids,4L,1))
        assertEquals(ids,ContactDirectoryOrder.move(ids,99L,1))
    }

    @Test fun pinsAndDeletionDoNotResurrectOldPersonas() {
        val pinned=ContactDirectoryOrder.top(listOf(7L,14L,1L),14L)
        assertEquals(listOf(14L,7L,1L),pinned)
        val after=ContactDirectoryOrder.afterDelete(pinned,14L)
        assertEquals(listOf(7L,1L),after)
        assertEquals(after,ContactDirectoryOrder.apply(after,ContactDirectoryOrder.decode("14,7,1")))
    }

    @Test fun malformedPrefDataAndDuplicateIdsAreIgnored() {
        assertEquals(listOf(3L,4L),ContactDirectoryOrder.decode("xxx,3,3,-6,4,,0"))
        assertEquals(listOf(3L,4L,5L),ContactDirectoryOrder.apply(listOf(3,4,5), listOf(4,3,4,44)))
    }
}
