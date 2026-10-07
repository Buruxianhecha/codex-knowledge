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
