package com.cleo.cleos.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import com.cleo.cleos.data.displayTypeface
import java.io.File

/** Changes default text throughout the app, including explicit chat/input TextStyles. */
private class DisplayFontResolver(
    val base: FontFamily.Resolver,
    private val selected: FontFamily?,
) : FontFamily.Resolver by base {
    override fun resolve(
        fontFamily: FontFamily?,
        fontWeight: FontWeight,
        fontStyle: FontStyle,
        fontSynthesis: FontSynthesis,
    ): State<Any> = base.resolve(
        if (fontFamily == null || fontFamily == FontFamily.Default) selected else fontFamily,
        fontWeight, fontStyle, fontSynthesis,
    )
}

@Composable
fun ProvideDisplayFont(file: File?, content: @Composable () -> Unit) {
    val parent = LocalFontFamilyResolver.current
    // A local preview of the default must bypass the app's saved custom font as well.
    val base = (parent as? DisplayFontResolver)?.base ?: parent
    val family = remember(file?.path, file?.lastModified()) {
        file?.let { runCatching { FontFamily(displayTypeface(it)) }.getOrNull() }
    }
    val resolver = remember(base, family) { DisplayFontResolver(base, family) }
    CompositionLocalProvider(LocalFontFamilyResolver provides resolver, content = content)
}
