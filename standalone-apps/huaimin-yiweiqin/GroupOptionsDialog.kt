package com.cleo.cleos.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Persisted group controls. No hidden background message generation. */
@Composable
fun GroupOptionsDialog(
    state: ChatUiState,
    onSave: (mode: Int, maxReplies: Int, dailyLimit: Int, shareOutside: Boolean, announcement: String, muted: Set<Long>, voices: Map<Long, String>, avatar: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember(state.conversationId) { mutableIntStateOf(state.groupMode) }
    var groupAvatar by remember(state.conversationId) { mutableStateOf(state.groupAvatarEmoji) }
    var maxReplies by remember(state.conversationId) { mutableStateOf(state.groupMaxReplies.toString()) }
    var dailyLimit by remember(state.conversationId) { mutableStateOf(state.groupDailyLimit.toString()) }
    var share by remember(state.conversationId) { mutableStateOf(state.groupShareOutside) }
    var announcement by remember(state.conversationId) { mutableStateOf(state.groupAnnouncement) }
    var muted by remember(state.conversationId) { mutableStateOf(state.groupMutedIds.split(",").mapNotNull { it.toLongOrNull() }.toSet()) }
    val voiceValues = remember(state.groupMembers) {
        mutableStateMapOf<Long, String>().apply {
            state.groupMembers.forEach { put(it.id, it.voiceOverride.orEmpty()) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("群聊设置") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("群头像（表情样式）", fontSize = 15.sp)
                val avatars = listOf("👥", "🌙", "🌿", "🍵", "☁️", "✨", "🌸", "🐱")
                avatars.chunked(4).forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        line.forEach { emoji ->
                            TextButton(onClick = { groupAvatar = emoji }) {
                                Text(if (groupAvatar == emoji) "✓$emoji" else emoji, fontSize = 21.sp)
                            }
                        }
                    }
                }
                Text("发言方式", fontSize = 15.sp)
                listOf(
                    0 to "自然聊天（角色决定是否发言）",
                    1 to "每位成员都回复",
                    2 to "仅 @ 或拍一拍时回复",
                ).forEach { (id, title) ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { mode = id }) {
                        Checkbox(checked = mode == id, onCheckedChange = { if (it) mode = id })
                        Text(title, modifier = Modifier.padding(top = 11.dp), fontSize = 13.sp)
                    }
                }
                OutlinedTextField(
                    value = maxReplies,
                    onValueChange = { maxReplies = it.filter(Char::isDigit).take(2) },
                    singleLine = true,
                    label = { Text("每轮最多 AI 回复人数（1–6）") },
                )
                OutlinedTextField(
                    value = dailyLimit,
                    onValueChange = { dailyLimit = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    label = { Text("本群每天最多模型请求（1–120）") },
                )
                Text("今日实际请求尝试：${state.groupCallsToday} 次；累计尝试：${state.groupTotalCalls} 次（含 SKIP 和重试）。", fontSize = 12.sp)
                Text(
                    "文本统计：约 ${state.groupTextInputChars} 输入字符 / ${state.groupTextOutputChars} 输出字符。仅为聊天文本粗略计数，不含完整系统提示、图片、语音或实际 Token；费用以服务商账单为准。",
                    fontSize = 12.sp,
                )
                Row(modifier = Modifier.fillMaxWidth().clickable { share = !share }) {
                    Checkbox(checked = share, onCheckedChange = { share = it })
                    Text("本群 AI 可检索其他授权会话的真实记录", modifier = Modifier.padding(top = 4.dp), fontSize = 13.sp)
                }
                OutlinedTextField(
                    value = announcement,
                    onValueChange = { announcement = it.take(300) },
                    label = { Text("群公告") },
                    minLines = 2,
                    maxLines = 4,
                )
                Text("群成员发言控制", fontSize = 15.sp)
                state.groupMembers.forEach { member ->
                    val isMuted = member.id in muted
                    Row(Modifier.fillMaxWidth().clickable {
                        muted = if (isMuted) muted - member.id else muted + member.id
                    }) {
                        Checkbox(
                            checked = isMuted,
                            onCheckedChange = { muted = if (it) muted + member.id else muted - member.id },
                        )
                        Text("禁言 ${member.name}", modifier = Modifier.padding(top = 10.dp))
                    }
                    OutlinedTextField(
                        value = voiceValues[member.id].orEmpty(),
                        onValueChange = { voiceValues[member.id] = it.take(120) },
                        label = { Text("${member.name} 的朗读音色 ID（空则跟随全局）") },
                        singleLine = true,
                    )
                }
                Text("各角色仍用自己的模型和人格；额外音色 ID 必须是当前语音服务支持的 ID。", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        mode,
                        (maxReplies.toIntOrNull() ?: 3).coerceIn(1, 6),
                        (dailyLimit.toIntOrNull() ?: 40).coerceIn(1, 120),
                        share,
                        announcement.trim(),
                        muted,
                        voiceValues.toMap(),
                        groupAvatar,
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
