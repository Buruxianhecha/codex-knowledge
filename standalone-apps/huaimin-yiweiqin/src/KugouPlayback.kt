package com.cleo.cleos.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

interface KugouPlaybackPhone {
    fun installedKugou(): LaunchableApp
    fun notificationAllowed(): Boolean
    fun screenControlKugou(): Boolean
    suspend fun beginKugou(app: LaunchableApp, song: SongRequest): Boolean
    fun current(app: LaunchableApp): NowPlaying?
    suspend fun selectKugouOnScreen(app: LaunchableApp, song: SongRequest): String?
}

class KugouPlayback(private val phone: KugouPlaybackPhone, private val pause: suspend (Long) -> Unit = { delay(it) }) {
    private val active = Mutex()

    suspend fun play(song: SongRequest): ToolOutcome {
        if (!active.tryLock()) throw ToolFailure("上一次 酷狗音乐点歌还在进行，请等它结束后再点下一首。", "正在点另一首歌")
        try {
            val app = phone.installedKugou()
            if (app.packageName !in setOf("com.kugou.android")) throw ToolFailure("没有找到可调用的 酷狗音乐安装包，请确认已经安装 酷狗音乐。", "不是 酷狗音乐安装包")
            if (!phone.notificationAllowed()) throw ToolFailure(
                "请先在「设置 → 能做的事 → 打开手机 App」点「播放状态权限」，开启「怀民亦未寝」的通知使用权，才能核实点的歌有没有播放。", "没开播放状态权限",
            )
            val nativeSent = phone.beginKugou(app, song)
            confirmed(app, song, if (nativeSent) 24 else 2)?.let { return done(it) }
            if (!phone.screenControlKugou()) throw ToolFailure(
                "已经请求打开 ${app.label}，但没有确认到目标歌曲播放。请在「设置 → 能做的事 → 打开手机 App」点「酷狗音乐点歌权限」，开启「怀民亦未寝 · 酷狗音乐点歌」，再说一次点歌指令。", "还没开 酷狗音乐点歌权限",
            )
            phone.selectKugouOnScreen(app, song)?.let { throw ToolFailure(it, "酷狗音乐选歌未完成") }
            confirmed(app, song, 32)?.let { return done(it) }
            throw ToolFailure(
                "已在 酷狗音乐点选《${song.title}》，但没有确认到这首歌正在播放。请检查 酷狗音乐是否要求登录、会员或网络连接；不能说已播放。", "未确认目标歌曲播放",
            )
        } finally {
            active.unlock()
        }
    }

    private suspend fun confirmed(app: LaunchableApp, song: SongRequest, looks: Int): NowPlaying? {
        repeat(looks) {
            val playing = phone.current(app)
            if (song.matches(playing, app.packageName)) return playing
            pause(250)
        }
        return phone.current(app)?.takeIf { song.matches(it, app.packageName) }
    }

    private fun done(song: NowPlaying) = ToolOutcome(
        "已从 酷狗音乐的实际播放状态确认正在播放《${song.title}》${song.artist.takeIf { it.isNotBlank() }?.let { "，歌手：$it" }.orEmpty()}。",
        "酷狗音乐正在播放《${song.title}》",
    )
}
