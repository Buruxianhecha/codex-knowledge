package com.cleo.cleos.ai

/** A bounded semantic plan; Android nodes stay on the device and are never sent to the model. */
data class QqUiNode(
    val id: Int, val parent: Int? = null, val text: String = "", val description: String = "",
    val viewId: String = "", val editable: Boolean = false, val clickable: Boolean = false,
    val selected: Boolean = false, val heightDp: Int = 48, val hint: String = "",
) {
    val words: List<String> get() = listOf(text, description, hint).filter { it.isNotBlank() }
}

sealed interface QqUiAction {
    data class Click(val id: Int, val purpose: String) : QqUiAction
    data class Fill(val id: Int, val query: String) : QqUiAction
    data class Submit(val id: Int) : QqUiAction
    data class Stop(val reason: String) : QqUiAction
    data object Wait : QqUiAction
}

class QqSearchPlan(private val song: SongRequest, private val player: String) {
    private var opened = false
    private var filled = false
    private var submitted = false
    private var songsTab = false
    var picked = false
        private set

    fun next(packageName: String, nodes: List<QqUiNode>): QqUiAction {
        if (packageName != player || player !in AppNames.qqMusic) return QqUiAction.Stop("QQ 音乐不在前台，点歌已停止。")
        if (picked) return QqUiAction.Wait
        val labels = nodes.flatMap { it.words }.map { it.trim() }
        if (labels.any { it in setOf("确认支付", "立即支付", "微信支付", "支付宝支付") }) return QqUiAction.Stop("QQ 音乐要求付款，请你自己处理；自动点歌已停止。")
        if (labels.any { it.contains("验证码") || it == "密码" } && labels.any { it.contains("登录") }) return QqUiAction.Stop("QQ 音乐需要登录，请先完成登录后重新点歌。")
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
                (node.words.any { it.trim() in setOf("搜索", "搜索音乐", "搜索歌曲", "搜索歌曲、歌手", "搜索歌曲、歌手、专辑") } ||
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
        if (labels.any { it in setOf("暂无搜索结果", "没有找到相关歌曲", "没有搜索结果", "暂无结果") }) return QqUiAction.Stop("QQ 音乐没有找到《${song.title}》的搜索结果。")
        val tab = nodes.firstOrNull { it.words.any { w -> w.trim().matches(Regex("单曲(?:\\s*\\d+)?")) } && clickFor(it) != null }
        if (!songsTab && tab != null && !tab.selected) return QqUiAction.Click(clickFor(tab)!!, "songs")
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
                val nonSong = words.any { it.trim() in setOf("MV", "视频", "专辑", "歌单") }
                if (group.id != title.id && parts.size in 2..36 && artist && !nonSong && clickFor(title) != null) return QqUiAction.Click(clickFor(title)!!, "song")
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
