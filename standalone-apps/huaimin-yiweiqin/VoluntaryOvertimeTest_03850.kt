package com.cleo.cleos.ai

import com.cleo.cleos.data.LifeOvertime
import com.cleo.cleos.data.LifeProfile
import com.cleo.cleos.data.LifeRules
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class VoluntaryOvertimeTest {
    private fun at(value:String)=ZonedDateTime.parse(value).toInstant().toEpochMilli()
    private fun profile()=LifeProfile(7L,enabled=true,allowVoluntaryOvertime=true)
    @Test fun assignedShiftIsNeverMistakenForVoluntary() {
        val from=at("2026-10-17T10:00:00+08:00[Asia/Shanghai]")
        val p=profile().copy(overtime=listOf(LifeOvertime(startAt=from,
            endAt=from+LifeRules.HOUR_MS,ratePercent=200)))
        val wages=LifeRules.accrueWages(p,from,from+LifeRules.HOUR_MS)
        assertTrue(wages.overtimeCents>0L)
        assertEquals(0L,wages.voluntaryOvertimeCents)
        assertEquals(0L,wages.voluntaryOvertimeWorked)
    }
    @Test fun voluntaryShiftIsPaidExactlyOnceAndTagged() {
        val from=at("2026-10-17T10:00:00+08:00[Asia/Shanghai]")
        val p=profile().copy(overtime=listOf(LifeOvertime(startAt=from,
            endAt=from+LifeRules.HOUR_MS,ratePercent=200,initiator="voluntary",
            reason="我决定攒钱准备一份特别的礼物")))
        val wages=LifeRules.accrueWages(p,from,from+LifeRules.HOUR_MS)
        assertEquals(wages.overtimeCents,wages.voluntaryOvertimeCents)
        assertEquals(LifeRules.HOUR_MS,wages.voluntaryOvertimeWorked)
        assertEquals(10344L,wages.cents)
        assertEquals(0L,LifeRules.accrueWages(p,from,from).cents)
    }
    @Test fun disabledVoluntaryCannotScheduleShift() {
        val from=at("2026-10-17T10:00:00+08:00[Asia/Shanghai]")
        val shift=LifeOvertime(startAt=from,endAt=from+LifeRules.HOUR_MS,ratePercent=200,
            initiator="voluntary",reason="为了生日礼物攒钱")
        try {
            LifeRules.validateOvertime(profile().copy(allowVoluntaryOvertime=false),shift,from-1)
            fail("no consent")
        } catch(_:IllegalArgumentException) {}
    }
    @Test fun modelCanReallyChooseToSkip() {
        assertNull(VoluntaryDecisionRules.parse("SKIP",6))
        assertNull(VoluntaryDecisionRules.parse("{\"action\":\"SKIP\"}",6))
    }
    @Test fun parsedModelChoiceIsBounded() {
        val plan=VoluntaryDecisionRules.parse(
            "{\"action\":\"PLAN\",\"choice\":0,\"goal\":\"为一份生日礼物攒钱\",\"targetStars\":200,\"reason\":\"想在空闲时间赚一份诚实的加班收入\"}",6)
        assertEquals(20000L,plan!!.targetCents)
        assertEquals(0,plan.index)
        try {
            VoluntaryDecisionRules.parse(
                "{\"action\":\"PLAN\",\"choice\":99,\"goal\":\"礼物\",\"targetStars\":100,\"reason\":\"我想赚一点星币\"}",2)
            fail("unchecked options")
        } catch(_:IllegalArgumentException) {}
    }
    @Test fun candidatesRespectExistingWorkAndHolidayBoundaries() {
        val now=at("2026-10-13T19:00:00+08:00[Asia/Shanghai]")
        val options=VoluntaryDecisionRules.candidates(profile(),now)
        assertTrue(options.isNotEmpty())
        assertTrue(options.size<=6)
        for (slot in options) {
            val p=profile()
            val candidate=LifeOvertime(startAt=slot.begin,endAt=slot.end,
                ratePercent=slot.rate,initiator="voluntary",reason="想买一份礼物")
            LifeRules.validateOvertime(p,candidate,now)
            assertTrue(slot.end>slot.begin)
        }
    }
    @Test fun oldV03849SnapshotPreservesAssignedKind() {
        val json=Json { ignoreUnknownKeys=false }
        val old="""{"startAt":1792202400000,"endAt":1792206000000,"ratePercent":200}"""
        val restored=json.decodeFromString<LifeOvertime>(old)
        assertEquals("assigned",restored.initiator)
        val profile=profile().copy(overtime=listOf(restored))
        assertEquals(1,json.decodeFromString<LifeProfile>(json.encodeToString(profile)).overtime.size)
    }
    @Test fun goalIsLedgerNotFreeWalletCredit() {
        val p=profile()
        assertEquals(0L,p.voluntaryOvertimeSalaryCents)
        assertNull(p.savingsGoal)
        assertEquals(0L,p.voluntaryOvertimeWorkedMillis)
        assertEquals(0L,p.salaryCents)
    }
}
