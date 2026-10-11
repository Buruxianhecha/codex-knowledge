package com.cleo.cleos.ui.settings

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.cleo.cleos.ai.DeviceGallery
import com.cleo.cleos.ai.GalleryAccess
import com.cleo.cleos.ai.GalleryPermissions
import com.cleo.cleos.ai.GalleryPhoto
import com.cleo.cleos.ai.GalleryReviewQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AI can propose candidates but NEVER invokes MediaStore.createTrashRequest.
 * An explicit user tap AND Android's system confirmation are both required.
 */
@Composable
internal fun GalleryManagerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val gallery = remember(ctx) { DeviceGallery(ctx) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(emptyList<GalleryPhoto>()) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    val suggestions by GalleryReviewQueue.ids.collectAsState()
    val permission = GalleryPermissions.access(ctx)

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        busy = false
        if (result.resultCode == Activity.RESULT_OK) {
            notice = "已向系统提交移入回收站操作。可以在系统相册的回收站中查看和恢复。"
            GalleryReviewQueue.clear()
            selected = emptySet()
            revision++
        } else {
            notice = "已取消，没有移入回收站。"
        }
    }
    LaunchedEffect(query,album,revision,permission,suggestions) {
        delay(260)
        busy = true
        try {
            val recent = gallery.listPhotos(query,album,0,45)
            val proposed = if (query.isBlank() && album.isBlank())
                gallery.photosByIds(suggestions) else emptyList()
            rows = (proposed + recent).distinctBy { it.id }
            selected = selected.filterTo(mutableSetOf()) { id ->
                rows.any { it.id == id }
            }
        } catch (e: Exception) {
            notice = "相册读取失败：" + (e.message ?: "请检查系统授权")
        } finally {
            busy = false
        }
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("返回") }
                    Text("AI 相册管理", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { revision++ }) { Text("刷新") }
                }
                Text(
                    when (permission) {
                        GalleryAccess.All -> "系统允许访问全部照片 · AI 工具还需要单独开关"
                        GalleryAccess.Selected -> "系统只允许访问选中的照片 · 可在系统权限中扩大范围"
                        GalleryAccess.None -> "没有系统相册权限，请先返回打开授权"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "AI 查看照片时，选中的图片缩略图会发送给你所配置的模型服务；" +
                        "照片不会因开启相册权限就自动批量上传。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (suggestions.isNotEmpty()) {
                    Text(
                        "AI 建议检查 " + suggestions.size +
                            " 张照片。以下只是候选，必须由你亲自勾选和系统确认才会移入回收站。",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                    )
                    TextButton(onClick = {
                        // AI candidates do not imply opt-in; show thumbnails first.
                        query = ""
                        album = ""
                        selected = emptySet()
                    }) { Text("查看 AI 建议候选") }
                }
                OutlinedTextField(value = query, onValueChange = { query = it },
                    label = { Text("搜索文件名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = album, onValueChange = { album = it },
                    label = { Text("按相册名称筛选（可留空）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                notice?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                Text("勾选后可移入系统回收站；不执行永久删除。已选 " + selected.size + " 张",
                    fontSize = 12.sp)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    // Suggestions displayed at the top only when present on this
                    // accessible system permission scope; never include hidden files.
                    val displayed = rows.sortedBy {
                        if (it.id in suggestions) 0 else 1
                    }
                    items(displayed, key = { it.id }) { photo ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (photo.id in selected) selected - photo.id
                                    else selected + photo.id
                            }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = gallery.uriFor(photo.id), contentDescription = photo.title,
                                modifier = Modifier.size(74.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(photo.title, maxLines = 2, fontSize = 13.sp)
                                Text(photo.album +
                                    (if (photo.id in suggestions) " · AI 建议审核" else ""),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(checked = photo.id in selected, onCheckedChange = { yes ->
                                selected = if (yes) selected + photo.id else selected - photo.id
                            })
                        }
                    }
                }
                Button(
                    enabled = selected.isNotEmpty() && !busy && Build.VERSION.SDK_INT >= 30,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // Only currently visible/accessible and user-checked media.
                        val ids = selected.filter { id -> rows.any { it.id == id } }
                        if (ids.isEmpty()) return@Button
                        busy = true
                        scope.launch {
                            try {
                                val request = MediaStore.createTrashRequest(
                                    ctx.contentResolver, ids.map(gallery::uriFor), true)
                                trashLauncher.launch(
                                    IntentSenderRequest.Builder(request.intentSender).build()
                                )
                            } catch (e: Exception) {
                                busy = false
                                notice = "无法打开系统回收站确认：" +
                                    (e.message ?: "请检查相册权限")
                            }
                        }
                    },
                ) {
                    Text("将已勾选的 " + selected.size + " 张移入系统回收站（系统再次确认）")
                }
                if (Build.VERSION.SDK_INT < 30) Text(
                    "当前 Android 版本没有受支持的统一系统回收站接口，已禁用删除。",
                    color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}
