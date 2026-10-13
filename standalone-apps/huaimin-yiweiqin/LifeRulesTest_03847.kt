package com.cleo.cleos.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

/** Regression tests for money conservation, schedule boundaries and evidence-backed learning. */
class LifeRulesTest {
    private fun at(value: String): Long = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    private fun working(schedule: LifeSchedule = LifeSchedule()) = LifeProfile(7L, enabled = true, schedule = schedule)
    private fun reject(block: () -> Unit) {
        try { block(); fail("Expected invalid input rejection") } catch (_: IllegalArgumentException) { }
        catch (_: java.time.DateTimeException) { }
    }

    @Test fun defaultWeekdayCountsEightHoursAndExcludesLunch() {
        val p = working()
        assertEquals(8L * LifeRules.HOUR_MS, LifeRules.eligibleWorkMillis(p,
            at("2026-10-13T00:00:00+08:00[Asia/Shanghai]"), at("2026-10-14T00:00:00+08:00[Asia/Shanghai]")))
        assertEquals(0L, LifeRules.eligibleWorkMillis(p,
            at("2026-10-13T12:00:00+08:00[Asia/Shanghai]"), at("2026-10-13T13:00:00+08:00[Asia/Shanghai]")))
    }

    @Test fun weekendAndDisabledCharactersEarnNothing() {
        val a = at("2026-10-17T09:00:00+08:00[Asia/Shanghai]")
        val b = at("2026-10-17T18:00:00+08:00[Asia/Shanghai]")
        assertEquals(0L, LifeRules.eligibleWorkMillis(working(), a, b))
        assertEquals(0L, LifeRules.eligibleWorkMillis(working().copy(enabled = false), a, b))
        assertFalse(LifeRules.availability(LifeProfile(7), a).isBusy)
    }

    @Test fun overnightShiftBelongsToItsStartingWorkday() {
        val s = LifeSchedule(workDays = listOf(5), workStartMinute = 1320, workEndMinute = 120,
            lunchStartMinute = 0, lunchEndMinute = 0, restStartMinute = 0, restEndMinute = 0,
            sleepStartMinute = 180, sleepEndMinute = 660)
        val p = working(s)
        val begin = at("2026-10-16T22:00:00+08:00[Asia/Shanghai]")
        val end = at("2026-10-17T02:00:00+08:00[Asia/Shanghai]")
        assertEquals(4L * LifeRules.HOUR_MS, LifeRules.eligibleWorkMillis(p, begin, end))
        val busy = LifeRules.availability(p, at("2026-10-17T01:00:00+08:00[Asia/Shanghai]"))
        assertTrue(busy.isBusy)
        assertEquals(end, busy.busyUntil)
        assertEquals("free", LifeRules.availability(p, end).phase)
    }

    @Test fun timezoneDstUsesActualElapsedHoursRatherThanWallClockHours() {
        val s = LifeSchedule(zoneId = "America/New_York", workDays = listOf(6), workStartMinute = 1320,
            workEndMinute = 480, lunchStartMinute = 0, lunchEndMinute = 0,
            restStartMinute = 0, restEndMinute = 0, sleepStartMinute = 0, sleepEndMinute = 0)
        assertEquals(9L * LifeRules.HOUR_MS, LifeRules.eligibleWorkMillis(working(s),
            at("2026-03-07T22:00:00-05:00[America/New_York]"),
            at("2026-03-08T08:00:00-04:00[America/New_York]")))
    }

    @Test fun scheduleValidationRejectsInvalidZoneDaysAndDayLongWork() {
        reject { LifeRules.validateSchedule(LifeSchedule(zoneId = "invalid_zone")) }
        reject { LifeRules.validateSchedule(LifeSchedule(workDays = listOf(1, 1))) }
        reject { LifeRules.validateSchedule(LifeSchedule(workDays = emptyList())) }
        reject { LifeRules.validateSchedule(LifeSchedule(workStartMinute = 1, workEndMinute = 0)) }
    }

    @Test fun actualLunchStillBlocksUntilEndOfWorkInsteadOfSuggestingItWillReplyAtThirteen() {
        val now = at("2026-10-13T12:30:00+08:00[Asia/Shanghai]")
        val a = LifeRules.availability(working(), now)
        assertEquals("lunch", a.phase)
        assertTrue(a.isBusy)
        assertEquals(at("2026-10-13T18:00:00+08:00[Asia/Shanghai]"), a.busyUntil)
    }

    @Test fun looseScheduleRetainsHonestActivityLabelWithoutBlocking() {
        val a = LifeRules.availability(working().copy(strict = false),
            at("2026-10-13T10:00:00+08:00[Asia/Shanghai]"))
        assertEquals("work", a.phase)
        assertFalse(a.isBusy)
        assertNull(a.busyUntil)
    }

    @Test fun manualWakeDoesNotCreateIncomeAndManualRestExcludesWork() {
        val a = at("2026-10-13T10:00:00+08:00[Asia/Shanghai]")
        val b = a + LifeRules.HOUR_MS
        val p = working().copy(restStartedAt = a, restUntil = b, wakeUntil = b)
        assertFalse(LifeRules.availability(p, a + 1).isBusy)
        assertEquals("wake", LifeRules.availability(p, a + 1).phase)
        assertEquals(0L, LifeRules.eligibleWorkMillis(p, a, b))
        val sleep = at("2026-10-13T23:00:00+08:00[Asia/Shanghai]")
        assertEquals(0L, LifeRules.eligibleWorkMillis(working().copy(wakeUntil = sleep + LifeRules.HOUR_MS),
            sleep, sleep + LifeRules.HOUR_MS))
    }

    @Test fun overlappingBreaksAreSubtractedOnce() {
        val a = at("2026-10-13T11:00:00+08:00[Asia/Shanghai]")
        val b = at("2026-10-13T14:00:00+08:00[Asia/Shanghai]")
        val p = working().copy(restStartedAt = at("2026-10-13T12:30:00+08:00[Asia/Shanghai]"),
            restUntil = at("2026-10-13T13:30:00+08:00[Asia/Shanghai]"))
        assertEquals(90L * 60_000L, LifeRules.eligibleWorkMillis(p, a, b))
    }

    @Test fun infiniteOfflineSpanIsRejectedAndCapIsSeventyTwoHours() {
        reject { LifeRules.eligibleWorkMillis(working(), 1L, LifeRules.MAX_OFFLINE_MS + 2L) }
        reject { LifeRules.salaryFor(working(), LifeRules.MAX_OFFLINE_MS + 1) }
        assertEquals(72L * LifeRules.HOUR_MS, LifeRules.MAX_OFFLINE_MS)
    }

    @Test fun salaryConversionUsesTenRmbPerStarcoinWithRemainderConserved() {
        val p = working()
        val expected = LifeRules.salaryFor(p, 8L * LifeRules.HOUR_MS)
        var partial = p
        var sum = 0L
        repeat(8 * 60) {
            val result = LifeRules.salaryFor(partial, 60_000L)
            sum += result.first
            partial = partial.copy(salaryRemainder = result.second)
        }
        assertEquals(expected.first, sum)
        assertEquals(expected.second, partial.salaryRemainder)
        assertEquals(4137L, sum) // 900 RMB-worth starcoins per 174 hours, 8h = 41.37 stars plus remainder.
    }

    @Test fun raisesAreBoundedAndSwitchingProfessionsDoesNotTransferAllSeniority() {
        val p = working().copy(professionWorkedMillis = mapOf("engineer" to 10_000L * LifeRules.HOUR_MS))
        assertEquals(10800, LifeRules.effectiveMonthlyRmb(p))
        assertEquals(15000, LifeRules.effectiveMonthlyRmb(p.copy(professionId = "software")))
    }

    @Test fun unverifiedOrIrrelevantBookQuantityCannotRaiseWages() {
        val notes = (1..60).map { LifeKnowledge("$it", "book", "$it", "片段", "正文", "用户导入", 1,
            "这是一份用于核对原文但不能证明专业能力的摘要记录。", "model_source_check", "test", "source_excerpt_check") }
        assertEquals(9000, LifeRules.effectiveMonthlyRmb(working().copy(knowledge = notes)))
        assertEquals(9000, LifeRules.effectiveMonthlyRmb(working().copy(knowledge = notes.map {
            it.copy(method = "model_source_and_application", skillId = "unrelated") })))
        assertEquals(9090, LifeRules.effectiveMonthlyRmb(working().copy(knowledge = notes.take(3).map {
            it.copy(method = "model_source_and_application", skillId = "engineering_diagnosis") })))
    }

    @Test fun salaryReceiptReplayAfterJsonRoundTripDoesNotIssueMoneyTwice() {
        val first = VirtualWalletStore.salaryBook(WalletBook(), 7L, 1234L, "life:device:7:1", 1L)
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString<WalletBook>(json.encodeToString(first))
        val replayed = VirtualWalletStore.salaryBook(restored, 7L, 1234L, "life:device:7:1", 2L)
        assertSame(restored, replayed)
        assertEquals(1234L, replayed.balances[7L])
        assertEquals(1234L, replayed.totalIssuedCents)
        assertEquals(1, replayed.salaryReceipts.size)
        assertFalse(replayed.initialized) // starter grant can still be claimed separately.
    }

    @Test fun sameSalaryIdWithDifferentAmountOrRecipientIsRejected() {
        val book = VirtualWalletStore.salaryBook(WalletBook(), 7L, 1234L, "salary-id", 1L)
        reject { VirtualWalletStore.salaryBook(book, 7L, 1235L, "salary-id", 2L) }
        reject { VirtualWalletStore.salaryBook(book, 8L, 1234L, "salary-id", 2L) }
        reject { VirtualWalletStore.salaryBook(book, 0L, 1L, "new-id", 2L) }
    }

    @Test fun salaryKeepsExistingBalancesEscrowAndMovesTotalSupplyByExactDelta() {
        val packet = WalletPacket(sender = 0, recipients = listOf(7), shares = listOf(3000), random = false)
        val old = WalletBook(initialized = true, balances = mapOf(0L to 97000L), packets = listOf(packet))
        val next = VirtualWalletStore.salaryBook(old, 7, 1234, "salary-id", 1)
        assertEquals(97000L, next.balances[0])
        assertEquals(old.packets, next.packets)
        assertEquals(101234L, next.totalIssuedCents)
        assertEquals(next.totalIssuedCents, next.balances.values.sum() + next.packets.sumOf { it.remaining })
        assertEquals("salary", next.movements.last().kind)
    }

    @Test fun receiptProtectionSurvivesTruncationOfVisibleMovementHistory() {
        val first = VirtualWalletStore.salaryBook(WalletBook(), 7, 1234, "salary-id", 1)
        val clearedHistory = first.copy(movements = emptyList())
        assertSame(clearedHistory, VirtualWalletStore.salaryBook(clearedHistory, 7, 1234, "salary-id", 2))
    }

    @Test fun restoringPairedOldSnapshotReplaysUnpaidSalaryExactlyOnce() {
        // Backup occurred after Life's durable outbox S but before wallet credit S.
        val backupWallet = WalletBook()
        val afterBackup = VirtualWalletStore.salaryBook(backupWallet, 7, 100, "outbox-S", 1)
        assertEquals(100L, afterBackup.balances[7])
        // Strict restore restores BOTH the old balance and its corresponding old receipt set.
        val restored = backupWallet
        val paid = VirtualWalletStore.salaryBook(restored, 7, 100, "outbox-S", 2)
        assertEquals(100L, paid.balances[7])
        assertSame(paid, VirtualWalletStore.salaryBook(paid, 7, 100, "outbox-S", 3))
    }

    @Test fun pendingConversationCoalescesAndCompletionCannotDropNewerEvent() {
        val one = LifePending("token", 12L, 100L, createdAt = 1L, messageId = 1)
        var p = LifeRules.enqueue(working(), one)
        p = LifeRules.enqueue(p, one.copy(id = "unused", messageCursor = 200L, messageId = 2))
        assertEquals(1, p.pending.size)
        assertEquals("token", p.pending.single().id)
        val unfinished = LifeRules.completePending(p, "token", 100L, 1)
        assertEquals(1, unfinished.pending.size)
        assertEquals(100L, unfinished.answeredCursors[12L])
        val complete = LifeRules.completePending(unfinished, "token", 200L, 2)
        assertTrue(complete.pending.isEmpty())
        assertEquals(200L, complete.answeredCursors[12L])
        assertEquals(2L, complete.answeredMessageIds[12L])
    }

    @Test fun sameTimestampNewMessageResetsRetryAndKeepsPendingToken() {
        val event = LifePending("token", 12L, 100L, createdAt = 1L, messageId = 1,
            attempts = 3, lastAttemptAt = 50, nextAttemptAt = 99999)
        val p = LifeRules.enqueue(working().copy(pending = listOf(event)),
            event.copy(id = "new", messageId = 2))
        assertEquals("token", p.pending.single().id)
        assertEquals(0, p.pending.single().attempts)
        assertEquals(0L, p.pending.single().nextAttemptAt)
    }

    @Test fun completingGoalDoesNotPretendOrdinaryUserMessageWasAnswered() {
        val event = LifePending("goal-token", 12L, 500L, purpose = "goal:abc:1", createdAt = 1L)
        val complete = LifeRules.completePending(working().copy(pending = listOf(event)), "goal-token", 500L)
        assertTrue(complete.pending.isEmpty())
        assertTrue(complete.answeredCursors.isEmpty())
    }

    @Test fun defaultCompletionDoesNotPersistInfiniteAnsweredCursor() {
        val event = LifePending("token", 12, 100, createdAt = 1, messageId = 2)
        val complete = LifeRules.completePending(working().copy(pending = listOf(event)), "token", Long.MAX_VALUE)
        assertEquals(100L, complete.answeredCursors[12])
        assertEquals(2L, complete.answeredMessageIds[12])
    }

    @Test fun builtinLessonsAreAnchoredAndHaveSeparateApplicationChecks() {
        val book = LifeStore.builtInBook(1L)
        assertEquals(3, book.lessons.size)
        book.lessons.forEach { lesson ->
            assertEquals(lesson.excerpt, book.content.substring(lesson.startOffset, lesson.endOffset))
            assertNotNull(lesson.transferCorrectIndex)
            assertTrue(lesson.correctIndex in lesson.choices.indices)
            assertTrue(lesson.transferCorrectIndex!! in lesson.transferChoices.indices)
        }
    }

    @Test fun readingTimeAndWrongApplicationAnswerNeverUnlockKnowledge() {
        val book = LifeStore.builtInBook(1)
        val lesson = book.lessons.first()
        val reading = LifeReading(book.id, 1, 2, readLessonIds = listOf(lesson.id), readingMillis = 86_400_000)
        val p = working().copy(readings = listOf(reading))
        assertTrue(p.knowledge.isEmpty())
        val failed = LifeRules.checkedKnowledge(p, book, lesson, lesson.correctIndex, 2L,
            "必须先记录发生条件和现象，提出可检验的假设，并在原触发条件下复测保存日志。", 0, "test-model")
        assertTrue(failed.knowledge.isEmpty())
        assertTrue(failed.skills.isEmpty())
        assertTrue(failed.readings.single().passedLessonIds.isEmpty())
    }

    @Test fun actualSourceAndApplicationChecksUnlockOnlyThisLessonOnce() {
        val book = LifeStore.builtInBook(1)
        val lesson = book.lessons.first()
        val p = working().copy(readings = listOf(LifeReading(book.id, 1, 2, readLessonIds = listOf(lesson.id))))
        val summary = "必须先记录发生条件和现象，提出可检验的假设，并在原触发条件下复测保存日志。"
        val learned = LifeRules.checkedKnowledge(p, book, lesson, lesson.correctIndex, 2,
            summary, lesson.transferCorrectIndex, "test-model")
        assertEquals(1, learned.knowledge.size)
        assertEquals(lesson.excerpt, learned.knowledge.single().excerpt)
        assertEquals("model_source_and_application", learned.knowledge.single().method)
        assertEquals(1, learned.skills.single().verifiedKnowledgeCount)
        val replay = LifeRules.checkedKnowledge(learned, book, lesson, lesson.correctIndex, 3,
            summary, lesson.transferCorrectIndex, "test-model")
        assertEquals(learned, replay)
    }

    @Test fun aFilenameOrEmptyModelSummaryCannotUnlockAnySkill() {
        val book = LifeStore.builtInBook(1)
        val lesson = book.lessons.first()
        val p = working().copy(readings = listOf(LifeReading(book.id, 1, 2, readLessonIds = listOf(lesson.id))))
        reject { LifeRules.checkedKnowledge(p, book, lesson, lesson.correctIndex, 2,
            "book.txt", lesson.transferCorrectIndex, "test-model") }
        reject { LifeRules.checkedKnowledge(p, book, lesson, lesson.correctIndex, 2,
            "必须先记录发生条件和现象，提出可检验的假设，并在原触发条件下复测保存日志。", lesson.transferCorrectIndex, "") }
    }
}
