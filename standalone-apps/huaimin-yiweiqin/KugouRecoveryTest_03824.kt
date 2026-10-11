package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

/** Regression for the user's recording: results exist, but 综合 never advances
 * to 单曲. These are semantic snapshots, not a claim of device verification. */
class KugouRecoveryTest {
    private val pkg = "com.kugou.android"
    private val song = SongRequest.of("小半", "陈粒")

    private fun searched() = KugouSearchPlan(song, pkg).apply {
        applied(QqUiAction.Fill(30, song.query), true)
        applied(QqUiAction.Submit(30), true)
    }

    private fun tabs(comprehensiveSelected: Boolean, songsSelected: Boolean) = listOf(
        QqUiNode(1, text = "综合", selected = comprehensiveSelected,
            centerX = 45, centerY = 320, heightDp = 40),
        QqUiNode(2, text = "单曲", selected = songsSelected,
            centerX = 220, centerY = 320, heightDp = 40),
        QqUiNode(3, text = "视频", centerX = 415, centerY = 320, heightDp = 40),
    )

    private fun row(
        artist: String = "陈粒",
        clickableRow: Boolean = true,
        titleDescription: String = "",
    ) = listOf(
        QqUiNode(100, clickable = clickableRow, heightDp = 86),
        QqUiNode(101, parent = 100, text = "小半", description = titleDescription,
            centerX = 195, centerY = 850, heightDp = 33),
        QqUiNode(102, parent = 100, text = artist, clickable = true,
            centerX = 180, centerY = 892, heightDp = 24),
    )

    private fun artistCard() = listOf(
        QqUiNode(5, text = "关注", clickable = true),
        QqUiNode(6, text = "歌曲 419"),
        QqUiNode(7, text = "专辑 77"),
    )

    @Test fun acceptedTabClickDoesNotProveSingleTrackPageAndCanRecover() {
        val plan = searched()
        val stuck = tabs(true, false) + artistCard() + row()
        val first = plan.next(pkg, stuck)
        assertTrue(first is QqUiAction.Click && first.purpose == "songs")
        plan.applied(first, true)
        assertEquals(QqUiAction.Wait, plan.next(pkg, stuck))
        assertFalse(plan.picked)

        var recovery: QqUiAction.Click? = null
        for (frame in 0 until 8) {
            val action = plan.next(pkg, stuck)
            assertFalse("A stuck 综合 page must never pick a song",
                action is QqUiAction.Click && action.purpose == "song")
            if (action is QqUiAction.Click) {
                assertEquals("songs", action.purpose)
                recovery = action
                break
            }
        }
        assertNotNull("An accepted but ineffective click must be retried", recovery)
        assertTrue("Recovery must use the verified tab gesture", recovery!!.preferGesture)
        assertEquals(2, recovery!!.gestureId ?: recovery!!.id)
        assertFalse(plan.picked)
    }

    @Test fun ineffectiveTabRetriesEventuallyStopWithoutPicking() {
        val plan = searched()
        val stuck = tabs(true, false) + artistCard() + row()
        var stopped = false
        var clicks = 0
        for (frame in 0 until 100) {
            val action = plan.next(pkg, stuck)
            if (action is QqUiAction.Stop) {
                stopped = true
                break
            }
            if (action is QqUiAction.Click) {
                assertEquals("songs", action.purpose)
                clicks++
                plan.applied(action, true)
            }
        }
        assertTrue("Repeated ineffective tab actions must be bounded", stopped)
        assertTrue(clicks in 2..8)
        assertFalse(plan.picked)
    }

    @Test fun unreportedSelectionRequiresTwoConsecutiveResultSnapshots() {
        val plan = searched()
        val comprehensive = tabs(true, false) + artistCard() + row()
        val tabAction = plan.next(pkg, comprehensive)
        plan.applied(tabAction, true)
        val singleWithoutSelectedFlag = tabs(false, false) + row()

        val first = plan.next(pkg, singleWithoutSelectedFlag)
        assertFalse("One changed snapshot is not enough",
            first is QqUiAction.Click && first.purpose == "song")
        val second = plan.next(pkg, singleWithoutSelectedFlag)
        assertTrue("Stable single-track results should be actionable",
            second is QqUiAction.Click && second.purpose == "song")
        assertFalse(plan.picked)
    }

    @Test fun artistCardAndSelectedComprehensiveOverrideSelectedSongFlag() {
        val plan = searched()
        val action = plan.next(pkg, tabs(true, true) + artistCard() + row())
        assertFalse(action is QqUiAction.Click && action.purpose == "song")
        assertFalse(plan.picked)
    }

    @Test fun exactTitleWithArtistDescriptionUsesClickableSongRow() {
        val action = searched().next(pkg,
            tabs(false, true) + row(titleDescription = "小半，陈粒，原唱"))
        assertTrue(action is QqUiAction.Click)
        action as QqUiAction.Click
        assertEquals("song", action.purpose)
        assertEquals("The small song row should keep semantic ACTION_CLICK", 100, action.id)
        assertEquals("Gesture fallback must stay on the exact title", 101, action.gestureId)
        assertFalse(action.preferGesture)
    }

    @Test fun verifiedClickableSongRowMayDescribeTitleAndArtist() {
        val describedRow = row().map {
            if (it.id == 100) it.copy(text = "小半", description = "陈粒") else it
        }
        val action = searched().next(pkg, tabs(false, true) + describedRow)
        assertTrue(action is QqUiAction.Click)
        action as QqUiAction.Click
        assertEquals("song", action.purpose)
        assertEquals("A verified song row is not a distinct singer link", 100, action.id)
        assertEquals(101, action.gestureId)
    }

    @Test fun structuralConfirmationResetsWhenComprehensivePageReturns() {
        val plan = searched()
        val comprehensive = tabs(true, false) + artistCard() + row()
        plan.applied(plan.next(pkg, comprehensive), true)
        val changed = tabs(false, false) + row()
        assertEquals(QqUiAction.Wait, plan.next(pkg, changed))
        val reappeared = plan.next(pkg, comprehensive)
        assertFalse(reappeared is QqUiAction.Click && reappeared.purpose == "song")
        val changedAgain = plan.next(pkg, changed)
        assertFalse("Interrupted snapshots must not accumulate confirmation",
            changedAgain is QqUiAction.Click && changedAgain.purpose == "song")
        val stableAgain = plan.next(pkg, changed)
        assertTrue(stableAgain is QqUiAction.Click && stableAgain.purpose == "song")
    }

    @Test fun titleGestureIsUsedWhenOnlyArtistLabelIsClickable() {
        val action = searched().next(pkg,
            tabs(false, true) + row(clickableRow = false, titleDescription = "陈粒"))
        assertTrue(action is QqUiAction.Click)
        action as QqUiAction.Click
        assertEquals("song", action.purpose)
        assertEquals(101, action.gestureId ?: action.id)
        assertNotEquals("The singer's link must not be a playback target", 102, action.id)
    }

    @Test fun wrongArtistAndMvRowsNeverBecomeSongTargets() {
        val wrongArtist = searched().next(pkg, tabs(false, true) + row(artist = "其他歌手"))
        assertEquals(QqUiAction.Wait, wrongArtist)
        val mv = searched().next(pkg,
            tabs(false, true) + row() + QqUiNode(103, parent = 100, text = "MV"))
        assertEquals(QqUiAction.Wait, mv)
    }

    @Test fun failedSongActionNeverCompletesPlan() {
        val plan = searched()
        val results = tabs(false, true) + row()
        val action = plan.next(pkg, results)
        assertTrue(action is QqUiAction.Click && action.purpose == "song")
        plan.applied(action, false)
        assertFalse(plan.picked)
        val again = plan.next(pkg, results)
        assertTrue(again is QqUiAction.Click && again.purpose == "song")
        plan.applied(again, true)
        assertTrue(plan.picked)
    }

    @Test fun alreadyLoadedResultsRecoverWithoutSuccessfulSubmitCallback() {
        val plan = KugouSearchPlan(song, pkg)
        val editor = QqUiNode(30, text = "小半", editable = true,
            viewId = "kugou:id/search", hint = "搜索歌曲")
        val existingResults = listOf(editor) + tabs(true, false) + artistCard() + row()
        val action = plan.next(pkg, existingResults)
        assertTrue("Observed requested results must not be refilled or submitted again",
            action is QqUiAction.Click && action.purpose == "songs")
        assertTrue("A recognized results screen may dismiss its observed IME",
            plan.canDismissKeyboard(existingResults))
        assertFalse(plan.picked)
        plan.applied(action, true)
        val result = plan.next(pkg, listOf(editor) + tabs(false, true) + row())
        assertTrue(result is QqUiAction.Click && result.purpose == "song")
    }

    @Test fun unconfirmedSongClickRetriesExactTitleGestureThenStops() {
        val plan = searched()
        val results = tabs(false, true) + row()
        val first = plan.next(pkg, results)
        assertTrue(first is QqUiAction.Click && first.purpose == "song")
        assertFalse((first as QqUiAction.Click).preferGesture)
        plan.applied(first, true)
        assertTrue(plan.picked)

        repeat(2) {
            plan.retryUnconfirmedSong()
            assertFalse(plan.picked)
            val retry = plan.next(pkg, results)
            assertTrue(retry is QqUiAction.Click)
            retry as QqUiAction.Click
            assertEquals("song", retry.purpose)
            assertTrue("Unconfirmed semantic clicks must recover with a title gesture", retry.preferGesture)
            assertEquals(101, retry.gestureId ?: retry.id)
            plan.applied(retry, true)
            assertTrue(plan.picked)
        }
        plan.retryUnconfirmedSong()
        assertTrue("Unconfirmed playback must stop after three accepted selections",
            plan.next(pkg, results) is QqUiAction.Stop)
        assertFalse(plan.picked)
    }

    @Test fun qqClickConstructorKeepsDefaultSemantics() {
        val click = QqUiAction.Click(7, "song")
        assertNull(click.gestureId)
        assertFalse(click.preferGesture)
    }

    @Test fun existingSearchHeaderParentRemainsClickable() {
        val nodes = listOf(
            QqUiNode(0, heightDp = 900),
            QqUiNode(10, parent = 0, clickable = true, heightDp = 180),
            QqUiNode(11, parent = 10, text = "搜索", heightDp = 40),
        )
        assertEquals(QqUiAction.Click(10, "search"),
            KugouSearchPlan(song, pkg).next(pkg, nodes))
    }
}
