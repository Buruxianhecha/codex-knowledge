package com.cleo.cleos.ai

/**
 * The QQ search-result click merely opens the player. Do not claim success
 * until the correct track's real MediaSession reports PLAYING.
 */
object QqFinalPlayRules {
    sealed interface Action {
        data class Click(val nodeId: Int) : Action
        data class Gesture(val x: Int, val y: Int) : Action
    }

    fun choose(
        song: SongRequest, nodes: List<QqUiNode>,
        boundsLeft: Int, boundsTop: Int, width: Int, height: Int,
    ): Action? {
        if (width <= 0 || height <= 0 || nodes.any { it.editable }) return null
        val title = nodes.any { node -> !node.editable && node.words.any {
            AppNames.key(it.trim().trim('《', '》', '「', '」', '"')) == AppNames.key(song.title)
        } }
        if (!title) return null
        if (song.artist.isNotBlank() && nodes.none { node -> !node.editable && node.words.any { song.artistMatches(it) } }) return null

        // A full-screen player has both elapsed and total duration labels.
        // Search results may show individual duration labels, but not the pair
        // alongside a dedicated full-screen transport area with no edit field.
        val times = nodes.flatMap { it.words }.map { it.trim() }
            .filter { it.matches(Regex("""\d{1,2}:\d{2}""")) }
        if (times.distinct().size < 2) return null

        val targetY = boundsTop + (height * 0.905f).toInt()
        val targetX = boundsLeft + width / 2
        val buttons = nodes.filter { node -> node.clickable && node.centerX >= 0 && node.centerY >= 0 &&
            node.centerX in (boundsLeft + width/4)..(boundsLeft + width*3/4) &&
            node.centerY in (boundsTop + height*4/5)..(boundsTop + height*96/100) &&
            node.words.any { word ->
                val text = word.trim().lowercase()
                text == "播放" || text == "继续播放" || text == "播放歌曲" ||
                    (nodePlayId(node.viewId) && text.isBlank())
            }
        }
        if (buttons.size == 1) return Action.Click(buttons.single().id)
        // Only use the standardized control center when the actual full
        // player is visible, never on a search result or a page with a keyboard.
        if (nodes.any { node -> node.words.any {
            val label=it.trim()
            label == "暂停" || label == "正在播放"
        } }) return null
        return Action.Gesture(targetX, targetY)
    }

    private fun nodePlayId(viewId: String): Boolean {
        val id = viewId.lowercase()
        return "play" in id && "playlist" !in id && "play_list" !in id && "replay" !in id
    }
}
