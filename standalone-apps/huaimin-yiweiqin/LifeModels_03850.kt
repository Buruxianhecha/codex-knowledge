package com.cleo.cleos.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

@Serializable
data class LifeSchedule(
    val zoneId: String = "Asia/Shanghai",
    val workDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val workStartMinute: Int = 540,
    val workEndMinute: Int = 1080,
    val lunchStartMinute: Int = 720,
    val lunchEndMinute: Int = 780,
    val restStartMinute: Int = 1320,
    val restEndMinute: Int = 1380,
    val sleepStartMinute: Int = 1380,
    val sleepEndMinute: Int = 420
)

@Serializable
data class LifeOvertime(val id: String = UUID.randomUUID().toString(), val startAt: Long,
    val endAt: Long, val ratePercent: Int,
    /** 'assigned' = user/employer-scheduled; 'voluntary' = actual model-chosen after opt-in. */
    val initiator: String = "assigned",
    val reason: String = "",
    val goalId: String? = null)

@Serializable
data class LifeSavingsGoal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetCents: Long,
    /** Only newly earned voluntary-overtime salary counts; old wallet funds are never reissued. */
    val baselineVoluntaryCents: Long,
    val createdAt: Long
)

@Serializable
data class LifeProfession(
    val id: String, val title: String, val monthlyRmb: Int,
    val skillId: String, val skillTitle: String
)

/** Simulation reference values, not real-time recruitment statistics or a real wage promise. */
object LifeProfessions {
    val presets = listOf(
        LifeProfession("engineer", "工程师", 9000, "work_engineer", "工程项目实践"),
        LifeProfession("technician", "技术员", 6500, "work_technician", "技术操作实践"),
        LifeProfession("bookseller", "书店店员", 4800, "work_bookseller", "书店服务实践"),
        LifeProfession("software", "软件工程师", 15000, "work_software", "软件工程实践")
    )
    fun get(id: String): LifeProfession = presets.singleOrNull { it.id == id }
        ?: throw IllegalArgumentException("请选择有效职业")
    fun effectiveMonthlyRmb(profile: LifeProfile): Int = LifeRules.effectiveMonthlyRmb(profile)
}

@Serializable
data class LifePending(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: Long,
    /** A monotonic chat-event timestamp; messageId disambiguates equal timestamps. */
    val messageCursor: Long,
    val purpose: String = "reply",
    val createdAt: Long,
    val messageId: Long = 0L,
    val attempts: Int = 0,
    val lastAttemptAt: Long = 0L,
    val nextAttemptAt: Long = 0L
)

@Serializable
data class LifeSkill(
    val id: String,
    val title: String,
    val experiencePoints: Int = 0,
    val verifiedKnowledgeCount: Int = 0
)

@Serializable
data class LifeKnowledge(
    val id: String,
    val bookId: String,
    val lessonId: String,
    val title: String,
    /** Verbatim source excerpt, never a claim to have mastered an entire book. */
    val excerpt: String,
    val source: String,
    val verifiedAt: Long,
    val summary: String = "",
    val method: String = "",
    val model: String = "",
    val skillId: String = ""
)

@Serializable
data class LifeStudyEvidence(
    val bookId: String,
    val lessonId: String,
    val summary: String,
    val answerIndex: Int,
    val applicationAnswerIndex: Int? = null,
    val model: String,
    val at: Long,
    val passed: Boolean,
    val actor: String = "model"
)

@Serializable
data class LifeReading(
    val bookId: String,
    val startedAt: Long,
    val lastReadAt: Long,
    val readLessonIds: List<String> = emptyList(),
    val passedLessonIds: List<String> = emptyList(),
    val readingMillis: Long = 0L
)

@Serializable
data class LifeLesson(
    val id: String,
    val title: String,
    val excerpt: String,
    val startOffset: Int,
    val endOffset: Int,
    val skillId: String,
    val skillTitle: String,
    val question: String,
    val choices: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val transferQuestion: String = "",
    val transferChoices: List<String> = emptyList(),
    val transferCorrectIndex: Int? = null
)

@Serializable
data class LifeBook(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val source: String,
    val content: String,
    val lessons: List<LifeLesson>,
    val importedAt: Long = System.currentTimeMillis(),
    val builtIn: Boolean = false
)

@Serializable
data class LifeSettlement(
    val id: String,
    val companionId: Long,
    val cents: Long,
    val workedMillis: Long,
    val fromAt: Long,
    val toAt: Long,
    val at: Long,
    val paid: Boolean = false
)

@Serializable
data class LifeProfile(
    val companionId: Long,
    /** Nothing blocks existing characters until a user explicitly activates that character. */
    val enabled: Boolean = false,
    val strict: Boolean = true,
    val professionId: String = "engineer",
    val customMonthlyRmb: Int? = null,
    val schedule: LifeSchedule = LifeSchedule(),
    val overtime: List<LifeOvertime> = emptyList(),
    val allowVoluntaryOvertime: Boolean = false,
    /** Context entered by the user, e.g. a confirmed birthday; never treated as verified by the model. */
    val voluntaryGoalHint: String = "",
    val lastVoluntaryDecisionDay: String = "",
    val savingsGoal: LifeSavingsGoal? = null,
    val enrolledAt: Long = 0L,
    val lastAccruedAt: Long = 0L,
    val workedMillis: Long = 0L,
    val overtimeWorkedMillis: Long = 0L,
    val overtimeSalaryCents: Long = 0L,
    val voluntaryOvertimeSalaryCents: Long = 0L,
    val voluntaryOvertimeWorkedMillis: Long = 0L,
    val professionWorkedMillis: Map<String, Long> = emptyMap(),
    val experiencePoints: Int = 0,
    /** Cumulative paid salary in starcoin cents (100 cents = 1 starcoin). */
    val salaryCents: Long = 0L,
    val salaryAccruedCents: Long = 0L,
    val unpaidSalaryCents: Long = 0L,
    val unpaidWorkedMillis: Long = 0L,
    val unpaidFromAt: Long = 0L,
    /** Fractional cents, denominator = 21.75 * 8 * 60 * 60 * 1000. */
    val salaryRemainder: Long = 0L,
    val settlementSequence: Long = 0L,
    val skills: List<LifeSkill> = emptyList(),
    val knowledge: List<LifeKnowledge> = emptyList(),
    val readings: List<LifeReading> = emptyList(),
    val studyEvidence: List<LifeStudyEvidence> = emptyList(),
    /** This changes reply permission only, never creates paid working time. */
    val wakeUntil: Long = 0L,
    val restStartedAt: Long = 0L,
    val restUntil: Long = 0L,
    val pending: List<LifePending> = emptyList(),
    val answeredCursors: Map<Long, Long> = emptyMap(),
    val answeredMessageIds: Map<Long, Long> = emptyMap(),
    val offlineSkippedMillis: Long = 0L
)

@Serializable
data class LifeSnapshot(
    val version: Int = 1,
    val generation: String = UUID.randomUUID().toString(),
    val profiles: Map<Long, LifeProfile> = emptyMap(),
    val books: List<LifeBook> = emptyList(),
    val settlements: List<LifeSettlement> = emptyList()
)

data class LifeAvailability(
    val label: String,
    val isBusy: Boolean,
    val busyUntil: Long?,
    val phase: String
)

/** Pure, testable rules. All times are epoch milliseconds, boundaries use the chosen IANA zone. */
object LifeRules {
    const val RMB_PER_STARCOIN = 1
    const val MAX_BOOK_CHARS = 300_000
    const val MAX_BOOKS = 40
    const val MAX_LESSONS_PER_BOOK = 80
    const val MAX_TITLE_CHARS = 100
    const val MAX_SOURCE_CHARS = 300
    const val HOUR_MS = 3_600_000L
    const val MAX_OFFLINE_MS = 72L * HOUR_MS
    const val SALARY_DENOMINATOR = 174L * HOUR_MS
    const val MIN_MONTHLY_RMB = 2740
    const val MAX_MONTHLY_RMB = 60_000
    const val MAX_SETTLEMENT_CENTS = 100_000L
    const val MAX_TIME = 4_102_444_800_000L // 2100-01-01; reject corrupt time ranges.

    fun validateSchedule(s: LifeSchedule) {
        require(s.zoneId.length in 1..80) { "时区无效" }
        ZoneId.of(s.zoneId)
        require(s.workDays.isNotEmpty() && s.workDays.size <= 7 &&
            s.workDays.distinct().size == s.workDays.size && s.workDays.all { it in 1..7 }) {
            "工作日请选择周一至周日" }
        require(listOf(s.workStartMinute, s.workEndMinute, s.lunchStartMinute, s.lunchEndMinute,
            s.restStartMinute, s.restEndMinute, s.sleepStartMinute, s.sleepEndMinute)
            .all { it in 0..1439 }) { "日程时间无效" }
        require(s.workStartMinute != s.workEndMinute) { "上下班时间不能相同" }
        val duration = (s.workEndMinute - s.workStartMinute + 1440) % 1440
        require(duration in 60..960) { "一次工作时段应为 1～16 小时" }
    }

    fun effectiveMonthlyRmb(p: LifeProfile): Int {
        val base = p.customMonthlyRmb ?: LifeProfessions.get(p.professionId).monthlyRmb
        val roleHours = (p.professionWorkedMillis[p.professionId] ?: 0L) / HOUR_MS
        // +2% per 160 paid-practice hours, capped at 20%; verified learning adds <=10%.
        val experienceRaise = min(20L, roleHours / 160L * 2L).toInt()
        val relevant = when (p.professionId) {
            "engineer", "software" -> setOf("engineering_diagnosis", "task_communication", "source_verification")
            "technician" -> setOf("engineering_diagnosis", "task_communication")
            "bookseller" -> setOf("task_communication", "source_verification")
            else -> emptySet()
        }
        val learnedRaise = min(10, p.knowledge.count {
            it.method == "model_source_and_application" && it.skillId in relevant
        } / 3)
        return base * (100 + experienceRaise + learnedRaise) / 100
    }

    fun salaryFor(p: LifeProfile, millis: Long): Pair<Long, Long> {
        require(millis in 0L..MAX_OFFLINE_MS && p.salaryRemainder in 0 until SALARY_DENOMINATOR)
        // 1 RMB equals 1 starcoin; retain fractional cents across accrual ticks.
        val numerator = millis * effectiveMonthlyRmb(p).toLong() * 100L + p.salaryRemainder
        return (numerator / SALARY_DENOMINATOR) to (numerator % SALARY_DENOMINATOR)
    }

    private data class Span(val start: Long, val end: Long)
    private fun interval(date: LocalDate, start: Int, end: Int, zone: ZoneId): Span? {
        if (start == end) return null // equal start/end disables that non-work interval.
        val a = date.atStartOfDay().plusMinutes(start.toLong()).atZone(zone).toInstant().toEpochMilli()
        val b = date.plusDays(if (end <= start) 1L else 0L).atStartOfDay()
            .plusMinutes(end.toLong()).atZone(zone).toInstant().toEpochMilli()
        return Span(a, b)
    }

    private fun intervals(p: LifeProfile, from: Long, to: Long): Pair<List<Span>, List<Span>> {
        val s = p.schedule
        val zone = ZoneId.of(s.zoneId)
        val first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1)
        val last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate().plusDays(1)
        val work = mutableListOf<Span>()
        val excludes = mutableListOf<Span>()
        var date = first
        while (!date.isAfter(last)) {
            val scheduled = if (s.zoneId == "Asia/Shanghai")
                ChinaWorkCalendar.isOrdinaryWorkday(date, s.workDays)
                else date.dayOfWeek.value in s.workDays
            if (scheduled) interval(date, s.workStartMinute, s.workEndMinute, zone)?.let(work::add)
            interval(date, s.lunchStartMinute, s.lunchEndMinute, zone)?.let(excludes::add)
            interval(date, s.restStartMinute, s.restEndMinute, zone)?.let(excludes::add)
            interval(date, s.sleepStartMinute, s.sleepEndMinute, zone)?.let(excludes::add)
            date = date.plusDays(1)
        }
        if (p.restUntil > p.restStartedAt && p.restStartedAt > 0) excludes += Span(p.restStartedAt, p.restUntil)
        return work to excludes
    }

    data class PaidSlice(val millis: Long, val ratePercent: Int, val initiator: String = "regular")
    data class WageAccrual(val cents: Long, val remainder: Long, val worked: Long,
        val overtimeWorked: Long, val overtimeCents: Long,
        val voluntaryOvertimeCents: Long, val voluntaryOvertimeWorked: Long)

    fun validateOvertime(p: LifeProfile, shift: LifeOvertime, now: Long) {
        require(p.enabled) { "请先开启角色工作" }
        require(shift.initiator in setOf("assigned","voluntary")) { "未知加班来源" }
        require(shift.reason.length <= 240) { "加班理由过长" }
        require(shift.initiator != "voluntary" || p.allowVoluntaryOvertime) { "未授权AI自主加班" }
        require(p.schedule.zoneId == "Asia/Shanghai") { "中国法定日历需要上海时区" }
        require(shift.startAt > now && shift.endAt > shift.startAt &&
            shift.endAt <= MAX_TIME) { "只能预约未来加班" }
        val zone = ZoneId.of(p.schedule.zoneId)
        val date = Instant.ofEpochMilli(shift.startAt).atZone(zone).toLocalDate()
        require(Instant.ofEpochMilli(shift.endAt - 1).atZone(zone).toLocalDate() == date) {
            "跨午夜加班请按自然日分开预约" }
        val percent = ChinaWorkCalendar.overtimeRate(date, p.schedule)
        require(shift.ratePercent == percent) { "加班倍率必须由官方日历决定" }
        val duration = shift.endAt - shift.startAt
        require(duration <= (if (percent == 150) 3L else 8L) * HOUR_MS) {
            "工作日加班最多3小时；休息日或法定节假日最多8小时" }
        require(regularWorkMillis(p, shift.startAt, shift.endAt) == 0L) {
            "加班不得与正常计薪工时重叠" }
        require(p.overtime.none { shift.startAt < it.endAt && shift.endAt > it.startAt }) {
            "加班时段重叠" }
        val sameMonth = p.overtime.filter {
            val oldDate = Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalDate()
            oldDate.year == date.year && oldDate.monthValue == date.monthValue
        }.sumOf { it.endAt - it.startAt }
        require(sameMonth + duration <= 36L * HOUR_MS) { "本月加班预约不得超过36小时" }
    }

    private fun regularWorkMillis(p: LifeProfile, from: Long, to: Long): Long {
        val (work, excluded) = intervals(p, from, to)
        val edges = (listOf(from, to) + work.flatMap { listOf(it.start,it.end) } +
            excluded.flatMap { listOf(it.start,it.end) }).filter { it in from..to }.distinct().sorted()
        return edges.zipWithNext().sumOf { (a,b) ->
            if (work.any { a >= it.start && a < it.end } &&
                excluded.none { a >= it.start && a < it.end }) b-a else 0L
        }
    }

    /** Non-overlapping intervals ensure overtime cannot be counted as ordinary work too. */
    fun paidSlices(p: LifeProfile, from: Long, to: Long): List<PaidSlice> {
        require(from in 0L..MAX_TIME && to in from..MAX_TIME && to-from <= MAX_OFFLINE_MS)
        if (!p.enabled || to == from) return emptyList()
        val (works, excluded) = intervals(p, from, to)
        val overtime = p.overtime.filter { it.startAt < to && it.endAt > from }
        val edges = (listOf(from, to) +
            works.flatMap { listOf(it.start,it.end) } +
            excluded.flatMap { listOf(it.start,it.end) } +
            overtime.flatMap { listOf(it.startAt,it.endAt) })
            .filter { it in from..to }.distinct().sorted()
        return edges.zipWithNext().mapNotNull { (a,b) ->
            if (excluded.any { a >= it.start && a < it.end }) return@mapNotNull null
            val extra = overtime.firstOrNull { a >= it.startAt && a < it.endAt }
            when {
                extra != null -> PaidSlice(b-a, extra.ratePercent, extra.initiator)
                works.any { a >= it.start && a < it.end } -> PaidSlice(b-a, 100)
                else -> null
            }
        }
    }

    fun eligibleWorkMillis(p: LifeProfile, from: Long, to: Long): Long =
        paidSlices(p,from,to).sumOf { it.millis }

    fun accrueWages(p: LifeProfile, from: Long, to: Long): WageAccrual {
        val slices = paidSlices(p,from,to)
        val baseCents = effectiveMonthlyRmb(p).toLong()*100L
        val total = slices.sumOf { it.millis*it.ratePercent.toLong()*baseCents/100L } +
            p.salaryRemainder
        val overtimeNumerator = slices.filter { it.ratePercent > 100 }
            .sumOf { it.millis*it.ratePercent.toLong()*baseCents/100L }
        val voluntary = slices.filter { it.initiator == "voluntary" }
        val voluntaryNumerator = voluntary.sumOf { it.millis*it.ratePercent.toLong()*baseCents/100L }
        return WageAccrual(total/SALARY_DENOMINATOR, total%SALARY_DENOMINATOR,
            slices.sumOf { it.millis },
            slices.filter { it.ratePercent>100 }.sumOf { it.millis },
            overtimeNumerator/SALARY_DENOMINATOR,
            voluntaryNumerator/SALARY_DENOMINATOR, voluntary.sumOf { it.millis })
    }

    private fun naturalPhase(p: LifeProfile, now: Long): String {
        val zone = ZoneId.of(p.schedule.zoneId)
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun inInterval(start: Int, end: Int): Boolean = listOf(day.minusDays(1), day).any { date ->
            interval(date, start, end, zone)?.let { now in it.start until it.end } ?: false
        }
        val s = p.schedule
        if (inInterval(s.sleepStartMinute, s.sleepEndMinute)) return "sleep"
        if (inInterval(s.lunchStartMinute, s.lunchEndMinute)) return "lunch"
        if (inInterval(s.restStartMinute, s.restEndMinute)) return "rest"
        if (p.overtime.any { now in it.startAt until it.endAt }) return "overtime"
        val work = listOf(day.minusDays(1), day).any { date ->
            (if (s.zoneId == "Asia/Shanghai") ChinaWorkCalendar.isOrdinaryWorkday(date,s.workDays)
                else date.dayOfWeek.value in s.workDays) &&
            (interval(date, s.workStartMinute, s.workEndMinute, zone)?.let { now in it.start until it.end } ?: false) }
        return if (work) "work" else "free"
    }

    private fun replyPhase(p: LifeProfile, now: Long): String = when {
        !p.enabled -> "disabled"
        p.wakeUntil > now -> "wake"
        p.restUntil > now && p.restStartedAt <= now -> "rest"
        else -> naturalPhase(p, now)
    }

    fun availability(p: LifeProfile, now: Long): LifeAvailability {
        require(now in 0L..MAX_TIME)
        val phase = replyPhase(p, now)
        val busy = p.enabled && p.strict && phase in setOf("work", "overtime", "sleep", "lunch", "rest")
        val labels = mapOf("disabled" to "未启用生活日程", "wake" to "临时在线", "work" to "工作中", "overtime" to "加班中",
            "sleep" to "睡眠中", "lunch" to "午休中", "rest" to "休息中", "free" to "空闲")
        if (!busy) return LifeAvailability(labels.getValue(phase), false, null, phase)
        val s = p.schedule
        val zone = ZoneId.of(s.zoneId)
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val boundaries = mutableListOf<Long>()
        for (offset in -1L..8L) {
            val d = day.plusDays(offset)
            listOf(s.workStartMinute to s.workEndMinute, s.lunchStartMinute to s.lunchEndMinute,
                s.restStartMinute to s.restEndMinute, s.sleepStartMinute to s.sleepEndMinute).forEach { (start, end) ->
                interval(d, start, end, zone)?.let { boundaries += it.start; boundaries += it.end }
            }
        }
        p.overtime.forEach { boundaries += it.startAt; boundaries += it.endAt }
        boundaries += p.restUntil
        boundaries += p.wakeUntil
        val next = boundaries.filter { it > now }.distinct().sorted().firstOrNull { at ->
            replyPhase(p, at) !in setOf("work", "overtime", "sleep", "lunch", "rest")
        }
        return LifeAvailability(labels.getValue(phase), true, next, phase)
    }

    fun cursorAtLeast(aTime: Long, aId: Long, bTime: Long, bId: Long): Boolean =
        aTime > bTime || (aTime == bTime && aId >= bId)

    /** Preserve the same pending token when additional user events arrive while unavailable. */
    fun enqueue(p: LifeProfile, event: LifePending): LifeProfile {
        require(event.conversationId > 0 && event.messageCursor > 0 && event.messageId >= 0 &&
            event.purpose.length in 1..200 && event.createdAt in 1..MAX_TIME)
        val same = p.pending.firstOrNull { it.conversationId == event.conversationId && it.purpose == event.purpose }
        if (same != null) {
            if (cursorAtLeast(same.messageCursor, same.messageId, event.messageCursor, event.messageId)) return p
            return p.copy(pending = p.pending.map { if (it.id == same.id)
                it.copy(messageCursor = event.messageCursor, messageId = event.messageId,
                    attempts = 0, lastAttemptAt = 0L, nextAttemptAt = 0L) else it })
        }
        require(p.pending.size < 200) { "待回复会话过多，请先处理已有会话" }
        return p.copy(pending = p.pending + event)
    }

    fun completePending(p: LifeProfile, pendingId: String, cursor: Long, messageId: Long = 0): LifeProfile {
        val event = p.pending.singleOrNull { it.id == pendingId } ?: return p
        val completedTime = if (cursor == Long.MAX_VALUE) event.messageCursor else cursor
        val completedId = if (cursor == Long.MAX_VALUE) event.messageId else messageId
        val next = if (event.purpose == "reply") recordAnswer(p, event.conversationId, completedTime, completedId) else p
        return if (cursorAtLeast(completedTime, completedId, event.messageCursor, event.messageId))
            next.copy(pending = next.pending.filterNot { it.id == pendingId }) else next
    }

    fun recordAnswer(p: LifeProfile, conversationId: Long, cursor: Long, messageId: Long): LifeProfile {
        require(conversationId > 0 && cursor >= 0 && messageId >= 0)
        val oldTime = p.answeredCursors[conversationId] ?: 0L
        val oldId = p.answeredMessageIds[conversationId] ?: 0L
        if (cursorAtLeast(oldTime, oldId, cursor, messageId)) return p
        require(p.answeredCursors.size < 5000 || conversationId in p.answeredCursors) { "已处理会话记录达到上限" }
        return p.copy(answeredCursors = p.answeredCursors + (conversationId to cursor),
            answeredMessageIds = p.answeredMessageIds + (conversationId to messageId))
    }

    /** Learning time or pressing 'read' never unlocks a skill: this result must pass a source-based check. */
    fun checkedKnowledge(p: LifeProfile, book: LifeBook, lesson: LifeLesson, answerIndex: Int, now: Long,
                         summary: String, applicationAnswerIndex: Int?, model: String): LifeProfile {
        val reading = p.readings.singleOrNull { it.bookId == book.id }
            ?: throw IllegalArgumentException("请先开始阅读")
        require(lesson.id in reading.readLessonIds) { "请先阅读本节原文" }
        require(answerIndex in lesson.choices.indices) { "请选择一个答案" }
        require(summary.length in 32..4000 && model.length in 1..120) { "请提供角色真实学习摘要与模型记录" }
        if (answerIndex != lesson.correctIndex ||
            (lesson.transferCorrectIndex != null && applicationAnswerIndex != lesson.transferCorrectIndex) ||
            lesson.id in reading.passedLessonIds) return p
        val note = LifeKnowledge("${book.id}:${lesson.id}", book.id, lesson.id, lesson.title,
            lesson.excerpt, book.source, now, summary,
            if (lesson.transferCorrectIndex == null) "model_source_check" else "model_source_and_application", model, lesson.skillId)
        require(p.knowledge.size < 2000) { "已核对知识记录达到上限" }
        val oldSkill = p.skills.singleOrNull { it.id == lesson.skillId }
            ?: LifeSkill(lesson.skillId, lesson.skillTitle)
        return p.copy(knowledge = p.knowledge + note,
            skills = p.skills.filterNot { it.id == oldSkill.id } + oldSkill.copy(
                experiencePoints = oldSkill.experiencePoints + 1,
                verifiedKnowledgeCount = oldSkill.verifiedKnowledgeCount + 1),
            readings = p.readings.map { if (it.bookId == book.id)
                it.copy(lastReadAt = now, passedLessonIds = it.passedLessonIds + lesson.id) else it })
    }
}
