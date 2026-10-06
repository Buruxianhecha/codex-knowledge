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
    "导出备份现在包含已保存的全部 API Key、语音 Key、MCP 连接，以及接口、聊天和语音模型、工具开关、壁纸、字号、拍一拍等配置。新版备份一次导入即可一起恢复。",
    "恢复后保持原来选中的 TA 和对话，设置页立即同步；退出时不会把恢复前的旧配置写回。「撤销上次恢复」也会撤销配置和 Key。",
    "旧备份仍能恢复，但旧文件没有的 Key 需要补填一次，再导出新版完整备份。备份包含可读取的 Key 和连接凭据，请妥善保存；系统权限仍需在手机上授权。",
    "保留 QQ 音乐点歌、打开手机 App、小表情回应、撤回、赞赏码图片、模型保存、表情识图、拍一拍及原有聊天功能。",
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
