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
    "「设置 → 外观 → 字体」新增内置预设“萌系80”，无需自己导入文件，选择后点保存即可让聊天、日记、设置和按钮文字一起使用。",
    "字体功能仍保留系统默认和自定义 TTF / OTF 导入；内置预设与自己导入的字体分开展示，预览满意后再保存。",
    "字体文件和当前选择继续跟随完整备份；原有 API Key、接口、模型、语音、MCP、14 张头像以及其他 0.37.13 功能全部保留。",
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


