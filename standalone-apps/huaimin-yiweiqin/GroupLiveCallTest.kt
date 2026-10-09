package com.cleo.cleos.ai

import com.cleo.cleos.data.db.CompanionEntity
import org.junit.Assert.*
import org.junit.Test

class GroupLiveCallTest {
    private fun person(id: Long) = CompanionEntity(id=id,name="角色$id",createdAt=0,apiBaseUrl="a",apiModel="m")
    @Test fun groupPhoneCanHaveThreeDistinctAIs() {
        assertEquals(listOf(1L,2L,3L),GroupLiveCall.select(
            (1L..6L).map(::person),emptyList(),6,0,false).map {it.id})
    }
    @Test fun mentionGoesOnlyToMentionedRole() {
        val roles=(1L..4L).map(::person)
        assertEquals(listOf(3L),GroupLiveCall.select(roles,listOf(roles[2]),6,0,false).map {it.id})
    }
    @Test fun onlyAtModeCannotWakeUninvitedSpeakers() {
        assertTrue(GroupLiveCall.select(listOf(person(1)),emptyList(),3,2,false).isEmpty())
        assertEquals(1,GroupLiveCall.select(listOf(person(1)),emptyList(),3,2,true).size)
    }
    @Test fun voicePromptNeverImpersonatesEveryone() {
        val s=GroupLiveCall.instruction("小月",listOf("小月","怀民"),null,false)
        assertTrue(s.contains("只能代表自己发言"))
        assertTrue(s.contains("SKIP"))
    }
}
