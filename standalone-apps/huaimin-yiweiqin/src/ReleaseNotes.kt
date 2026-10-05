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
    "新增“本次更新”提示：以后每次升级后首次打开 App，会自动告诉你这次更新了什么。",
    "更新说明只在当前版本首次打开时自动出现一次，不会反复打扰。",
    "保留 0.36.5 的表情包真实图片识别、模型保存、预设壁纸和其他现有功能。",
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
