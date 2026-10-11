package com.cleo.cleos.ai

/** Search state advances from observed results, rather than a dispatched tap. */
class KugouSearchPlan(private val song: SongRequest, private val player: String) {
    private var opened = false
    private var filled = false
    private var submitted = false
    private var songsTab = false
    private var tabAttempts = 0
    private var tabWaitFrames = 0
    private var structuralFrames = 0
    private var songAttempts = 0
    private var retrySongGesture = false
    var picked = false
        private set

    fun canDismissKeyboard(nodes: List<QqUiNode>): Boolean {
        val labels = nodes.flatMap { it.words }.map { it.trim() }
        val requestedTitle = nodes.any { !it.editable && it.words.any { w -> AppNames.key(w.trim('《', '》')) == AppNames.key(song.title) } }
        val editors = nodes.filter { it.editable }
        val loaded = requestedTitle && (editors.isEmpty() || editors.any { AppNames.key(it.text) in setOf(AppNames.key(song.query), AppNames.key(song.title)) })
        return (submitted || loaded) && labels.contains("综合") && labels.contains("视频") && labels.contains("单曲")
    }

    fun retryUnconfirmedSong() { picked = false; retrySongGesture = true }

    fun next(packageName: String, nodes: List<QqUiNode>): QqUiAction {
        if (packageName != player || player != "com.kugou.android") return QqUiAction.Stop("酷狗音乐不在前台，点歌已停止。")
        val labels = nodes.flatMap { it.words }.map { it.trim() }
        if (labels.any { it in setOf("确认支付", "立即支付", "微信支付", "支付宝支付") }) return QqUiAction.Stop("酷狗音乐要求付款，请你自己处理；自动点歌已停止。")
        if (labels.any { it.contains("验证码") || it == "密码" } && labels.any { it.contains("登录") }) return QqUiAction.Stop("酷狗音乐需要登录，请先完成登录后重新点歌。")
        if (picked) return QqUiAction.Wait
        val byId = nodes.associateBy { it.id }
        fun clickFor(node: QqUiNode, maxHeight: Int = 220): Int? {
            var at: QqUiNode? = node
            repeat(5) {
                val current = at ?: return null
                if (current.heightDp > maxHeight) return null
                if (current.clickable) return current.id
                at = byId[current.parent]
            }
            return null
        }
        fun click(node: QqUiNode, purpose: String, target: Int? = clickFor(node, 150), retry: Boolean = false): QqUiAction.Click? {
            val located = node.centerX > 0 && node.centerY > 0
            val semantic = target ?: node.id.takeIf { located } ?: return null
            return QqUiAction.Click(semantic, purpose,
                gestureId = node.id.takeIf { located && (semantic != node.id || retry) },
                preferGesture = retry && located)
        }
        val field = nodes.firstOrNull { it.editable && (it.viewId.contains("search", true) || it.words.any { word -> word.contains("搜索") }) }
            ?: nodes.singleOrNull { it.editable }?.takeIf { labels.any { label -> label in setOf("搜索", "搜索音乐", "搜索歌曲") } }
        // The submitted result page is stronger evidence than a previous
        // ACTION_IME_ENTER return value. Some players rewrite the editor to
        // title-only text or remove it after loading the result list.
        val requestedResult = labels.contains("综合") && labels.contains("视频") && labels.contains("单曲") &&
            (field == null || AppNames.key(field.text) in setOf(AppNames.key(song.query), AppNames.key(song.title))) &&
            nodes.any { !it.editable && it.words.any { w -> AppNames.key(w.trim('《', '》')) == AppNames.key(song.title) } }
        if (requestedResult) { filled = true; submitted = true }
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
            if (AppNames.key(field.text) != AppNames.key(song.query)) return QqUiAction.Fill(field.id, song.query)
            val button = nodes.firstOrNull { !it.editable && it.words.any { w -> w.trim() == "搜索" } && clickFor(it) != null }
            return button?.let { QqUiAction.Click(clickFor(it)!!, "submit") } ?: QqUiAction.Submit(field.id)
        }
        if (labels.any { it in setOf("暂无搜索结果", "没有找到相关歌曲", "没有搜索结果", "暂无结果") }) return QqUiAction.Stop("酷狗音乐没有找到《${song.title}》的搜索结果。")
        val searchTabs = labels.contains("综合") && labels.contains("视频")
        val artistHome = !searchTabs && labels.any { it.contains("入驻艺人") || it.contains("粉丝") } &&
            labels.any { it == "关注" || it == "播放全部" }
        if (artistHome) return QqUiAction.Stop("误入歌手主页，已停止自动操作。请返回搜索，切到「单曲」再点歌。")
        val nav = nodes.firstOrNull { it.words.any { w -> w.trim() == "综合" } }
        val video = nodes.firstOrNull { it.words.any { w -> w.trim() == "视频" } }
        val tab = if (searchTabs) nodes.firstOrNull { candidate ->
            candidate.words.any { w -> w.trim() == "单曲" } && candidate.heightDp in 1..110 &&
                (if ((nav?.centerY ?: -1) > 0 && (video?.centerY ?: -1) > 0) {
                    val tolerance = 44 * candidate.density
                    candidate.centerX > 0 && candidate.centerY > 0 &&
                        kotlin.math.abs(candidate.centerY - nav!!.centerY) < tolerance &&
                        kotlin.math.abs(candidate.centerY - video!!.centerY) < tolerance
                } else clickFor(candidate) != null)
        } else null
        val artistCard = labels.contains("关注") &&
            labels.any { it.contains("歌曲") && it.any(Char::isDigit) } &&
            labels.any { it.contains("专辑") && it.any(Char::isDigit) }
        val comprehensiveSelected = nodes.any { it.selected && it.words.any { w -> w.trim() == "综合" } }

        fun rowTarget(): QqUiAction.Click? {
            for (title in nodes.filter { !it.editable && it.words.any { w -> AppNames.key(w.trim('《', '》')) == AppNames.key(song.title) } }) {
                var row: QqUiNode? = title
                repeat(5) {
                    val group = row ?: return@repeat
                    if (group.heightDp > 150) { row = null; return@repeat }
                    val parts = nodes.filter { it.id == group.id || descendant(it, group.id, byId) }
                    val words = parts.flatMap { it.words }
                    val artist = song.artist.isEmpty() || words.any { song.artistMatches(it) ||
                        it.split(Regex("\\s*[-|·]\\s*")).any(song::artistMatches) }
                    val nonSong = words.any { it.trim() in setOf("MV", "视频", "专辑", "歌单", "关注", "入驻艺人", "歌手主页") }
                    var target = clickFor(title, 150)
                    // Guard only a distinct singer link, never the verified exact title.
                    if (target != title.id && target != group.id && byId[target]?.words?.any { song.artist.isNotBlank() && song.artistMatches(it) } == true) target = null
                    if (group.id != title.id && parts.size in 2..28 && artist && !nonSong) {
                        click(title, "song", target, retrySongGesture)?.let { return it }
                    }
                    row = byId[group.parent]
                }
            }
            return null
        }
        val result = rowTarget()
        if (!songsTab) {
            if (tab?.selected == true && !artistCard && !comprehensiveSelected) songsTab = true
            else {
                // Some custom tabs do not report selected. Require two consecutive
                // result snapshots after our tab click, with the singer card gone.
                val structural = tabAttempts > 0 && tab != null && !artistCard && !comprehensiveSelected && result != null
                structuralFrames = if (structural) structuralFrames + 1 else 0
                if (structuralFrames >= 2) songsTab = true
                else {
                    if (tabAttempts > 0 && tabWaitFrames++ < 2) return QqUiAction.Wait
                    if (tabAttempts >= 3) return QqUiAction.Stop("酷狗音乐没有切换到「单曲」，已停止重试。")
                    return tab?.let { click(it, "songs", retry = tabAttempts > 0) } ?: QqUiAction.Wait
                }
            }
        }
        if (artistCard || comprehensiveSelected) { songsTab = false; structuralFrames = 0; return QqUiAction.Wait }
        if (songAttempts >= 3) return QqUiAction.Stop("已点选匹配歌曲，但酷狗没有确认播放，已停止重试。")
        return result ?: QqUiAction.Wait
    }

    fun applied(action: QqUiAction, success: Boolean) {
        if (!success) return
        when (action) {
            is QqUiAction.Fill -> filled = true
            is QqUiAction.Submit -> submitted = true
            is QqUiAction.Click -> when (action.purpose) {
                "search" -> opened = true
                "submit" -> submitted = true
                "songs" -> { tabAttempts++; tabWaitFrames = 0 }
                "song" -> { picked = true; songAttempts++ }
            }
            else -> Unit
        }
    }

    private fun descendant(node: QqUiNode, parent: Int, byId: Map<Int, QqUiNode>): Boolean {
        var at = node.parent
        repeat(12) {
            if (at == parent) return true
            at = byId[at]?.parent ?: return false
        }
        return false
    }
}
