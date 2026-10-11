package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class QqFinalPlayRulesTest {
    private val song = SongRequest.of("小半", "陈粒")
    private fun player() = listOf(
        QqUiNode(1, text="小半", centerX=120, centerY=1100),
        QqUiNode(2, text="陈粒", centerX=120, centerY=1150),
        QqUiNode(3, text="01:58", centerX=80, centerY=1960),
        QqUiNode(4, text="04:57", centerX=960, centerY=1960),
    )
    private fun choose(nodes: List<QqUiNode>) =
        QqFinalPlayRules.choose(song, nodes, 0, 0, 1080, 2414)

    @Test fun fullscreenPlayerWithoutAccessiblePlayButtonUsesOneBoundedGesture() {
        assertEquals(QqFinalPlayRules.Action.Gesture(540,2184), choose(player()))
    }
    @Test fun semanticallyLabeledPlayButtonIsPreferred() {
        val nodes=player()+QqUiNode(5,description="播放",clickable=true,centerX=540,centerY=2190)
        assertEquals(QqFinalPlayRules.Action.Click(5),choose(nodes))
    }
    @Test fun searchEditorPreventsGestureOrClick() {
        assertNull(choose(player()+QqUiNode(6,text="陈粒 小半",editable=true)))
    }
    @Test fun songOrArtistMismatchCannotStartAnotherTrack() {
        assertNull(choose(player().filter { it.id!=1 }))
        assertNull(choose(player().filter { it.id!=2 }))
    }
    @Test fun onlyOneTimeLabelCannotAuthorizeAPlayGesture() {
        assertNull(choose(player().filter { it.id!=4 }))
    }
    @Test fun pauseButtonMeansDoNotTogglePlayback() {
        assertNull(choose(player()+QqUiNode(5,description="暂停",clickable=true,centerX=540,centerY=2190)))
    }
    @Test fun differentClickTargetsOutsidePlayControlCannotBeUsed() {
        val candidate=QqUiNode(5,description="播放",clickable=true,centerX=950,centerY=850)
        assertEquals(QqFinalPlayRules.Action.Gesture(540,2184),choose(player()+candidate))
    }
}
