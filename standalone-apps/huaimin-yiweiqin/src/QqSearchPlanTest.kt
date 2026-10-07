package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class QqSearchPlanTest {
    private val pkg = "com.tencent.qqmusic"
    private val song = SongRequest.of("灰", "h3r3")
    private fun field(text: String = "") = QqUiNode(1, text = text, viewId = "qq:id/search_input", editable = true)
    private fun ready(): QqSearchPlan = QqSearchPlan(song, pkg).apply {
        applied(QqUiAction.Fill(1, song.query), true)
        applied(QqUiAction.Submit(1), true)
    }
    private fun rows(artist: String = "h3R3", height: Int = 80) = listOf(
        QqUiNode(0, clickable = false, heightDp = 900), field(song.query),
        QqUiNode(2, parent = 0, clickable = true, heightDp = height),
        QqUiNode(3, parent = 2, text = "灰"), QqUiNode(4, parent = 2, text = artist),
    )
    @Test fun homeSearchButtonOpensTheSearchPage() {
        val plan = QqSearchPlan(song, pkg)
        val action = plan.next(pkg, listOf(QqUiNode(1, description = "搜索", clickable = true)))
        assertEquals(QqUiAction.Click(1, "search"), action)
        plan.applied(action, true)
        assertEquals(QqUiAction.Wait, plan.next(pkg, listOf(QqUiNode(1, description = "搜索", clickable = true))))
    }
    @Test fun accessibleSearchBarDescriptionCanOpenSearchWithoutAStableViewId() {
        assertEquals(QqUiAction.Click(7, "search"), QqSearchPlan(song, pkg).next(pkg,
            listOf(QqUiNode(7, description = "搜索歌曲、歌手、视频", viewId = "qq:id/a1", clickable = true))))
    }
    @Test fun searchEditorIsFilledWithArtistAndTitle() {
        assertEquals(QqUiAction.Fill(1, "h3r3 灰"), QqSearchPlan(song, pkg).next(pkg, listOf(field())))
    }
    @Test fun textIsNeverSentIntoOneOfSeveralUnidentifiedEditors() {
        val fields = listOf(field().copy(viewId = "name"), field().copy(id = 2, viewId = "phone"))
        assertEquals(QqUiAction.Wait, QqSearchPlan(song, pkg).next(pkg, fields))
    }
    @Test fun loneUnidentifiedLoginEditorIsNeverFilledAsSearch() {
        assertEquals(QqUiAction.Wait, QqSearchPlan(song, pkg).next(pkg, listOf(field().copy(viewId = "login_account"))))
        assertEquals(QqUiAction.Fill(1, song.query), QqSearchPlan(song, pkg).next(pkg, listOf(field().copy(viewId = "input", hint = "搜索歌曲"))))
    }
    @Test fun filledQueryUsesSearchButtonBeforeClickingAnySong() {
        val plan = QqSearchPlan(song, pkg).apply { applied(QqUiAction.Fill(1, song.query), true) }
        val button = QqUiNode(5, text = "搜索", clickable = true)
        assertEquals(QqUiAction.Click(5, "submit"), plan.next(pkg, rows() + button))
    }
    @Test fun withoutSearchButtonTheFocusedEditorCanSendItsImeAction() {
        val plan = QqSearchPlan(song, pkg).apply { applied(QqUiAction.Fill(1, song.query), true) }
        assertEquals(QqUiAction.Submit(1), plan.next(pkg, listOf(field(song.query))))
    }
    @Test fun failedFillDoesNotAdvanceToSearchOrResults() {
        val plan = QqSearchPlan(song, pkg)
        val action = plan.next(pkg, listOf(field()))
        plan.applied(action, false)
        assertEquals(action, plan.next(pkg, listOf(field())))
    }
    @Test fun changedOrStaleEditorMustBeRefilledBeforeSubmission() {
        val plan = QqSearchPlan(song, pkg).apply { applied(QqUiAction.Fill(1, song.query), true) }
        assertEquals(QqUiAction.Fill(1, song.query), plan.next(pkg, listOf(field("周杰伦 晴天"))))
    }
    @Test fun exactSongAndArtistInTheSameRowCanBeClicked() {
        assertEquals(QqUiAction.Click(2, "song"), ready().next(pkg, rows()))
    }
    @Test fun sameTitleByAnotherArtistIsNeverClicked() {
        assertEquals(QqUiAction.Wait, ready().next(pkg, rows("翻唱者")))
    }
    @Test fun artistElsewhereInResultsDoesNotQualifyTheWrongSongRow() {
        val nodes = rows("翻唱者") + QqUiNode(5, text = "h3r3", parent = 0)
        assertEquals(QqUiAction.Wait, ready().next(pkg, nodes))
    }
    @Test fun searchInputContainingTitleIsNeverMistakenForAResult() {
        val noArtist = QqSearchPlan(SongRequest.of("灰", null), pkg).apply {
            applied(QqUiAction.Fill(1, "灰"), true); applied(QqUiAction.Submit(1), true)
        }
        assertEquals(QqUiAction.Wait, noArtist.next(pkg, listOf(field("灰").copy(clickable = true))))
    }
    @Test fun wholeListsAndLargeCardsAreNeverClickedAsSongs() {
        assertEquals(QqUiAction.Wait, ready().next(pkg, rows(height = 600)))
    }
    @Test fun musicVideoCardsAreNotSelectedAsSongRows() {
        val video = rows() + QqUiNode(5, parent = 2, text = "MV")
        assertEquals(QqUiAction.Wait, ready().next(pkg, video))
    }
    @Test fun singleSongsTabIsSelectedBeforeMixedSearchResults() {
        val plan = ready()
        val action = plan.next(pkg, rows() + QqUiNode(6, text = "单曲", clickable = true))
        assertEquals(QqUiAction.Click(6, "songs"), action)
        plan.applied(action, true)
        assertEquals(QqUiAction.Click(2, "song"), plan.next(pkg, rows() + QqUiNode(6, text = "单曲", clickable = true)))
    }
    @Test fun songsTabAlternativeLabelWithCountIsSupported() {
        assertEquals(QqUiAction.Click(6, "songs"), ready().next(pkg, rows() + QqUiNode(6, text = "歌曲 20", clickable = true)))
    }
    @Test fun explicitNoResultsStopsTheTask() {
        assertTrue(ready().next(pkg, listOf(QqUiNode(1, text = "暂无搜索结果"))) is QqUiAction.Stop)
    }
    @Test fun leavingQQOrAskingToOperateAnotherAppStopsTheTask() {
        assertTrue(ready().next("com.tencent.mm", rows()) is QqUiAction.Stop)
        assertTrue(QqSearchPlan(song, "com.tencent.mm").next("com.tencent.mm", rows()) is QqUiAction.Stop)
    }
    @Test fun loginAndPaymentPagesStopWithoutClickingTheirButtons() {
        assertTrue(ready().next(pkg, listOf(QqUiNode(1, text = "立即支付", clickable = true))) is QqUiAction.Stop)
        assertTrue(ready().next(pkg, listOf(QqUiNode(1, text = "登录", clickable = true), QqUiNode(2, text = "验证码"))) is QqUiAction.Stop)
    }
    @Test fun afterOneSongClickNoFurtherUiActionsAreIssued() {
        val plan = ready()
        val action = plan.next(pkg, rows())
        plan.applied(action, true)
        assertTrue(plan.picked)
        assertEquals(QqUiAction.Wait, plan.next(pkg, rows()))
    }
}
