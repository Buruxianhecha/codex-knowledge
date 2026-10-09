package com.cleo.cleos.data

import org.junit.Assert.*
import org.junit.Test

class GroupPatSpeakerTest {
    @Test fun encodedAiPatPreservesActualSpeakingRole() {
        val pat = PatRecord(Pats.FROM_AI, 1, "拍", "肩膀", targetCompanionId = 19L)
        val got = Pats.decode(Pats.encode(pat))!!
        assertEquals(19L, got.targetCompanionId)
        assertEquals(Pats.FROM_AI, got.who)
    }
}
