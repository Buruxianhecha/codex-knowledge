package com.cleo.cleos.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CalendarOvertimeTest {
    private fun at(s: String) = ZonedDateTime.parse(s).toInstant().toEpochMilli()
    private fun profile() = LifeProfile(7L,enabled=true)
    @Test fun published2026HolidaysAreAuthoritative() {
        assertEquals(ChinaWorkCalendar.Kind.STATUTORY, ChinaWorkCalendar.kind(LocalDate.parse("2026-10-01")))
        assertEquals(ChinaWorkCalendar.Kind.HOLIDAY_REST, ChinaWorkCalendar.kind(LocalDate.parse("2026-10-06")))
        assertEquals(ChinaWorkCalendar.Kind.STATUTORY, ChinaWorkCalendar.kind(LocalDate.parse("2026-02-17")))
        assertEquals(ChinaWorkCalendar.Kind.HOLIDAY_REST, ChinaWorkCalendar.kind(LocalDate.parse("2026-02-22")))
        assertEquals(ChinaWorkCalendar.Kind.STATUTORY, ChinaWorkCalendar.kind(LocalDate.parse("2026-05-02")))
    }
    @Test fun transferredSaturdayBecomesOrdinaryWorkday() {
        val start=at("2026-10-10T09:00:00+08:00[Asia/Shanghai]")
        assertEquals(ChinaWorkCalendar.Kind.MAKEUP_WORK, ChinaWorkCalendar.kind(LocalDate.parse("2026-10-10")))
        assertEquals(LifeRules.HOUR_MS,LifeRules.eligibleWorkMillis(profile(),start,start+LifeRules.HOUR_MS))
        assertEquals(150,ChinaWorkCalendar.overtimeRate(LocalDate.parse("2026-10-10"),LifeSchedule()))
    }
    @Test fun transferredHolidayCannotMintOrdinaryWorkSalary() {
        val start=at("2026-10-06T09:00:00+08:00[Asia/Shanghai]")
        assertEquals(0L,LifeRules.eligibleWorkMillis(profile(),start,start+LifeRules.HOUR_MS))
    }
    @Test fun halfHourOvertimeIsOneToOneAndGetsTimePremium() {
        val start=at("2026-10-13T19:00:00+08:00[Asia/Shanghai]")
        val shift=LifeOvertime(startAt=start,endAt=start+30*60_000L,ratePercent=150)
        val p=profile().copy(overtime=listOf(shift))
        val pay=LifeRules.accrueWages(p,start,shift.endAt)
        assertEquals(30*60_000L,pay.overtimeWorked)
        assertEquals(pay.overtimeCents,pay.cents)
        assertEquals(3879L,pay.cents) // 9000/174 * 0.5 * 1.5 RMB, in starcoin cents.
    }
    @Test fun restDayAndStatutoryPremiumDiffer() {
        val rest=at("2026-10-17T10:00:00+08:00[Asia/Shanghai]")
        val legal=at("2026-10-01T10:00:00+08:00[Asia/Shanghai]")
        val a=LifeRules.accrueWages(profile().copy(overtime=listOf(
            LifeOvertime(startAt=rest,endAt=rest+LifeRules.HOUR_MS,ratePercent=200))),rest,rest+LifeRules.HOUR_MS)
        val b=LifeRules.accrueWages(profile().copy(overtime=listOf(
            LifeOvertime(startAt=legal,endAt=legal+LifeRules.HOUR_MS,ratePercent=300))),legal,legal+LifeRules.HOUR_MS)
        assertEquals(10344L,a.cents)
        assertEquals(15517L,b.cents)
    }
    @Test fun disallowOverlappingOrBackdatedOvertime() {
        val start=at("2026-10-17T09:00:00+08:00[Asia/Shanghai]")
        val shift=LifeOvertime(startAt=start,endAt=start+LifeRules.HOUR_MS,ratePercent=200)
        LifeRules.validateOvertime(profile(),shift,start-1)
        try { LifeRules.validateOvertime(profile(),shift,start+1);fail("retroactive") } catch (_:IllegalArgumentException) {}
        try { LifeRules.validateOvertime(profile().copy(overtime=listOf(shift)),shift.copy(id="another"),start-1);
            fail("overlap") } catch (_:IllegalArgumentException) {}
    }
    @Test fun futureCalendarNotInvented() {
        assertEquals(ChinaWorkCalendar.Kind.UNPUBLISHED,ChinaWorkCalendar.kind(LocalDate.parse("2027-10-01")))
        try { ChinaWorkCalendar.overtimeRate(LocalDate.parse("2027-10-01"),LifeSchedule())
            fail("unpublished must reject") } catch (_:IllegalArgumentException) {}
    }
    @Test fun oldSnapshotWithoutOvertimeFieldsStillDecodes() {
        val json=Json { ignoreUnknownKeys=true }
        val old="""{"companionId":7,"enabled":true,"professionId":"engineer"}"""
        val restored=json.decodeFromString<LifeProfile>(old)
        assertTrue(restored.overtime.isEmpty())
        assertEquals(0L,restored.overtimeSalaryCents)
        assertEquals(0L,restored.overtimeWorkedMillis)
        val roundtrip=json.decodeFromString<LifeProfile>(json.encodeToString(restored.copy(overtime=listOf(
            LifeOvertime(startAt=at("2026-10-17T09:00:00+08:00[Asia/Shanghai]"),
                endAt=at("2026-10-17T10:00:00+08:00[Asia/Shanghai]"),ratePercent=200)))))
        assertEquals(1,roundtrip.overtime.size)
    }
}
