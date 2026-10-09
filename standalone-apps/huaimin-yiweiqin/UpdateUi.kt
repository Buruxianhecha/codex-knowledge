package com.cleo.cleos.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleo.cleos.ui.common.appContainer
import com.cleo.cleos.ui.settings.Section
import kotlinx.coroutines.launch

/** A quiet check on launch; no system-notification permission or forced install. */
@Composable
fun UpdateNoticeHost() {
    val controller = appContainer().updates
    val status by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(controller) { controller.check(manual = false) }
    val version = status.offered
    if (version != null) {
        AlertDialog(
            onDismissRequest = { if (!status.downloading) controller.dismiss() },
            title = { Text("发现新版本 v${version.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("《怀民亦未寝》有新版本，当前聊天和角色数据无需先卸载。")
                    if (version.notes.isNotEmpty()) {
                        Text(version.notes.joinToString("\n") { "• $it" },
                            modifier = Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()))
                    }
                    if (status.downloading) {
                        LinearProgressIndicator(
                            progress = { status.progress / 100f }, modifier = Modifier.fillMaxWidth(),
                        )
                        Text("正在下载并校验安装包：${status.progress}%", fontSize = 12.sp)
                    }
                    status.message?.let { Text(it, fontSize = 12.sp) }
                    if (status.readyFile != null) {
                        Text("已验证文件完整性、包名和签名。点击安装后仍需在 Android 系统中确认。",
                            fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !status.checking && !status.downloading,
                    onClick = {
                        if (status.readyFile != null) controller.install()
                        else scope.launch { if (controller.download()) controller.install() }
                    },
                ) { Text(if (status.readyFile != null) "安装新版" else if (status.downloading) "下载中" else "下载并安装") }
            },
            dismissButton = {
                Row {
                    if (!version.mandatory && !status.downloading) {
                        TextButton(onClick = controller::skipThisVersion) { Text("跳过这版") }
                    }
                    TextButton(enabled = !status.downloading, onClick = controller::dismiss) {
                        Text("稍后再说")
                    }
                }
            },
        )
    }
}

/** Settings → About. Manual checks ignore the auto-check timer and skipped-version flag. */
@Composable
fun UpdateSettingsSection() {
    val controller = appContainer().updates
    val current = LocalContext.current
    val status by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var automatic by remember { mutableStateOf(controller.automatic) }
    var source by remember { mutableStateOf(controller.sourceUrl) }
    var sourceStatus by remember { mutableStateOf<String?>(null) }
    Section("怀民亦未寝 · 软件更新") {
        Text("打开软件时自动检查新版本；发现更新后你可以选择下载并覆盖安装，不必每次去 QQ 群找文件。",
            fontSize = 13.sp, lineHeight = 19.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("启动时自动检查更新", fontSize = 13.sp)
            Switch(checked = automatic, onCheckedChange = {
                automatic = it
                controller.setAutomatic(it)
            })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !status.checking && !status.downloading,
                onClick = { scope.launch { controller.check(manual = true) } },
            ) { Text(if (status.checking) "正在检查…" else "检查更新") }
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UpdatePolicy.SITE_URL))
                runCatching { current.startActivity(intent) }
            }) { Text("打开下载官网") }
        }
        val release = status.offered
        if (release != null) {
            Text("发现 v${release.version}（${release.versionCode}）", fontSize = 14.sp)
            if (release.notes.isNotEmpty()) {
                Text(release.notes.joinToString("\n") { "• $it" }, fontSize = 12.sp, lineHeight = 19.sp)
            }
            if (status.downloading) {
                LinearProgressIndicator(progress = { status.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("正在下载 ${status.progress}%", fontSize = 12.sp)
            }
            Button(enabled = !status.downloading, onClick = {
                if (status.readyFile != null) controller.install()
                else scope.launch { if (controller.download()) controller.install() }
            }) { Text(if (status.readyFile != null) "安装新版" else "下载并安装") }
            if (!release.mandatory && !status.downloading)
                TextButton(onClick = controller::skipThisVersion) { Text("跳过此版本") }
        }
        status.message?.let { Text(it, fontSize = 12.sp, lineHeight = 18.sp) }
        Text("如国内网络无法访问当前检查地址，可替换为你自己的 Cloudflare Pages 更新地址（须同时放置 latest.json 和 APK）。",
            fontSize = 11.sp, lineHeight = 16.sp)
        OutlinedTextField(
            value = source,
            onValueChange = { source = it; sourceStatus = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("更新源 HTTPS 地址") },
            placeholder = { Text(UpdatePolicy.FEED_URL, fontSize = 11.sp) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = {
                sourceStatus = if (controller.setSource(source)) "更新地址已保存。" else "请输入有效的 HTTPS 地址。"
            }) { Text("保存更新地址") }
            TextButton(onClick = {
                source = UpdatePolicy.FEED_URL
                sourceStatus = if (controller.setSource(source)) "已恢复默认更新地址。" else "无法恢复"
            }) { Text("恢复默认") }
        }
        sourceStatus?.let { Text(it, fontSize = 12.sp) }
        Text("安装需要 Android 系统确认，并且新版 APK 必须保持相同包名与签名。建议更新前备份聊天记录。",
            fontSize = 11.sp, lineHeight = 17.sp)
    }
}
