package com.cleo.cleos.data

import android.content.res.AssetManager
import android.graphics.Typeface
import java.io.File

/** Native decoding is checked on import, selection and load; broken fonts cannot crash a page. */
fun displayTypeface(file: File): Typeface {
    DisplayFonts.validateFile(file)
    return try {
        Typeface.Builder(file).build()
            ?: throw DisplayFontException("手机无法读取这个字体，请换一个 TTF 或 OTF 文件")
    }
    catch (_: Exception) { throw DisplayFontException("手机无法读取这个字体，请换一个 TTF 或 OTF 文件") }
}

/**
 * Materialise the bundled 萌系80 asset into the same private store used by imported fonts.
 * Keeping one storage path means selection, global typography and backup do not need a special case.
 */
fun ensureBundledDisplayFont(assets: AssetManager, directory: File): DisplayFont {
    if (!directory.isDirectory && !directory.mkdirs()) throw DisplayFontException("无法准备内置字体")
    val target = File(directory, DisplayFonts.BUNDLED_FILE)
    val valid = runCatching {
        DisplayFonts.validateFile(target)
        displayTypeface(target)
        true
    }.getOrDefault(false)
    if (valid) return DisplayFonts.BUNDLED

    val temp = File(directory, ".${DisplayFonts.BUNDLED_FILE}.tmp")
    try {
        assets.open(DisplayFonts.BUNDLED_ASSET).use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        DisplayFonts.validateFile(temp)
        displayTypeface(temp)
        if (target.exists() && !target.delete()) throw DisplayFontException("无法更新内置字体")
        if (!temp.renameTo(target)) throw DisplayFontException("无法保存内置字体")
    } finally {
        temp.delete()
    }
    return DisplayFonts.BUNDLED
}

/** Installs each real font independently, so a missing legacy font never hides the new presets.
 * Assets are bundled in the APK; no network or external storage is needed on the phone.
 * Only valid TTF files reach the selectable catalog. An existing file is kept unchanged.
 */
fun ensureExtraBundledDisplayFonts(assets: AssetManager, directory: File): List<DisplayFont> {
    if (!directory.isDirectory && !directory.mkdirs()) throw DisplayFontException("无法准备字体预设目录")
    return listOf(DisplayFonts.LONG_CANG, DisplayFonts.ZHI_MANG_XING).mapNotNull { font ->
        val asset = DisplayFonts.assetFor(font) ?: return@mapNotNull null
        runCatching {
            val target = File(directory, font.file)
            val ready = runCatching { DisplayFonts.validateFile(target); displayTypeface(target); true }
                .getOrDefault(false)
            if (!ready) {
                val temporary = File(directory, "." + font.file + ".install")
                try {
                    assets.open(asset).use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    DisplayFonts.validateFile(temporary)
                    displayTypeface(temporary)
                    if (target.exists() && !target.delete())
                        throw DisplayFontException("无法更新内置字体")
                    if (!temporary.renameTo(target))
                        throw DisplayFontException("无法完成内置字体安装")
                } finally {
                    temporary.delete()
                }
            }
            font
        }.getOrNull()
    }
}
