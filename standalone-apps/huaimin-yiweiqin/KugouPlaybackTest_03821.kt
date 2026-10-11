package com.cleo.cleos.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class KugouPlaybackTest {
    private val kugou = LaunchableApp("com.kugou.android", "酷狗音乐")
    private val qq = LaunchableApp("com.tencent.qqmusic", "QQ音乐")
    private val song = SongRequest.of("小半", "陈粒")

    private class Stub(private val app: LaunchableApp) : KugouPlaybackPhone {
        var track: NowPlaying? = null
        var starts = 0
        var selects = 0
        var allowed = true
        var selectionAllowed = true
        override fun installedKugou() = app
        override fun notificationAllowed() = allowed
        override fun screenControlKugou() = selectionAllowed
        override fun current(app: LaunchableApp) = track
        override suspend fun beginKugou(app: LaunchableApp, song: SongRequest): Boolean {
            starts++
            return true
        }
        override suspend fun selectKugouOnScreen(app: LaunchableApp, song: SongRequest): String? {
            selects++
            return "歌曲没找到"
        }
    }

    @Test fun exactKugouPlayedSongIsConfirmed() = runBlocking {
        val phone = Stub(kugou).apply {
            track = NowPlaying(kugou.packageName, "小半", "陈粒", "", 210_000, 5000, 1, true)
        }
        assertTrue(KugouPlayback(phone) {}.play(song).result.contains("酷狗音乐的实际播放状态"))
        assertEquals(1, phone.starts)
        assertEquals(0, phone.selects)
    }

    @Test fun qqMusicCanNeverStandInForKugou() = runBlocking {
        val phone = Stub(qq)
        try {
            KugouPlayback(phone) {}.play(song)
            fail("QQ should be rejected")
        } catch (f: ToolFailure) {
            assertTrue(f.result.contains("酷狗音乐"))
            assertEquals(0, phone.starts)
        }
    }

    @Test fun correctAppButWrongPlayerMetadataIsNotSuccess() = runBlocking {
        val phone = Stub(kugou).apply {
            track = NowPlaying(qq.packageName, "小半", "陈粒", "", 210_000, 2000, 1, true)
        }
        try {
            KugouPlayback(phone) {}.play(song)
            fail("QQ session must not confirm Kugou playback")
        } catch (f: ToolFailure) {
            assertTrue(f.result.contains("没有确认") || f.result.contains("歌曲没找到"))
        }
    }

    @Test fun noPermissionDoesNotOpenAnything() = runBlocking {
        val phone = Stub(kugou).apply { allowed = false }
        try {
            KugouPlayback(phone) {}.play(song)
            fail("should require notification permission")
        } catch (_: ToolFailure) {
            assertEquals(0, phone.starts)
        }
    }

    @Test fun aliasNeverReturnsQQMusic() {
        assertEquals(kugou, AppNames.find("酷狗", listOf(qq, kugou)))
        assertEquals(kugou, AppNames.find("酷狗音乐", listOf(qq, kugou)))
        try {
            AppNames.find("酷狗音乐", listOf(qq))
            fail("no fallback permitted")
        } catch (_: ToolFailure) { }
    }
}
