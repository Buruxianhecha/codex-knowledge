package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

/** Regression from the user's 36-second Kugou screen recording: search returned
 * a 综合 singer card above the correct song, but automation entered singer home.
 */
class KugouResultSafetyTest {
    private val pkg = "com.kugou.android"
    private val song = SongRequest.of("小半", "陈粒")
    private fun searched() = KugouSearchPlan(song, pkg).apply {
        applied(QqUiAction.Fill(20, song.query), true)
        applied(QqUiAction.Submit(20), true)
    }
    private fun songRow(artist: String = "陈粒") = listOf(
        QqUiNode(100, clickable = true, heightDp = 86),
        QqUiNode(101, parent = 100, text = "小半"),
        QqUiNode(102, parent = 100, text = artist),
    )
    private fun tabs(comprehensive: Boolean) = listOf(
        QqUiNode(1, text = "综合", selected = comprehensive, heightDp = 55),
        QqUiNode(2, text = "单曲", clickable = true, selected = !comprehensive, heightDp = 55),
        QqUiNode(3, text = "视频", heightDp = 55),
    )
    private val artistCard = listOf(
        QqUiNode(4, text = "陈粒", clickable = true, heightDp = 90),
        QqUiNode(5, text = "关注", clickable = true, heightDp = 44),
        QqUiNode(6, text = "歌曲 419", heightDp = 44),
        QqUiNode(7, text = "专辑 77", heightDp = 44),
    )

    @Test fun comprehensiveResultsMustSwitchToSingleTrackTabFirst() {
        val plan = searched()
        assertEquals(QqUiAction.Click(2, "songs"),
            plan.next(pkg, tabs(true) + artistCard + songRow()))
        plan.applied(QqUiAction.Click(2, "songs"), true)
        // Screen has not switched yet: singer card still present.
        assertEquals(QqUiAction.Wait, plan.next(pkg, tabs(true) + artistCard + songRow()))
        assertEquals(QqUiAction.Click(100, "song"), plan.next(pkg, tabs(false) + songRow()))
    }

    @Test fun artistProfileMustNeverBeChosenAsPlayback() {
        val plan = searched()
        val home = listOf(
            QqUiNode(1, text = "陈粒", clickable = true),
            QqUiNode(2, text = "入驻艺人"),
            QqUiNode(3, text = "粉丝164.6万"),
            QqUiNode(4, text = "关注"),
            QqUiNode(5, text = "播放全部"),
            QqUiNode(6, text = "小半", clickable = true),
        )
        assertTrue(plan.next(pkg, home) is QqUiAction.Stop)
        assertFalse(plan.picked)
    }

    @Test fun songTitleWithoutSameRowArtistIsNotEnough() {
        val plan = searched()
        plan.applied(QqUiAction.Click(2, "songs"), true)
        assertEquals(QqUiAction.Wait, plan.next(pkg, tabs(false) + songRow("其他歌手")))
    }

    @Test fun noClickableSingleTabNeverFallsBackToArtistCard() {
        val plan = searched()
        val navigation = listOf(
            QqUiNode(1, text = "综合"),
            QqUiNode(2, text = "单曲", clickable = false),
            QqUiNode(3, text = "视频"),
        )
        assertEquals(QqUiAction.Wait, plan.next(pkg, navigation + artistCard + songRow()))
    }

    @Test fun clickingSongMarksCompleteButClickingArtistNeverDoes() {
        val plan = searched()
        plan.applied(QqUiAction.Click(2, "songs"), true)
        val action = plan.next(pkg, tabs(false) + songRow())
        assertEquals(QqUiAction.Click(100, "song"), action)
        assertFalse(plan.picked)
        plan.applied(action, true)
        assertTrue(plan.picked)
        assertEquals(QqUiAction.Wait, plan.next(pkg, tabs(false) + songRow()))
    }
}
