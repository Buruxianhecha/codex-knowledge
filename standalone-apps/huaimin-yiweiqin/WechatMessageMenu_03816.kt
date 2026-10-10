package com.cleo.cleos.ui.chat

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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

/** All existing message handlers remain owned by MessageBubble. Only visuals change here. */
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

internal const val MESSAGE_MENU_COLUMNS = 2
private val GlassBlue = Color(0xFF15314F)
private val GlassNavy = Color(0xFF0B2039)
private val IcyLight = Color(0xFF9FD8FF)
private val WhiteInk = Color(0xFFF3F8FF)
private val DeleteInk = Color(0xFFF19A9F)
private val PanelShape = RoundedCornerShape(27.dp)
private val TileShape = RoundedCornerShape(19.dp)

/** Clamp floating menu to screen: handles first/last messages and both bubble directions. */
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
        val above = anchorBounds.top - gapPx
        val below = windowSize.height - anchorBounds.bottom - gapPx
        val showAbove = when {
            preferAbove && above >= popupContentSize.height -> true
            !preferAbove && below >= popupContentSize.height -> false
            above >= popupContentSize.height -> true
            below >= popupContentSize.height -> false
            else -> above > below
        }
        val yWanted = if (showAbove) anchorBounds.top - popupContentSize.height - gapPx
                      else anchorBounds.bottom + gapPx
        val maxY = (windowSize.height - popupContentSize.height - edgePx).coerceAtLeast(edgePx)
        return IntOffset(x, yWanted.coerceIn(edgePx, maxY))
    }
}

/**
 * Frosted navy / ice glass with separate translucent tiles.
 * The two-column icon-left + title-right style follows the app's blue glass theme.
 * No original action or availability condition is altered.
 */
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
    val config = LocalConfiguration.current
    val maxWidth = (config.screenWidthDp - 22).coerceAtLeast(210).dp
    val width = minOf(366.dp, maxWidth)
    val maxHeight = (config.screenHeightDp * 0.65f).dp.coerceAtLeast(180.dp)
    val edge = with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 4.dp.roundToPx() }
    val provider = remember(mine, preferAbove, edge, gap) {
        ChatMenuPositionProvider(mine, preferAbove, edge, gap)
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true,
        ),
    ) {
        Column(Modifier.width(width)) {
            if (!preferAbove) GlassArrow(up = true, mine = mine)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(13.dp, PanelShape)
                    .clip(PanelShape)
                    // Diffused layered color and a restrained luminous border mimic the
                    // existing blue liquid-glass theme without blurring icon/text content.
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                GlassBlue.copy(alpha = 0.95f),
                                Color(0xFF17516B).copy(alpha = 0.88f),
                                GlassNavy.copy(alpha = 0.91f),
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.53f),
                                IcyLight.copy(alpha = 0.23f),
                                Color.White.copy(alpha = 0.09f),
                            ),
                        ),
                        shape = PanelShape,
                    ),
            ) {
                // Blur is confined to the internal light diffusion layer; buttons stay sharp.
                Box(
                    Modifier
                        .matchParentSize()
                        .blur(21.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    IcyLight.copy(alpha = 0.20f),
                                    Color(0xFF39B9D6).copy(alpha = 0.08f),
                                    Color(0xFF06152C).copy(alpha = 0.12f),
                                ),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 11.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (reactionContent != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            reactionContent()
                        }
                    }
                    actions.chunked(MESSAGE_MENU_COLUMNS).forEach { pair ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { item ->
                                GlassActionTile(item, Modifier.weight(1f))
                            }
                            repeat(MESSAGE_MENU_COLUMNS - pair.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (preferAbove) GlassArrow(up = false, mine = mine)
        }
    }
}

@Composable
private fun GlassActionTile(item: MessageMenuAction, modifier: Modifier = Modifier) {
    val destructive = item.glyph == MessageMenuGlyph.DELETE
    val ink = if (destructive) DeleteInk else WhiteInk
    Row(
        modifier
            .height(59.dp)
            .clip(TileShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF8EB7E0).copy(alpha = 0.20f),
                        Color(0xFF2F6085).copy(alpha = 0.30f),
                        Color(0xFF0B2945).copy(alpha = 0.15f),
                    ),
                ),
            )
            .border(
                0.7.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.07f),
                    ),
                ),
                TileShape,
            )
            .clickable(onClick = item.onClick)
            .semantics { contentDescription = item.label }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = glyphIcon(item.glyph),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(23.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = item.label,
            color = ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small frosted pointer toward the originating message. */
@Composable
private fun GlassArrow(up: Boolean, mine: Boolean) {
    Box(Modifier.fillMaxWidth().height(9.dp)) {
        Canvas(
            modifier = Modifier
                .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 27.dp)
                .size(width = 17.dp, height = 9.dp),
        ) {
            val tri = Path().apply {
                if (up) {
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
            drawPath(tri, GlassBlue, style = Fill)
        }
    }
}
