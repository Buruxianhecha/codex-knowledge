package com.cleo.cleos.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.glass.Backdrop
import com.cleo.cleos.glass.GlassShape
import com.cleo.cleos.glass.LocalGlassPalette
import com.cleo.cleos.glass.liquidGlass

/** Checkbox occupies the same leading gutter for both left and right chat bubbles. */
@Composable
internal fun ChatMessageCheck(checked: Boolean, onClick: () -> Unit) {
    val color = LocalGlassPalette.current.accent
    Box(
        Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(23.dp)
                .background(if (checked) color else Color.Transparent, CircleShape)
                .border(
                    width = if (checked) 0.dp else 1.7.dp,
                    color = if (checked) Color.Transparent else Color.White.copy(alpha = 0.85f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = "已选中", tint = Color.White, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
internal fun ChatMultiSelectBottomBar(
    backdrop: Backdrop,
    bottomPadding: Dp,
    count: Int,
    busy: Boolean,
    onForward: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .liquidGlass(backdrop, palette.input, GlassShape.Rounded(27.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val enabled = count > 0 && !busy
        @Composable fun Command(label: String, symbol: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit, danger: Boolean = false) {
            val ink = if (danger) palette.error else palette.content
            Column(
                Modifier.weight(1f).clickable(enabled = enabled, onClick = action).padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(symbol, contentDescription = label, tint = ink.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(23.dp))
                Spacer(Modifier.height(3.dp))
                Text(label, color = ink.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 12.sp)
            }
        }
        Command("转发", Icons.Rounded.Send, onForward)
        Command("收藏", Icons.Rounded.Bookmark, onFavorite)
        Command("删除", Icons.Rounded.Delete, onDelete, danger = true)
    }
}

@Composable
internal fun ChooseForwardMode(onSeparate: () -> Unit, onMerge: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("转发聊天记录") },
        text = {
            Column {
                TextButton(onClick = onSeparate, modifier = Modifier.fillMaxWidth()) { Text("逐条转发") }
                TextButton(onClick = onMerge, modifier = Modifier.fillMaxWidth()) { Text("合并转发") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun ChooseForwardTarget(
    candidates: List<ConversationEntity>,
    sourceId: Long,
    count: Int,
    onTarget: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("转发 " + count + " 条消息到") },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (candidates.isEmpty()) Text("还没有可转发的其他会话")
                candidates.filter { it.id != sourceId }.forEach { conversation ->
                    TextButton(onClick = { onTarget(conversation.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (conversation.isGroup) "👥 " else "💬 ") +
                            (conversation.title?.takeIf { it.isNotBlank() } ?: "聊天 " + conversation.id))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
