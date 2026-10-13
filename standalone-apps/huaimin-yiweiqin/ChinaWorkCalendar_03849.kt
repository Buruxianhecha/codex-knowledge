package com.cleo.cleos.data

import java.time.LocalDate

/**
 * China (mainland) 2026 official holiday calendar.
 * Source: State Council General Office 国办发明电〔2025〕7号
 * https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm
 * Holiday periods are NOT identical to statutory leave dates.
 */
object ChinaWorkCalendar {
    const val YEAR = 2026
    const val SOURCE = "国务院办公厅 国办发明电〔2025〕7号"
    const val SOURCE_URL = "https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm"

    private fun span(first: String, last: String): Set<LocalDate> {
        val start = LocalDate.parse(first)
        val end = LocalDate.parse(last)
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toSet()
    }
    private val holiday = (
        span("2026-01-01", "2026-01-03") +
        span("2026-02-15", "2026-02-23") +
        span("2026-04-04", "2026-04-06") +
        span("2026-05-01", "2026-05-05") +
        span("2026-06-19", "2026-06-21") +
        span("2026-09-25", "2026-09-27") +
        span("2026-10-01", "2026-10-07")
    )
    private val makeupWork = setOf("2026-01-04", "2026-02-14", "2026-02-28",
        "2026-05-09", "2026-09-20", "2026-10-10").map(LocalDate::parse).toSet()
    private val statutory = (
        setOf("2026-01-01", "2026-04-05", "2026-06-19", "2026-09-25") +
        span("2026-02-16", "2026-02-19").map(LocalDate::toString) +
        span("2026-05-01", "2026-05-02").map(LocalDate::toString) +
        span("2026-10-01", "2026-10-03").map(LocalDate::toString)
    ).map(LocalDate::parse).toSet()

    enum class Kind { STATUTORY, HOLIDAY_REST, MAKEUP_WORK, WEEKEND, WEEKDAY, UNPUBLISHED }
    fun supported(date: LocalDate) = date.year == YEAR
    fun kind(date: LocalDate): Kind = when {
        !supported(date) -> Kind.UNPUBLISHED
        date in statutory -> Kind.STATUTORY
        date in holiday -> Kind.HOLIDAY_REST
        date in makeupWork -> Kind.MAKEUP_WORK
        date.dayOfWeek.value >= 6 -> Kind.WEEKEND
        else -> Kind.WEEKDAY
    }
    fun isOrdinaryWorkday(date: LocalDate, selectedDays: List<Int>): Boolean = when (kind(date)) {
        Kind.STATUTORY, Kind.HOLIDAY_REST -> false
        Kind.MAKEUP_WORK -> true
        Kind.UNPUBLISHED, Kind.WEEKEND, Kind.WEEKDAY -> date.dayOfWeek.value in selectedDays
    }
    fun label(date: LocalDate) = when (kind(date)) {
        Kind.STATUTORY -> "法定节假日"
        Kind.HOLIDAY_REST -> "放假调休"
        Kind.MAKEUP_WORK -> "调休上班"
        Kind.WEEKEND -> "周末休息"
        Kind.WEEKDAY -> "正常工作日"
        Kind.UNPUBLISHED -> "该年度官方调休安排尚未内置，请更新日历"
    }
    fun overtimeRate(date: LocalDate, schedule: LifeSchedule): Int = when (kind(date)) {
        Kind.STATUTORY -> 300
        Kind.HOLIDAY_REST -> 200
        Kind.MAKEUP_WORK -> 150
        Kind.WEEKEND, Kind.WEEKDAY -> if (isOrdinaryWorkday(date, schedule.workDays)) 150 else 200
        Kind.UNPUBLISHED -> throw IllegalArgumentException("未找到 " + date.year + " 年国务院官方调休安排，请更新日历后预约加班")
    }
}
