package com.cleo.cleos.data

import android.graphics.Typeface
import java.io.File

/** Native decoding is checked on import, selection and load; broken fonts cannot crash a page. */
fun displayTypeface(file: File): Typeface {
    DisplayFonts.validateFile(file)
    return try { Typeface.createFromFile(file) }
    catch (_: Exception) { throw DisplayFontException("手机无法读取这个字体，请换一个 TTF 或 OTF 文件") }
}
