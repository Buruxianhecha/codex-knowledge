package com.cleo.cleos.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import java.util.Locale

/** Only launcher entries are exposed: arbitrary intents and private activities are not tools. */
data class LaunchableApp(val packageName: String, val label: String)

object AppNames {
    val qqMusic = listOf("com.tencent.qqmusic", "com.tencent.qqmusiclite", "com.tencent.qqmusicpad")
    private val aliases = mapOf(
        "qq音乐" to qqMusic, "qqmusic" to qqMusic,
        "微信" to listOf("com.tencent.mm"), "wechat" to listOf("com.tencent.mm"),
        "qq" to listOf("com.tencent.mobileqq"),
        "网易云音乐" to listOf("com.netease.cloudmusic"), "网易云" to listOf("com.netease.cloudmusic"),
        "酷狗音乐" to listOf("com.kugou.android"), "酷我音乐" to listOf("cn.kuwo.player"),
        "哔哩哔哩" to listOf("tv.danmaku.bili"), "b站" to listOf("tv.danmaku.bili"),
        "抖音" to listOf("com.ss.android.ugc.aweme"), "支付宝" to listOf("com.eg.android.AlipayGphone"),
    )
    fun key(text: String) = text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "")

    fun find(name: String, installed: List<LaunchableApp>): LaunchableApp {
        val wanted = key(name)
        if (wanted.isEmpty() || name.length > 100) throw ToolFailure("请提供要打开的 App 名称。", "App 名称不完整")
        val apps = installed.distinctBy { it.packageName }
        apps.firstOrNull { key(it.packageName) == wanted }?.let { return it }
        aliases[wanted]?.forEach { pkg -> apps.firstOrNull { it.packageName == pkg }?.let { return it } }
        val matches = apps.filter { key(it.label) == wanted }
        if (matches.size == 1) return matches.single()
        if (matches.size > 1) throw ToolFailure(
            "有多个同名 App：${matches.joinToString { "${it.label}（${it.packageName}）" }}。请让对方选择，再用对应包名。", "App 名称有重复",
        )
        throw ToolFailure("没有找到已安装且可打开的「${name.trim()}」。请确认手机上已安装，或提供桌面上的完整名称。", "没找到这个 App")
    }
}

interface PhoneAppActions {
    fun open(name: String): ToolOutcome
    suspend fun playQQ(title: String, artist: String?): ToolOutcome
}

data class SongRequest(val title: String, val artist: String) {
    val query: String get() = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
    fun matches(song: NowPlaying?, player: String): Boolean = song != null && song.player == player && song.playing &&
        AppNames.key(song.title.trim('《', '》', '「', '」', '"')) == AppNames.key(title) &&
        (artist.isEmpty() || artistMatches(song.artist))

    fun artistMatches(text: String): Boolean {
        val wanted = AppNames.key(artist)
        return AppNames.key(text) == wanted || text.split(Regex("[/、,&;；]|\\s+(?:feat\\.?|ft\\.?)\\s+", RegexOption.IGNORE_CASE))
            .any { AppNames.key(it) == wanted }
    }

    companion object {
        fun of(title: String, artist: String?): SongRequest {
            val name = title.trim().trim('《', '》', '「', '」', '"').trim()
            val singer = artist.orEmpty().trim()
            if (name.isBlank() || name.length > 120 || singer.length > 80 || name.any { it.isISOControl() } || singer.any { it.isISOControl() }) {
                throw ToolFailure("请提供完整歌名，歌手可选；歌名最长 120 字、歌手最长 80 字。", "歌曲参数不完整")
            }
            return SongRequest(name, singer)
        }
    }
}

/** Native requests have no success callback. Playback metadata is the acknowledgement. */
interface QqPlaybackPhone {
    fun installed(): LaunchableApp
    fun notificationAllowed(): Boolean
    fun screenControlAllowed(): Boolean
    suspend fun begin(app: LaunchableApp, song: SongRequest): Boolean
    fun current(app: LaunchableApp): NowPlaying?
    suspend fun selectOnScreen(app: LaunchableApp, song: SongRequest): String?
}

class QqPlayback(private val phone: QqPlaybackPhone, private val pause: suspend (Long) -> Unit = { delay(it) }) {
    private val active = Mutex()

    suspend fun play(song: SongRequest): ToolOutcome {
        if (!active.tryLock()) throw ToolFailure("上一次 QQ 音乐点歌还在进行，请等它结束后再点下一首。", "正在点另一首歌")
        try {
            val app = phone.installed()
            if (app.packageName !in AppNames.qqMusic) throw ToolFailure("没有找到可调用的 QQ 音乐安装包，请确认已经安装 QQ 音乐。", "不是 QQ 音乐安装包")
            if (!phone.notificationAllowed()) throw ToolFailure(
                "请先在「设置 → 能做的事 → 打开手机 App」点「播放状态权限」，开启「怀民亦未寝」的通知使用权，才能核实点的歌有没有播放。", "没开播放状态权限",
            )
            val nativeSent = phone.begin(app, song)
            confirmed(app, song, if (nativeSent) 24 else 2)?.let { return done(it) }
            if (!phone.screenControlAllowed()) throw ToolFailure(
                "已经请求打开 ${app.label}，但没有确认到目标歌曲播放。请在「设置 → 能做的事 → 打开手机 App」点「QQ 音乐点歌权限」，开启「怀民亦未寝 · QQ 音乐点歌」，再说一次点歌指令。", "还没开 QQ 音乐点歌权限",
            )
            phone.selectOnScreen(app, song)?.let { throw ToolFailure(it, "QQ 音乐选歌未完成") }
            confirmed(app, song, 32)?.let { return done(it) }
            throw ToolFailure(
                "已在 QQ 音乐点选《${song.title}》，但没有确认到这首歌正在播放。请检查 QQ 音乐是否要求登录、会员或网络连接；不能说已播放。", "未确认目标歌曲播放",
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
        "已从 QQ 音乐的实际播放状态确认正在播放《${song.title}》${song.artist.takeIf { it.isNotBlank() }?.let { "，歌手：$it" }.orEmpty()}。",
        "QQ 音乐正在播放《${song.title}》",
    )
}
