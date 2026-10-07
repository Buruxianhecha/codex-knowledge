package com.cleo.cleos

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Release notes shown once per installed version.
 *
 * Android's system package installer does not expose an APK-controlled changelog area,
 * so the app shows this immediately on the first launch of each new version instead.
 * Update [CURRENT_RELEASE_NOTES] for every release.
 */
internal val CURRENT_RELEASE_NOTES = listOf(
    "新增「自由找话题」：TA 可以在没有提醒的情况下，结合性格、记忆、共同经历和最近聊天，自己决定要不要来找你。",
    "主动程度新增「跟随性格、偶尔、自然、比较主动」。这些控制的是考虑机会，不是强制发送次数；没有自然想说的内容时 TA 会保持安静。",
    "新增「聊完再说一点」、自由找话题免打扰和主动消息记录；连续两轮主动消息没人回复会先停下，用户开始输入或发消息会取消旧主动生成。",
    "主动消息继续使用真实多气泡，单次主动最多 3 个气泡；最近主动过的话题会提供给模型用于避免重复。0.37.20 的消息编辑与其他现有功能全部保留。",
)

@Composable
internal fun ReleaseNotesDialogIfNeeded() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
    if (version.isBlank()) return

    val prefs = remember {
        context.getSharedPreferences("huaimin_release_notes", Context.MODE_PRIVATE)
    }
    var show by remember(version) {
        mutableStateOf(prefs.getString("seen_version", null) != version)
    }
    if (!show) return

    fun dismiss() {
        prefs.edit().putString("seen_version", version).apply()
        show = false
    }

    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text("本次更新 · $version") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                CURRENT_RELEASE_NOTES.forEachIndexed { index, note ->
                    Text(
                        text = "${index + 1}. $note",
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { dismiss() }) {
                Text("知道了")
            }
        },
    )
}



