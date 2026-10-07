package com.cleo.cleos.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import com.cleo.cleos.data.displayTypeface
import java.io.File

/** Null keeps each screen's original default (including the letters' serif style). */
val LocalDisplayFontFamily = compositionLocalOf<FontFamily?> { null }

@Composable
fun ProvideDisplayFont(file: File?, content: @Composable () -> Unit) {
    val selected = remember(file?.path, file?.lastModified()) {
        file?.let { runCatching { FontFamily(displayTypeface(it)) }.getOrNull() }
    }
    val family = selected ?: FontFamily.Default
    val base = MaterialTheme.typography
    val typography = remember(base, family) {
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = family),
            displayMedium = base.displayMedium.copy(fontFamily = family),
            displaySmall = base.displaySmall.copy(fontFamily = family),
            headlineLarge = base.headlineLarge.copy(fontFamily = family),
            headlineMedium = base.headlineMedium.copy(fontFamily = family),
            headlineSmall = base.headlineSmall.copy(fontFamily = family),
            titleLarge = base.titleLarge.copy(fontFamily = family),
            titleMedium = base.titleMedium.copy(fontFamily = family),
            titleSmall = base.titleSmall.copy(fontFamily = family),
            bodyLarge = base.bodyLarge.copy(fontFamily = family),
            bodyMedium = base.bodyMedium.copy(fontFamily = family),
            bodySmall = base.bodySmall.copy(fontFamily = family),
            labelLarge = base.labelLarge.copy(fontFamily = family),
            labelMedium = base.labelMedium.copy(fontFamily = family),
            labelSmall = base.labelSmall.copy(fontFamily = family),
        )
    }
    MaterialTheme(typography = typography) {
        CompositionLocalProvider(
            LocalDisplayFontFamily provides selected,
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = family),
            content = content,
        )
    }
}
