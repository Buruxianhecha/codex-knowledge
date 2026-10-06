package com.cleo.cleos.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleo.cleos.data.DisplayFonts
import com.cleo.cleos.glass.GlassShape
import com.cleo.cleos.glass.GlassSurface
import com.cleo.cleos.glass.LocalGlassPalette
import com.cleo.cleos.ui.chat.ChatType
import com.cleo.cleos.ui.theme.ProvideDisplayFont
import com.cleo.cleos.ui.theme.LocalDisplayFontFamily

@Composable
internal fun DisplayFontSettings(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current
    val resolver = LocalContext.current.applicationContext.contentResolver
    var draft by rememberSaveable(settings.displayFont) { mutableStateOf(settings.displayFont) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = settings.displayFonts.firstOrNull { it.file == draft }
    val presets = settings.displayFonts.filter(DisplayFonts::isBundled)
    val imported = settings.displayFonts.filterNot(DisplayFonts::isBundled)
    val deletable = imported.firstOrNull { it.file == draft }
    val deletingFont = imported.firstOrNull { it.file == deleting }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importDisplayFont(uri, resolver) { draft = it.file }
    }

    Section("字体（全软件）") {
        Text("应用到聊天、日记、设置和按钮文字。选好后点保存，退出和重开也会保留。", color = palette.contentSecondary, fontSize = 12.sp)
        Text("字体预设", color = palette.content, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("系统默认", selected = draft == null) { draft = null }
            presets.forEach { font ->
                Chip(font.name, selected = draft == font.file) { draft = font.file }
            }
        }
        if (imported.isNotEmpty()) {
            Text("我的字体", color = palette.content, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                imported.forEach { font ->
                    Chip(font.name, selected = draft == font.file) { draft = font.file }
                }
            }
        } else {
            Text("还没有自己导入的字体，也可以继续从手机添加 TTF 或 OTF 文件。", color = palette.contentSecondary, fontSize = 12.sp)
        }
        ProvideDisplayFont(selected?.let { vm.displayFontFile(it.file) }) {
            GlassSurface(
                style = palette.bubble,
                shape = GlassShape.Rounded(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("怀民亦未寝 · 字体预览", color = palette.content, fontSize = 18.sp)
                    Text("今天也辛苦啦。晚饭吃了吗？我想陪你再聊一会儿。", color = palette.content, style = ChatType(settings.chatTextSize, LocalDisplayFontFamily.current).body)
                    Text("ABC abc 0123456789，。！？", color = palette.content, fontSize = 14.sp)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = !vm.fontBusy && !vm.backupBusy && imported.size < DisplayFonts.MAX_IMPORTED_FONTS, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (vm.fontBusy) "处理中…" else "导入字体")
            }
            TextButton(onClick = { vm.saveDisplayFont(draft) }, enabled = !vm.fontBusy && !vm.backupBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("保存")
            }
            TextButton(onClick = { draft = null; vm.saveDisplayFont(null) }, enabled = !vm.fontBusy && !vm.backupBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("恢复默认")
            }
            TextButton(
                onClick = { deleting = deletable?.file },
                enabled = deletable != null && !vm.fontBusy && !vm.backupBusy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("删除字体")
            }
        }
        vm.fontResult?.let { Text(it, color = if (vm.fontFailed) palette.error else palette.contentSecondary, fontSize = 12.sp) }
        Text("当前使用：${settings.displayFonts.firstOrNull { it.file == settings.displayFont }?.name ?: "系统默认"}", color = palette.contentSecondary, fontSize = 12.sp)
        Text("内置预设、导入的字体和当前选择都会跟着完整备份。没有相应字形的文字会使用手机的默认字体。", color = palette.contentSecondary, fontSize = 12.sp)
    }

    if (deletingFont != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除字体？") },
            text = {
                Text(
                    "确定删除「${deletingFont.name}」吗？字体文件会从这台手机移除；如果它正是当前使用的字体，会自动恢复为系统默认。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !vm.fontBusy && !vm.backupBusy,
                    onClick = {
                        val file = deletingFont.file
                        deleting = null
                        vm.deleteDisplayFont(deletingFont) {
                            if (draft == file) draft = null
                        }
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("取消")
                }
            },
        )
    }
}
