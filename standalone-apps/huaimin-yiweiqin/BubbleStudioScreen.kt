package com.cleo.cleos.ui.bubbles

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleo.cleos.data.AppSettings
import com.cleo.cleos.data.BubblePresets
import com.cleo.cleos.data.BubbleSkin
import com.cleo.cleos.data.BubbleStudioCodec
import com.cleo.cleos.data.BubbleStudioConfig
import com.cleo.cleos.glass.GlassIconButton
import com.cleo.cleos.glass.GlassShape
import com.cleo.cleos.glass.GlassSurface
import com.cleo.cleos.glass.LocalGlassPalette
import com.cleo.cleos.ui.common.GlassPage
import com.cleo.cleos.ui.common.GlassTopBar
import com.cleo.cleos.ui.common.TopBarHeight
import com.cleo.cleos.ui.common.appContainer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Fixed, real renderer preview above independently scrollable controls.
 * Changes are shown immediately while dragging and saved to DataStore when released.
 */
@Composable
fun BubbleStudioScreen(onBack: () -> Unit) {
    val c = appContainer()
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    val settings by remember { c.settings.settings }.collectAsStateWithLifecycle(AppSettings())
    val companions by remember { c.companions.all }.collectAsStateWithLifecycle(emptyList())
    val config = remember(settings.bubbleStudio) { BubbleStudioCodec.decode(settings.bubbleStudio) }
    var key by remember { mutableStateOf("me") }
    val selected = when (key) {
        "me" -> config.mine
        "ta" -> config.defaultTa
        else -> config.perCompanion[key.removePrefix("ta:")] ?: config.defaultTa
    }
    var draft by remember(key) { mutableStateOf(selected) }
    LaunchedEffect(config, key) { draft = selected }
    var sample by remember { mutableStateOf("短句") }
    var presetName by remember { mutableStateOf("") }
    var colorTarget by remember { mutableStateOf("primary") }
    fun save(next: BubbleStudioConfig) {
        scope.launch {
            c.settings.update { old ->
                val latest = BubbleStudioCodec.decode(old.bubbleStudio)
                // Apply only this edit over the latest preference snapshot.
                val merged = when {
                    !next.enabled && config.enabled -> latest.copy(enabled = false)
                    next.enabled && !config.enabled && next == config.copy(enabled = true) -> latest.copy(enabled = true)
                    else -> next
                }
                old.copy(bubbleStudio = BubbleStudioCodec.encode(merged))
            }
        }
    }
    fun apply(skin: BubbleSkin) {
        draft = skin.safe()
        scope.launch {
            c.settings.update { old ->
                val latest = BubbleStudioCodec.decode(old.bubbleStudio)
                old.copy(bubbleStudio = BubbleStudioCodec.encode(latest.withSkin(key, skin)))
            }
        }
    }
    fun saveDraft() { apply(draft) }

    GlassPage(overlay = { background ->
        GlassTopBar(
            title = "气泡实验室",
            subtitle = "实时预览 · 独立调节 · 一键保存",
            backdrop = background,
            leading = { GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, background) },
        )
    }) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(top = TopBarHeight + 6.dp, start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = GlassShape.Rounded(24.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("实时气泡预览", color = palette.content, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (item in listOf("短句", "长句", "语音", "群聊")) {
                            FilterChip(selected = sample == item, onClick = { sample = item }, label = {
                                Text(item, fontSize = 11.sp)
                            })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        BubbleSurface(mine = false,
                            preview = if (key == "me") config.defaultTa else draft,
                        ) {
                            val text = when (sample) {
                                "长句" -> "慢慢说也没关系。我会认真听你讲完，不需要急着找到答案。"
                                "语音" -> "▶  0:18   ━━━━━━━"
                                "群聊" -> "小艺：刚才说到哪里啦？"
                                else -> "今天过得怎么样？ 🌿"
                            }
                            Text(text, color = bubbleTextColor(false, preview = if (key == "me") config.defaultTa else draft),
                                fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        BubbleSurface(mine = true, preview = if (key == "me") draft else config.mine) {
                            Text(if (sample == "语音") "▶  0:08  ━━━" else "有人回应，夜便不长。",
                                color = bubbleTextColor(true, preview = if (key == "me") draft else config.mine),
                                fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                    Text("调节时立即预览；滑动结束后保存。文字颜色自动适配深浅背景。",
                        color = palette.contentSecondary, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StudioPanel("当前样式") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (config.enabled) "已应用到聊天" else "尚未启用，保留原有气泡", color = palette.content, fontSize = 13.sp)
                        Switch(checked = config.enabled, onCheckedChange = { enabled ->
                            scope.launch {
                                c.settings.update { current ->
                                    val existing = BubbleStudioCodec.decode(current.bubbleStudio)
                                    current.copy(bubbleStudio = BubbleStudioCodec.encode(existing.copy(enabled = enabled)))
                                }
                            }
                        })
                    }
                    Text("选择要编辑的对象：不同角色使用不同气泡，重命名角色不会丢失设置。",
                        color = palette.contentSecondary, fontSize = 12.sp)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = key == "me", onClick = { key = "me" }, label = { Text("我") })
                        FilterChip(selected = key == "ta", onClick = { key = "ta" }, label = { Text("所有 AI 默认") })
                        companions.forEach { ta ->
                            val role = "ta:${ta.id}"
                            FilterChip(selected = key == role, onClick = { key = role },
                                label = { Text(ta.name.trim().ifEmpty { "TA" }.take(10)) })
                        }
                    }
                    if (key.startsWith("ta:")) {
                        Text("此角色的单聊与群聊都采用独立气泡风格。",
                            color = palette.contentSecondary, fontSize = 12.sp)
                    }
                }
                StudioPanel("精选主题 · 一键应用") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (BubblePresets.all + config.saved).forEach { (name, style) ->
                            FilterChip(selected = draft == style, onClick = { apply(style) },
                                label = { Text(name) })
                        }
                    }
                }
                StudioPanel("颜色与材质") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !draft.gradient,
                            onClick = { apply(draft.copy(gradient = false)) }, label = { Text("纯色玻璃") })
                        FilterChip(selected = draft.gradient,
                            onClick = { apply(draft.copy(gradient = true)) }, label = { Text("渐变玻璃") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = colorTarget == "primary",
                            onClick = { colorTarget = "primary" }, label = { Text("主颜色") })
                        if (draft.gradient) FilterChip(selected = colorTarget == "second",
                            onClick = { colorTarget = "second" }, label = { Text("渐变末端") })
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val colors = listOf(0xFF126D94,0xFF253F52,0xFF2A5545,0xFF9AB6BF,0xFFF9F3E9,0xFFE8B2B9,0xFF34393D,0xFF5B659C)
                        colors.forEach { argb ->
                            val color = Color(argb.toInt())
                            OutlinedButton(onClick = {
                                apply(if (colorTarget == "primary") draft.copy(startArgb = color.toArgb())
                                else draft.copy(endArgb = color.toArgb()))
                            }) {
                                Box(Modifier.width(18.dp).height(18.dp)
                                    .background(color, RoundedCornerShape(6.dp)))
                            }
                        }
                    }
                    val argb = if (colorTarget == "primary") draft.startArgb else draft.endArgb
                    fun channel(shift: Int) = (argb ushr shift) and 255
                    fun setChannel(shift: Int, v: Float) {
                        val mask = 255 shl shift
                        val replacement = (v.roundToInt().coerceIn(0,255) shl shift)
                        val next = (argb and mask.inv()) or replacement
                        draft = (if (colorTarget == "primary") draft.copy(startArgb = next)
                            else draft.copy(endArgb = next)).safe()
                    }
                    StudioSlider("红色 R", channel(16).toFloat(), 0f..255f, { setChannel(16,it) }, ::saveDraft)
                    StudioSlider("绿色 G", channel(8).toFloat(), 0f..255f, { setChannel(8,it) }, ::saveDraft)
                    StudioSlider("蓝色 B", channel(0).toFloat(), 0f..255f, { setChannel(0,it) }, ::saveDraft)
                    StudioSlider("玻璃浓度", draft.opacity, 0.65f..1f, { draft = draft.copy(opacity = it) }, ::saveDraft, "%")
                    StudioSlider("模糊强度", draft.blur, 0f..36f, { draft = draft.copy(blur = it) }, ::saveDraft, "dp")
                }
                StudioPanel("形状、轮廓与留白") {
                    StudioSlider("圆角", draft.radius, 6f..36f, { draft = draft.copy(radius = it) }, ::saveDraft, "dp")
                    StudioSlider("左右内边距", draft.paddingHorizontal, 6f..28f,
                        { draft = draft.copy(paddingHorizontal = it) }, ::saveDraft, "dp")
                    StudioSlider("上下内边距", draft.paddingVertical, 4f..20f,
                        { draft = draft.copy(paddingVertical = it) }, ::saveDraft, "dp")
                    StudioSlider("描边宽度", draft.rim, 0f..3f, { draft = draft.copy(rim = it) }, ::saveDraft, "dp")
                    StudioSlider("阴影浓度", draft.shadow, 0f..0.36f, { draft = draft.copy(shadow = it) }, ::saveDraft, "%")
                }
                StudioPanel("保存自己的预设") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = presetName, onValueChange = { presetName = it.take(24) },
                            label = { Text("给主题取个名字") },
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                        Button(enabled = presetName.isNotBlank(), onClick = {
                            val name = presetName
                            scope.launch {
                                c.settings.update { old ->
                                    val latest = BubbleStudioCodec.decode(old.bubbleStudio)
                                    old.copy(bubbleStudio = BubbleStudioCodec.encode(latest.savePreset(name, draft)))
                                }
                            }
                            presetName = ""
                        }) { Text("保存") }
                    }
                    Text("预设随应用完整备份一起导出与恢复，最多保存 24 套。",
                        fontSize = 12.sp, color = palette.contentSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (key.startsWith("ta:")) {
                                scope.launch {
                                    c.settings.update { old ->
                                        val p = BubbleStudioCodec.decode(old.bubbleStudio)
                                        old.copy(bubbleStudio = BubbleStudioCodec.encode(p.copy(
                                            perCompanion = p.perCompanion - key.removePrefix("ta:"))))
                                    }
                                }
                                draft = config.defaultTa
                            } else apply(if (key == "me") BubblePresets.nightMine else BubblePresets.nightTa)
                        }) { Text("恢复该对象默认样式") }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun StudioPanel(title: String, body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val palette = LocalGlassPalette.current
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = GlassShape.Rounded(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = palette.content, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            body()
        }
    }
}

@Composable
private fun StudioSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit, onRelease: () -> Unit, unit: String = "",
) {
    val palette = LocalGlassPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = palette.contentSecondary)
            val display = if (unit == "%") "${(value * 100).roundToInt()}%"
                else "${(value * 10).roundToInt() / 10f} $unit"
            Text(display, fontSize = 12.sp, color = palette.content)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), valueRange = range,
            onValueChange = onChange, onValueChangeFinished = onRelease)
    }
}
