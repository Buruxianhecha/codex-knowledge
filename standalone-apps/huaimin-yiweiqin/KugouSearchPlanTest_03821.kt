package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class KugouSearchPlanTest {
    private val pkg = "com.kugou.android"
    private val song = SongRequest.of("小半", "陈粒")
    private fun plan() = KugouSearchPlan(song,pkg).apply {
        applied(QqUiAction.Fill(1,song.query),true)
        applied(QqUiAction.Submit(1),true)
    }

    @Test fun refusesOtherPackagesIncludingQQ() {
        assertTrue(plan().next("com.tencent.qqmusic",emptyList()) is QqUiAction.Stop)
        assertTrue(KugouSearchPlan(song,"com.tencent.qqmusic")
            .next("com.tencent.qqmusic",emptyList()) is QqUiAction.Stop)
    }
    @Test fun searchesKugouArtistAndTitle() {
        val a=KugouSearchPlan(song,pkg).next(pkg,listOf(
            QqUiNode(1,editable=true,viewId="kugou:id/search",hint="搜索歌曲")
        ))
        assertEquals(QqUiAction.Fill(1,"陈粒 小半"),a)
    }
    @Test fun paymentOrLoginStops() {
        assertTrue(plan().next(pkg,listOf(QqUiNode(1,text="立即支付",clickable=true))) is QqUiAction.Stop)
    }
    @Test fun wrongArtistNeverSelected() {
        val nodes=listOf(
            QqUiNode(0,heightDp=900),
            QqUiNode(2,parent=0,clickable=true,heightDp=80),
            QqUiNode(3,parent=2,text="小半"),
            QqUiNode(4,parent=2,text="翻唱歌手"),
        )
        assertEquals(QqUiAction.Wait,plan().next(pkg,nodes))
    }
}
