package com.cleo.cleos.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cleo.cleos.ai.LaunchOutcome
import com.cleo.cleos.ai.MusicLaunch
import com.cleo.cleos.ai.SongPlayer
import com.cleo.cleos.ai.ChatSongLaunchRequest
import com.cleo.cleos.ai.SongRequests
import kotlinx.coroutines.flow.collect

/** Foreground UI: select installed player, warn on loud volume, and report launch status. */
@Composable
internal fun MusicSongRequestDialogs(
    conversationId: Long?,
    visible: Boolean,
    onStatus: (String) -> Unit,
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ChatSongLaunchRequest?>(null) }
    var loudPlayer by remember { mutableStateOf<SongPlayer?>(null) }

    fun dismiss() {
        pending = null
        loudPlayer = null
    }

    fun launch(player: SongPlayer) {
        val request = pending ?: return
        val percent = runCatching { MusicLaunch.musicVolumePercent(context) }.getOrDefault(50)
        if (MusicLaunch.requiresLoudnessConfirmation(percent) && loudPlayer == null) {
            loudPlayer = player
            return
        }
        val changed = MusicLaunch.normalizeLowMusicVolume(context)
        val outcome = MusicLaunch.request(context, player, request)
        val status = when (outcome) {
            LaunchOutcome.PLAY_REQUESTED ->
                "已向" + player.name + "发送《" + request.title + "》的播放请求，请以播放器实际状态为准"
            LaunchOutcome.SEARCH_OPENED ->
                "已打开" + player.name + "的歌曲搜索，请在播放器里确认播放"
            LaunchOutcome.APP_OPENED ->
                "已打开" + player.name + "，但它不支持直接按歌名搜索播放，请在应用内搜索《" + request.title + "》"
            LaunchOutcome.FAILED ->
                "没有成功打开" + player.name + "，请确认音乐 App 已安装且可正常启动"
        }
        onStatus(if (!changed) status + "；系统未允许自动调整音量" else status)
        dismiss()
    }

    LaunchedEffect(conversationId, visible) {
        if (visible && conversationId != null) {
            SongRequests.events.collect { request ->
                if (request.conversationId == conversationId) {
                    pending = request
                    loudPlayer = null
                }
            }
        } else dismiss()
    }

    val choices = remember(pending) { MusicLaunch.installed(context) }
    LaunchedEffect(pending, choices) {
        if (pending != null && choices.size == 1 && loudPlayer == null) launch(choices.single())
    }
    val req = pending ?: return
    if (choices.isEmpty()) {
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text("找不到音乐播放器") },
            text = { Text("没有发现已安装的 QQ 音乐、酷狗、网易云、酷我或咪咕音乐。请先安装其中一款。") },
            confirmButton = { TextButton(onClick = { dismiss() }) { Text("知道了") } },
        )
    } else if (loudPlayer != null) {
        val chosen = loudPlayer ?: return
        val percent = runCatching { MusicLaunch.musicVolumePercent(context) }.getOrDefault(100)
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text("当前媒体音量较高") },
            text = { Text("目前音量约 " + percent + "%，播放音乐可能会打扰周围的人。是否继续使用" + chosen.name + "？继续不会自动降低音量。") },
            confirmButton = { TextButton(onClick = { launch(chosen) }) { Text("继续播放") } },
            dismissButton = { TextButton(onClick = { dismiss() }) { Text("取消") } },
        )
    } else if (choices.size > 1) {
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text("选择音乐播放器") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("想听《" + req.title + "》" + req.artist.takeIf { it.isNotBlank() }?.let { " · " + it }.orEmpty() + "，用哪个播放器？")
                    choices.forEach { player ->
                        TextButton(onClick = { launch(player) }, modifier = Modifier.fillMaxWidth()) {
                            Text(player.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dismiss() }) { Text("取消") } },
        )
    }
}
