package com.cleo.cleos.ai

/** A bounded semantic plan; Android nodes stay on the device and are never sent to the model. */
// Use QqUiNode and QqUiAction defined in QqSearchPlan.kt; avoid duplicate classes.

class KugouSearchPlan(private val song: SongRequest, private val player: String) {
    private var opened = false
    private var filled = false
    private var submitted = false
    private var songsTab = false
    var picked = false
        private set

    fun next(packageName: String, nodes: List<QqUiNode>): QqUiAction {
        if (packageName != player || player != "com.kugou.android") return QqUiAction.Stop("酷狗音乐不在前台，点歌已停止。")
        if (picked) return QqUiAction.Wait
        val labels = nodes.flatMap { it.words }.map { it.trim() }
        if (labels.any { it in setOf("确认支付", "立即支付", "微信支付", "支付宝支付") }) return QqUiAction.Stop("酷狗音乐要求付款，请你自己处理；自动点歌已停止。")
        if (labels.any { it.contains("验证码") || it == "密码" } && labels.any { it.contains("登录") }) return QqUiAction.Stop("酷狗音乐需要登录，请先完成登录后重新点歌。")
        fun clickFor(node: QqUiNode): Int? {
            var at: QqUiNode? = node
            repeat(5) {
                val current = at ?: return null
                if (current.heightDp > 220) return null
                if (current.clickable) return current.id
                at = nodes.firstOrNull { it.id == current.parent }
            }
            return null
        }
        val field = nodes.firstOrNull { it.editable && (it.viewId.contains("search", true) || it.words.any { word -> word.contains("搜索") }) }
            ?: nodes.singleOrNull { it.editable }?.takeIf { labels.any { label -> label in setOf("搜索", "搜索音乐", "搜索歌曲") } }
        if (!filled) {
            if (field != null) return QqUiAction.Fill(field.id, song.query)
            if (opened) return QqUiAction.Wait
            val search = nodes.firstOrNull { node -> !node.editable && clickFor(node) != null &&
                (node.words.any { it.trim().startsWith("搜索") && it.trim().length <= 50 } ||
                    node.viewId.contains("search", true) && !node.viewId.contains("history", true)) }
            return search?.let { QqUiAction.Click(clickFor(it)!!, "search") } ?: QqUiAction.Wait
        }
        if (!submitted) {
            if (field == null) return QqUiAction.Wait
            // Don't submit a stale field from a different screen.
            if (AppNames.key(field.text) != AppNames.key(song.query)) return QqUiAction.Fill(field.id, song.query)
            val button = nodes.firstOrNull { !it.editable && it.words.any { w -> w.trim() == "搜索" } && clickFor(it) != null }
            return button?.let { QqUiAction.Click(clickFor(it)!!, "submit") } ?: QqUiAction.Submit(field.id)
        }
        if (labels.any { it in setOf("暂无搜索结果", "没有找到相关歌曲", "没有搜索结果", "暂无结果") }) return QqUiAction.Stop("酷狗音乐没有找到《${song.title}》的搜索结果。")
        // Kugou's 综合 tab contains a large singer profile card ABOVE the song list.
        // Clicking its singer name/photo opens the artist home instead of playing music.
        // Require the real 单曲 search tab before any clickable song candidate.
        val searchTabs = labels.contains("综合") && labels.contains("视频")
        val artistHome = labels.any { it.contains("入驻艺人") || it.contains("粉丝") } &&
            labels.any { it == "关注" || it == "播放全部" }
        if (artistHome) return QqUiAction.Stop("误入歌手主页，已停止自动操作。请返回搜索，切到「单曲」再点歌。")
        // Singer profile's section heading also says 单曲. The navigation tab
        // must align vertically with the 综合 and 视频 navigation labels.
        val navY = nodes.firstOrNull { it.words.any { w -> w.trim() == "综合" } }?.centerY ?: -1
        val videoY = nodes.firstOrNull { it.words.any { w -> w.trim() == "视频" } }?.centerY ?: -1
        val tab = if (searchTabs) nodes.firstOrNull { candidate ->
            candidate.words.any { w -> w.trim() == "单曲" } &&
                candidate.heightDp in 1..110 &&
                (if (navY > 0 && videoY > 0) {
                    candidate.centerX > 0 && candidate.centerY > 0 &&
                        kotlin.math.abs(candidate.centerY - navY) < 125 &&
                        kotlin.math.abs(candidate.centerY - videoY) < 125
                } else clickFor(candidate) != null)
        } else null
        // A visible tab may have no ACTION_CLICK. It is still a valid,
        // uniquely located tap target, unlike arbitrary matching singer text.
        val tabId = tab?.id // precise tab text node, not a broad parent container
        if (!songsTab) {
            if (tab?.selected == true) songsTab = true
            else return tabId?.let { QqUiAction.Click(it, "songs") } ?: QqUiAction.Wait
        }
        // Clicking a tab does not mean the content switched yet; wait for the
        // singer card to disappear, and never select a song in the 综合 page.
        val artistCard = labels.contains("关注") &&
            labels.any { it.contains("歌曲") && it.any(Char::isDigit) } &&
            labels.any { it.contains("专辑") && it.any(Char::isDigit) }
        val comprehensiveSelected = nodes.any { n -> n.selected && n.words.any { it == "综合" } }
        if (artistCard || comprehensiveSelected) return QqUiAction.Wait
        // Only a title in a small result row, with the requested artist in that same row.
        // The search editor, a whole results list, MV cards and arbitrary "play" buttons don't qualify.
        for (title in nodes.filter { !it.editable && it.words.any { w -> AppNames.key(w.trim('《', '》')) == AppNames.key(song.title) } }) {
            var row: QqUiNode? = title
            repeat(5) {
                val group = row ?: return@repeat
                if (group.heightDp > 220) return@repeat
                val parts = nodes.filter { it.id == group.id || descendant(it, group.id, nodes) }
                val words = parts.flatMap { it.words }
                val artist = song.artist.isEmpty() || words.any { song.artistMatches(it) ||
                    it.split(Regex("\\s*[-|·]\\s*")).any(song::artistMatches) }
                val nonSong = words.any { it.trim() in setOf("MV", "视频", "专辑", "歌单", "关注", "入驻艺人", "歌手主页") }
                // Prefer tapping the exact song TITLE bounds, rather than
                // an ancestor that could route to the singer's profile.
                val target = if (title.centerX > 0 && title.centerY > 0) title.id
                    else clickFor(title)
                val targetNode = nodes.firstOrNull { it.id == target }
                // The singer's clickable label can be nested in the same result
                // container. It is never a valid song playback target.
                val singerLink = targetNode?.words?.any { w ->
                    song.artist.isNotBlank() && song.artistMatches(w)
                } == true
                if (group.id != title.id && group.heightDp <= 150 &&
                    parts.size in 2..28 && artist && !nonSong && !singerLink &&
                    target != null) return QqUiAction.Click(target, "song")
                row = nodes.firstOrNull { it.id == group.parent }
            }
        }
        return QqUiAction.Wait
    }

    fun applied(action: QqUiAction, success: Boolean) {
        if (!success) return
        when (action) {
            is QqUiAction.Fill -> filled = true
            is QqUiAction.Submit -> submitted = true
            is QqUiAction.Click -> when (action.purpose) {
                "search" -> opened = true
                "submit" -> submitted = true
                "songs" -> songsTab = true
                "song" -> picked = true
            }
            else -> Unit
        }
    }

    private fun descendant(node: QqUiNode, parent: Int, nodes: List<QqUiNode>): Boolean {
        var at = node.parent
        repeat(8) {
            if (at == parent) return true
            at = nodes.firstOrNull { it.id == at }?.parent ?: return false
        }
        return false
    }
}
