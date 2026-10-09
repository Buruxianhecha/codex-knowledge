package com.cleo.cleos.data

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class DisplayFont(val file: String, val name: String)

class DisplayFontException(message: String) : Exception(message)

/** Imported files are immutable, private copies; their names also survive a full backup. */
object DisplayFonts {
    const val CATALOG_KEY = "display_fonts"
    const val SELECTED_KEY = "display_font"
    /** Three fixed bundled identities plus up to 20 user-imported fonts. */
    const val MAX_IMPORTED_FONTS = 20
    const val MAX_FONTS = MAX_IMPORTED_FONTS + 3
    const val MAX_BYTES = 32 * 1024 * 1024

    /** Stable private-store name used by the bundled 萌系80 preset. */
    const val BUNDLED_FILE = "font-00000000-0000-0000-0000-000000000080.ttf"
    const val BUNDLED_NAME = "萌系80"
    const val BUNDLED_ASSET = "display_fonts/mengxi80.ttf"
    val BUNDLED = DisplayFont(BUNDLED_FILE, BUNDLED_NAME)
    /** Fixed UUID-like names survive reinstalls, upgrades and backup restore. */
    const val LONG_CANG_FILE = "font-00000000-0000-0000-0000-000000000081.ttf"
    const val ZHI_MANG_XING_FILE = "font-00000000-0000-0000-0000-000000000082.ttf"
    const val LONG_CANG_ASSET = "display_fonts/LongCang-Regular.ttf"
    const val ZHI_MANG_XING_ASSET = "display_fonts/ZhiMangXing-Regular.ttf"
    val LONG_CANG = DisplayFont(LONG_CANG_FILE, "龙藏体 · Long Cang")
    val ZHI_MANG_XING = DisplayFont(ZHI_MANG_XING_FILE, "志莽行书 · Zhi Mang Xing")
    val BUILT_INS: List<DisplayFont> = listOf(BUNDLED, LONG_CANG, ZHI_MANG_XING)
    fun assetFor(font: DisplayFont): String? = when (font.file) {
        LONG_CANG_FILE -> LONG_CANG_ASSET
        ZHI_MANG_XING_FILE -> ZHI_MANG_XING_ASSET
        BUNDLED_FILE -> BUNDLED_ASSET
        else -> null
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val filename = Regex("font-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(ttf|otf)")

    fun isFontName(name: String) = filename.matches(name)

    fun isBundled(font: DisplayFont) = BUILT_INS.any { it.file == font.file }
    fun importedCount(fonts: List<DisplayFont>) = fonts.count { !isBundled(it) }

    /** Remove only a user-imported font; built-in presets are permanent. */
    fun removeImported(
        fonts: List<DisplayFont>,
        selected: String?,
        file: String,
    ): Pair<List<DisplayFont>, String?> {
        val target = fonts.firstOrNull { it.file == file } ?: return fonts to selected
        if (isBundled(target)) throw DisplayFontException("内置字体预设不能删除")
        return fonts.filterNot { it.file == file } to selected?.takeUnless { it == file }
    }

    fun encode(fonts: List<DisplayFont>): String {
        validateCatalog(fonts)
        return json.encodeToString(fonts)
    }

    fun decode(raw: String?): List<DisplayFont> {
        if (raw.isNullOrBlank()) return emptyList()
        if (raw.length > 32 * 1024) throw DisplayFontException("字体列表无效")
        return try {
            json.decodeFromString<List<DisplayFont>>(raw).also(::validateCatalog)
        } catch (e: DisplayFontException) {
            throw e
        } catch (_: Exception) {
            throw DisplayFontException("字体列表无效")
        }
    }

    private fun validateCatalog(fonts: List<DisplayFont>) {
        if (fonts.size > MAX_FONTS || fonts.map { it.file }.distinct().size != fonts.size || fonts.any {
                !isFontName(it.file) || it.name.isBlank() || it.name.length > 80 || it.name.any(Char::isISOControl)
            }) throw DisplayFontException("字体列表无效")
    }

    /** Checks the SFNT header and every table's bounds before Android sees an imported file. */
    fun validateFile(file: File): String {
        if (!file.isFile || file.length() !in 12L..MAX_BYTES.toLong()) {
            throw DisplayFontException("字体文件无效，单个文件需小于 32 MB")
        }
        RandomAccessFile(file, "r").use { input ->
            val extension = when (input.readInt()) {
                0x00010000 -> "ttf"
                0x4f54544f -> "otf"
                else -> throw DisplayFontException("请选择 TTF 或 OTF 字体文件")
            }
            val tables = input.readUnsignedShort()
            val directoryEnd = 12L + tables * 16L
            if (tables !in 1..512 || directoryEnd > input.length()) throw DisplayFontException("字体文件不完整")
            input.seek(12)
            repeat(tables) {
                input.readInt() // tag
                input.readInt() // checksum
                val offset = input.readInt().toLong() and 0xffffffffL
                val length = input.readInt().toLong() and 0xffffffffL
                if ((length > 0 && offset < directoryEnd) || offset + length > input.length()) {
                    throw DisplayFontException("字体文件不完整")
                }
            }
            return extension
        }
    }

    /** A failed copy or native validation leaves neither a half-written file nor a catalog item. */
    fun importFont(
        directory: File,
        sourceName: String?,
        open: () -> InputStream,
        validateNative: (File) -> Unit,
    ): DisplayFont {
        if (!directory.isDirectory && !directory.mkdirs()) throw DisplayFontException("无法保存字体文件")
        val token = UUID.randomUUID().toString()
        val temp = File(directory, ".font-$token.tmp")
        try {
            open().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var size = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > MAX_BYTES) throw DisplayFontException("单个字体文件不能超过 32 MB")
                        output.write(buffer, 0, count)
                    }
                }
            }
            val extension = validateFile(temp)
            validateNative(temp)
            val final = File(directory, "font-$token.$extension")
            if (!temp.renameTo(final)) throw DisplayFontException("无法保存字体文件")
            val name = sourceName.orEmpty().substringAfterLast('/').substringAfterLast('\\')
                .substringBeforeLast('.').filterNot(Char::isISOControl).trim().take(80)
                .ifBlank { "自定义字体" }
            return DisplayFont(final.name, name)
        } finally {
            temp.delete()
        }
    }

    /** Missing font bytes fall back to the default; unsafe or malformed metadata rejects restore. */
    internal fun restoredPreferences(
        preferences: Map<String, BackupPreference>,
        exists: (String) -> Boolean,
    ): Map<String, BackupPreference> {
        if (CATALOG_KEY !in preferences && SELECTED_KEY !in preferences) return preferences
        val fonts = try { decode(preferences[CATALOG_KEY]?.value?.jsonPrimitive?.content) }
            catch (_: Exception) { throw BackupException("备份里的字体列表无效，没有恢复") }
        val available = fonts.filter { exists(it.file) }
        return preferences.toMutableMap().apply {
            this[CATALOG_KEY] = BackupPreference("string", JsonPrimitive(encode(available)))
            val selected = this[SELECTED_KEY]?.value?.jsonPrimitive?.content
            if (selected != null && available.none { it.file == selected }) remove(SELECTED_KEY)
        }
    }
}
