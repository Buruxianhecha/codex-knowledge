package com.cleo.cleos.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Message actions retain existing callbacks; only their presentation changes. */
internal enum class MessageMenuGlyph { FAVORITE, COPY, READ, QUOTE, TRANSCRIBE, RETRY, EDIT, RECALL, DELETE }

internal data class MessageMenuAction(
    val label: String,
    val glyph: MessageMenuGlyph,
    val onClick: () -> Unit,
)

private fun glyphIcon(glyph: MessageMenuGlyph): ImageVector = when (glyph) {
    MessageMenuGlyph.FAVORITE -> Icons.Rounded.Bookmark
    MessageMenuGlyph.COPY -> Icons.Rounded.ContentCopy
    MessageMenuGlyph.READ -> Icons.Rounded.VolumeUp
    MessageMenuGlyph.QUOTE -> Icons.Rounded.FormatQuote
    MessageMenuGlyph.TRANSCRIBE -> Icons.Rounded.Refresh
    MessageMenuGlyph.RETRY -> Icons.Rounded.Refresh
    MessageMenuGlyph.EDIT -> Icons.Rounded.Edit
    MessageMenuGlyph.RECALL -> Icons.Rounded.Undo
    MessageMenuGlyph.DELETE -> Icons.Rounded.Delete
}

internal const val MESSAGE_MENU_COLUMNS = 3
private val MenuBackground = Color(0xFF45464C)
private val MenuInk = Color.White

/** Window-relative positioning, clamped on both axes instead of covering offscreen messages. */
internal class ChatMenuPositionProvider(
    private val mine: Boolean,
    private val preferAbove: Boolean,
    private val edgePx: Int,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - edgePx).coerceAtLeast(edgePx)
        val preferredX = if (mine) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val x = preferredX.coerceIn(edgePx, maxX)

        val roomAbove = anchorBounds.top - gapPx
        val roomBelow = windowSize.height - anchorBounds.bottom - gapPx
        val showAbove = when {
            preferAbove && roomAbove >= popupContentSize.height -> true
            !preferAbove && roomBelow >= popupContentSize.height -> false
            roomAbove >= popupContentSize.height -> true
            roomBelow >= popupContentSize.height -> false
            else -> roomAbove > roomBelow
        }
        val preferredY = if (showAbove) anchorBounds.top - popupContentSize.height - gapPx
                         else anchorBounds.bottom + gapPx
        val maxY = (windowSize.height - popupContentSize.height - edgePx).coerceAtLeast(edgePx)
        return IntOffset(x, preferredY.coerceIn(edgePx, maxY))
    }
}

/** Compact 3 x up-to-2 grid, with the existing AI emoji picker above the actions. */
@Composable
internal fun WechatMessagePopup(
    expanded: Boolean,
    mine: Boolean,
    preferAbove: Boolean,
    actions: List<MessageMenuAction>,
    onDismiss: () -> Unit,
    reactionContent: (@Composable () -> Unit)? = null,
) {
    if (!expanded || actions.isEmpty()) return
    val density = LocalDensity.current
    val screen = LocalConfiguration.current
    val menuWidth = minOf(310.dp, (screen.screenWidthDp - 24).coerceAtLeast(180).dp)
    val edgePx = with(density) { 8.dp.roundToPx() }
    val gapPx = with(density) { 3.dp.roundToPx() }
    val provider = remember(mine, preferAbove, edgePx, gapPx) {
        ChatMenuPositionProvider(mine, preferAbove, edgePx, gapPx)
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Column(Modifier.width(menuWidth)) {
            if (!preferAbove) PopupArrow(tipUp = true, mine = mine)
            Surface(
                modifier = Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MenuBackground,
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                    if (reactionContent != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            reactionContent()
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
                            thickness = 1.dp,
                            color = Color.White.copy(alpha = 0.18f),
                        )
                    }
                    actions.chunked(MESSAGE_MENU_COLUMNS).forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color.White.copy(alpha = 0.13f),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            row.forEach { item ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(66.dp)
                                        .clickable(onClick = item.onClick)
                                        .semantics { contentDescription = item.label },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Spacer(Modifier.height(7.dp))
                                    Icon(
                                        imageVector = glyphIcon(item.glyph),
                                        contentDescription = null,
                                        tint = MenuInk,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        text = item.label,
                                        color = MenuInk,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            repeat(MESSAGE_MENU_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (preferAbove) PopupArrow(tipUp = false, mine = mine)
        }
    }
}

@Composable
private fun PopupArrow(tipUp: Boolean, mine: Boolean) {
    Box(Modifier.fillMaxWidth().height(9.dp)) {
        Canvas(
            modifier = Modifier
                .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 23.dp)
                .size(width = 17.dp, height = 9.dp),
        ) {
            val triangle = Path().apply {
                if (tipUp) {
                    moveTo(size.width / 2f, 0f)
                    lineTo(0f, size.height)
                    lineTo(size.width, size.height)
                } else {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                }
                close()
            }
            drawPath(path = triangle, color = MenuBackground, style = Fill)
        }
    }
}
