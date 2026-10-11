package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

/** Reproduces the new 34-second recording: Kugou displays song results
 * but accessibility does not mark tab/title labels as clickable.
 */
class KugouGestureFallbackTest {
    private val pkg = "com.kugou.android"
    private val song = SongRequest.of("小半", "陈粒")

    private fun ready(): KugouSearchPlan = KugouSearchPlan(song, pkg).apply {
        applied(QqUiAction.Fill(30, song.query), true)
        applied(QqUiAction.Submit(30), true)
    }

    private fun tabs(selectedSong: Boolean) = listOf(
        QqUiNode(1, text = "综合", clickable = false,
            centerX = 45, centerY = 320, selected = !selectedSong, heightDp = 40),
        QqUiNode(2, text = "单曲", clickable = false,
            centerX = 220, centerY = 320, selected = selectedSong, heightDp = 40),
        QqUiNode(3, text = "视频", clickable = false,
            centerX = 415, centerY = 320, heightDp = 40),
    )
    private fun row() = listOf(
        QqUiNode(100, heightDp = 78),
        QqUiNode(101, parent = 100, text = "小半", clickable = false,
            centerX = 195, centerY = 850, heightDp = 33),
        QqUiNode(102, parent = 100, text = "陈粒", clickable = true,
            centerX = 180, centerY = 892, heightDp = 24),
    )
    private fun artistCard() = listOf(
        QqUiNode(5, text = "关注", clickable = true),
        QqUiNode(6, text = "歌曲 419"),
        QqUiNode(7, text = "专辑 77"),
    )

    @Test fun unclickableButLocatedSongTabIsTapped() {
        val plan = ready()
        assertEquals(QqUiAction.Click(2, "songs"),
            plan.next(pkg, tabs(false) + artistCard() + row()))
        plan.applied(QqUiAction.Click(2, "songs"), true)
        assertEquals(QqUiAction.Wait,
            plan.next(pkg, tabs(false) + artistCard() + row()))
        assertEquals(QqUiAction.Click(101, "song"),
            plan.next(pkg, tabs(true) + row()))
    }

    @Test fun duplicateSingleTitleAtSectionHeadingCannotReplaceNavigation() {
        val plan = ready()
        val heading = QqUiNode(25, text = "单曲", clickable = true,
            centerX = 52, centerY = 1030, heightDp = 48)
        assertEquals(QqUiAction.Click(2, "songs"),
            plan.next(pkg, tabs(false) + listOf(heading) + artistCard() + row()))
    }

    @Test fun displacedSingleLabelIsNotTrustedAsTab() {
        val plan = ready()
        val fake = listOf(
            QqUiNode(1, text = "综合", centerX = 45, centerY = 320),
            QqUiNode(2, text = "单曲", centerX = 250, centerY = 1080),
            QqUiNode(3, text = "视频", centerX = 415, centerY = 320),
        )
        assertEquals(QqUiAction.Wait, plan.next(pkg, fake + artistCard() + row()))
    }

    @Test fun otherArtistNeverChosenEvenWhenTitleGeometryExists() {
        val plan = ready()
        plan.applied(QqUiAction.Click(2, "songs"), true)
        val wrong = row().map {
            if (it.id == 102) it.copy(text = "别人唱的") else it
        }
        assertEquals(QqUiAction.Wait, plan.next(pkg, tabs(true) + wrong))
    }
}
