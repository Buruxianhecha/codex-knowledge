package com.cleo.cleos.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleo.cleos.data.RecallDisplayText
import com.cleo.cleos.glass.GlassShape
import com.cleo.cleos.glass.GlassSurface
import com.cleo.cleos.glass.LocalGlassPalette

/** Setting lives under Settings > Chat and is shared by single and group conversations. */
@Composable
internal fun RecallMessageSettingsPage(vm: SettingsViewModel) {
    val palette = LocalGlassPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val current = RecallDisplayText.clean(settings.recallDisplayText)
    val preview = if (editing) RecallDisplayText.clean(draft) else current

    Text("预览", color = palette.contentSecondary, fontSize = 13.sp)
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassShape.Rounded(20.dp),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = palette.accentContent)
            Spacer(Modifier.width(10.dp))
            Text(preview, fontSize = 14.sp, color = palette.content)
        }
    }

    Section("选择撤回消息") {
        RecallDisplayText.presets.forEach { option ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    editing = false
                    vm.setRecallDisplayText(option)
                }.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    option.removePrefix(RecallDisplayText.DEFAULT)
                        .removePrefix("，")
                        .ifEmpty { "无（默认）" },
                    modifier = Modifier.weight(1f),
                    color = palette.content,
                    fontSize = 15.sp,
                )
                RadioButton(
                    selected = !editing && current == option,
                    onClick = { editing = false; vm.setRecallDisplayText(option) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable {
                if (!editing) draft = if (current in RecallDisplayText.presets) "" else current
                editing = true
            }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("自定义文字", modifier = Modifier.weight(1f), color = palette.content, fontSize = 15.sp)
            RadioButton(
                selected = editing || current !in RecallDisplayText.presets,
                onClick = {
                    if (!editing) draft = if (current in RecallDisplayText.presets) "" else current
                    editing = true
                },
            )
        }
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(RecallDisplayText.MAX_LENGTH) },
                label = { Text("输入完整的撤回提示文字") },
                supportingText = { Text("最多 80 字；只显示一行。") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editing = false }) { Text("取消") }
                TextButton(onClick = {
                    vm.setRecallDisplayText(draft)
                    editing = false
                }) { Text("保存") }
            }
        }
    }
    Text(
        "仅影响之后撤回的消息；以前的撤回提示不会被改写。撤回会照常清除原文和附件，AI 收到的仍是撤回事件，不会看到被撤回的原文。",
        color = palette.contentSecondary,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}
