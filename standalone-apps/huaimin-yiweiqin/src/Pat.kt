package com.cleo.cleos.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleo.cleos.data.PatRecord
import com.cleo.cleos.data.Pats
import com.cleo.cleos.glass.LocalGlassPalette
import com.cleo.cleos.ui.settings.Chip
import kotlinx.coroutines.launch

/** What an avatar can do. [targetCompanionId] disambiguates which TA was patted in a group. */
internal class PatActions(val pat: (ai: Boolean, targetCompanionId: Long?) -> Unit, val edit: () -> Unit)

internal val LocalPat = staticCompositionLocalOf<PatActions?> { null }

private val WIGGLE = listOf(-14f, 12f, -8f, 5f, 0f)

/**
 * Double tap to pat (the way WeChat does it): the avatar wiggles, and a long press on the TA's
 * opens what the pat says and whether the phone buzzes. Without [LocalPat], an avatar is a picture.
 */
@Composable
internal fun Modifier.pattable(ai: Boolean, targetCompanionId: Long? = null): Modifier {
    val wiggle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val actions = LocalPat.current ?: return this
    return this
        .graphicsLayer { rotationZ = wiggle.value }
        .pointerInput(ai, targetCompanionId, actions) {
            detectTapGestures(
                onDoubleTap = {
                    actions.pat(ai, targetCompanionId)
                    scope.launch { for (angle in WIGGLE) wiggle.animateTo(angle, tween(55)) }
                },
                onLongPress = { if (ai) actions.edit() },
            )
        }
}

/** The line a pat leaves: small, grey and in the middle; one that is pat again pops. */
@Composable
internal fun PatLine(record: PatRecord?, aiName: String) {
    if (record == null) return
    val palette = LocalGlassPalette.current
    val pop = remember { Animatable(1f) }
    // Only one more pat landing on a line already on screen pops it: scrolled back to, a run of
    // them from an hour ago is just a line and stays still.
    var seen by remember { mutableIntStateOf(record.count) }
    LaunchedEffect(record.count) {
        if (record.count > seen) {
            pop.snapTo(1.2f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        seen = record.count
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(
            Pats.line(record, aiName),
            color = palette.contentSecondary,
            fontSize = 12.sp,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .background(palette.contentSecondary.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** What the pat says (the verb and what follows the name) and whether the phone buzzes. */
@Composable
internal fun PatDialog(
    aiName: String,
    verb: String,
    suffix: String,
    buzz: Boolean,
    onSave: (verb: String, suffix: String, buzz: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    var chosenVerb by remember { mutableStateOf(verb) }
    var text by remember { mutableStateOf(suffix) }
    var shake by remember { mutableStateOf(buzz) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("拍一拍") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("双击 TA 头像拍一下，停手后 TA 会直接回应；连续拍几下会合并成一轮再回复。", color = palette.contentSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pats.VERBS.forEach { v -> Chip(v, selected = chosenVerb == v) { chosenVerb = v } }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(Pats.SUFFIX_MAX) },
                    singleLine = true,
                    label = { Text("名字后面的话") },
                    placeholder = { Text("比如 的小脑袋，可以留空") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("的小脑袋", "的脸蛋", "的肩膀").forEach { s -> Chip(s, selected = text == s) { text = s } }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { shake = !shake },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("拍的时候震一下")
                    Switch(checked = shake, onCheckedChange = { shake = it })
                }
                Text(
                    "会显示成：" + Pats.line(PatRecord(Pats.AI, 1, Pats.cleanVerb(chosenVerb), Pats.cleanSuffix(text)), aiName),
                    color = palette.contentSecondary,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosenVerb, text, shake) }) { Text("好") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("算了") } },
    )
}
