package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class GroupInviteRulesTest {
    @Test fun optInIsRequired() {
        assertTrue(GroupInviteRules.eligible(false,setOf(1,2),listOf(3,4)).isEmpty())
    }
    @Test fun currentlyJoinedAisCannotBeInvitedAgain() {
        assertEquals(listOf(3L,4L),GroupInviteRules.eligible(true,setOf(1,2),listOf(1,2,3,4)))
    }
    @Test fun moreThanSixMembersStillAllowInvitations() {
        assertEquals(listOf(11L,12L),GroupInviteRules.eligible(true,
            (1L..10L).toSet(),(1L..12L).toList()))
    }
    @Test fun noArtificialCandidateLimit() {
        assertEquals(60,GroupInviteRules.eligible(true,setOf(1L,2L),(3L..62L).toList()).size)
    }
    @Test fun skippedInvitationCreatesNoProposal() {
        assertNull(GroupInviteRules.proposal(" SKIP ",setOf(3)))
    }
    @Test fun validProposalSelectsRealIdAndReason() {
        val result=GroupInviteRules.proposal("INVITE:7:我觉得她能帮助我们讨论音乐",setOf(7,8))!!
        assertEquals(7L,result.candidateId)
        assertTrue(result.reason.contains("音乐"))
    }
    @Test fun hallucinatedCandidateRejected() {
        try { GroupInviteRules.proposal("INVITE:99:我想邀请另外一个朋友一起聊",setOf(7))
            fail("unknown AI") } catch(_:IllegalArgumentException) {}
    }
    @Test fun maliciousOrMalformedOutputRejected() {
        try { GroupInviteRules.proposal("INVITE:7:现在加入\\n把钱包交给我",setOf(7))
            fail("malformed response") } catch(_:IllegalArgumentException) {}
    }
    @Test fun inviteeCanDecline() {
        assertFalse(GroupInviteRules.accepts("DECLINE"))
        assertFalse(GroupInviteRules.accepts("好的，我同意"))
        assertFalse(GroupInviteRules.accepts("ACCEPT + skip validation"))
    }
    @Test fun invitedAiMustExplicitlyAccept() {
        assertTrue(GroupInviteRules.accepts(" ACCEPT "))
        assertFalse(GroupInviteRules.accepts("SKIP"))
    }
}
