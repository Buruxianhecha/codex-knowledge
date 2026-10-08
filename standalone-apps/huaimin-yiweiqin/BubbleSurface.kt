package com.cleo.cleos.ui.bubbles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.cleo.cleos.data.BubbleSkin
import com.cleo.cleos.data.BubbleStudioConfig
import com.cleo.cleos.glass.GlassShape
import com.cleo.cleos.glass.GlassSurface
import com.cleo.cleos.glass.LocalGlassPalette
import com.cleo.cleos.glass.LocalWallpaperBackdrop
import com.cleo.cleos.glass.liquidGlass

/** Supplied only by ChatScreen; all ordinary app panels retain their normal glass style. */
val LocalBubbleStudio = compositionLocalOf { BubbleStudioConfig() }
val LocalBubbleCompanion = compositionLocalOf<Long?> { null }

@Composable
fun bubbleTextColor(mine: Boolean, companionId: Long? = LocalBubbleCompanion.current, preview: BubbleSkin? = null): Color {
    val palette = LocalGlassPalette.current
    val config = LocalBubbleStudio.current
    val skin = preview ?: if (config.enabled) config.skinFor(mine, companionId) else null
    if (skin == null) return if (mine) palette.mineContent else palette.content
    val light = Color(skin.startArgb).luminance() * (if (skin.gradient) 0.6f else 1f) +
        (if (skin.gradient) Color(skin.endArgb).luminance() * 0.4f else 0f)
    return if (light < 0.31f) Color.White else Color(0xFF20312B)
}

/**
 * Reusable chat bubble that keeps the existing GPU liquid-glass implementation.
 * The optional gradient is painted *above* the tinted glass at low opacity:
 * refraction/blur remain visible and text contrast stays protected.
 */
@Composable
fun BubbleSurface(
    mine: Boolean,
    modifier: Modifier = Modifier,
    companionId: Long? = LocalBubbleCompanion.current,
    preview: BubbleSkin? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalGlassPalette.current
    val config = LocalBubbleStudio.current
    val skin = preview ?: if (config.enabled) config.skinFor(mine, companionId) else null
    if (skin == null) {
        GlassSurface(
            modifier = modifier,
            style = if (mine) palette.bubbleMine else palette.bubble,
            shape = GlassShape.Rounded(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            content = content,
        )
        return
    }
    val safe = skin.safe()
    val base = if (mine) palette.bubbleMine else palette.bubble
    val shape = GlassShape.Rounded(safe.radius.dp)
    val start = Color(safe.startArgb)
    val end = Color(safe.endArgb)
    val glass = base.copy(
        tint = start.copy(alpha = safe.opacity.coerceAtLeast(0.65f)),
        blur = safe.blur.dp,
        rimWidth = safe.rim.dp,
        shadowAlpha = safe.shadow,
    )
    Box(
        modifier
            .liquidGlass(LocalWallpaperBackdrop.current, glass, shape)
            .clip(shape.shape)
            .then(
                if (safe.gradient) Modifier.background(
                    Brush.linearGradient(
                        listOf(start.copy(alpha = 0.19f), end.copy(alpha = 0.38f)),
                    ),
                ) else Modifier
            )
            .padding(horizontal = safe.paddingHorizontal.dp, vertical = safe.paddingVertical.dp),
        content = content,
    )
}
