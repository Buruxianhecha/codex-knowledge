package com.cleo.cleos.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class DisplayFontsTest {
    private val a = DisplayFont("font-00000000-0000-0000-0000-000000000001.ttf", "青年手写")
    private val b = DisplayFont("font-00000000-0000-0000-0000-000000000002.otf", "圆体")
    private fun fixture(magic: Int = 0x00010000) = ByteBuffer.allocate(32)
        .putInt(magic).putShort(1.toShort()).putShort(0.toShort()).putShort(0.toShort()).putShort(0.toShort())
        .putInt(0x636d6170).putInt(0).putInt(28).putInt(4).putInt(0).array()
    private fun catalog(fonts: List<DisplayFont>, selected: String? = a.file): Map<String, BackupPreference> = buildMap {
        put(DisplayFonts.CATALOG_KEY, BackupPreference("string", JsonPrimitive(DisplayFonts.encode(fonts))))
        if (selected != null) put(DisplayFonts.SELECTED_KEY, BackupPreference("string", JsonPrimitive(selected)))
    }
    private inline fun <T> temporary(block: (File) -> T): T {
        val dir = Files.createTempDirectory("fonts-test").toFile()
        try { return block(dir) } finally { dir.deleteRecursively() }
    }

    @Test fun namesAndMultipleFontsRoundTrip() {
        assertEquals(listOf(a, b), DisplayFonts.decode(DisplayFonts.encode(listOf(a, b))))
        assertEquals(emptyList<DisplayFont>(), DisplayFonts.decode(null))
    }

    @Test fun unsafeFileNamesRejected() {
        assertThrows(DisplayFontException::class.java) { DisplayFonts.encode(listOf(a.copy(file = "../../key.ttf"))) }
        assertThrows(DisplayFontException::class.java) { DisplayFonts.decode("[{\"file\":\"/tmp/font.ttf\",\"name\":\"font\"}]") }
    }

    @Test fun duplicateNamesAndOversizedListsRejected() {
        assertThrows(DisplayFontException::class.java) { DisplayFonts.encode(listOf(a, a)) }
        val fonts = (1..DisplayFonts.MAX_FONTS + 1).map { a.copy(file = "font-00000000-0000-0000-0000-${it.toString().padStart(12, '0')}.ttf") }
        assertThrows(DisplayFontException::class.java) { DisplayFonts.encode(fonts) }
    }

    @Test fun restoreKeepsAvailableFontsAndChosenFile() {
        val result = DisplayFonts.restoredPreferences(catalog(listOf(a, b))) { it == a.file }
        assertEquals(listOf(a), DisplayFonts.decode((result[DisplayFonts.CATALOG_KEY]!!.value as JsonPrimitive).content))
        assertEquals(JsonPrimitive(a.file), result[DisplayFonts.SELECTED_KEY]!!.value)
    }

    @Test fun missingSelectedFontFallsBackToDefault() {
        val result = DisplayFonts.restoredPreferences(catalog(listOf(a, b))) { it == b.file }
        assertFalse(DisplayFonts.SELECTED_KEY in result)
        assertEquals(listOf(b), DisplayFonts.decode((result[DisplayFonts.CATALOG_KEY]!!.value as JsonPrimitive).content))
    }

    @Test fun corruptCatalogRejectsRestore() {
        val bad = mapOf(DisplayFonts.CATALOG_KEY to BackupPreference("string", JsonPrimitive("not-json")))
        assertThrows(BackupException::class.java) { DisplayFonts.restoredPreferences(bad) { true } }
    }

    @Test fun oldBackupsWithoutFontsStayCompatible() {
        val old = mapOf("user_name" to BackupPreference("string", JsonPrimitive("我")))
        assertEquals(old, DisplayFonts.restoredPreferences(old) { false })
    }

    @Test fun importedFontsAreIndependentCompleteCopies() = temporary { dir ->
        val bytes = fixture()
        val first = DisplayFonts.importFont(dir, "手写.ttf", { ByteArrayInputStream(bytes) }) { assertArrayEquals(bytes, it.readBytes()) }
        val second = DisplayFonts.importFont(dir, "手写.ttf", { ByteArrayInputStream(bytes) }) { }
        assertNotEquals(first.file, second.file)
        assertEquals("手写", first.name)
        assertTrue(DisplayFonts.isFontName(first.file))
        assertArrayEquals(bytes, File(dir, first.file).readBytes())
        assertArrayEquals(bytes, File(dir, second.file).readBytes())
    }

    @Test fun failedNativeValidationCleansTemporaryFile() = temporary { dir ->
        assertThrows(IOException::class.java) {
            DisplayFonts.importFont(dir, "bad.ttf", { ByteArrayInputStream(fixture()) }) { throw IOException("unsupported") }
        }
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun incompleteTableCannotReachNativeDecoder() = temporary { dir ->
        val bytes = fixture().apply { ByteBuffer.wrap(this).putInt(20, 0x7fffffff) }
        var called = false
        assertThrows(DisplayFontException::class.java) {
            DisplayFonts.importFont(dir, "bad.ttf", { ByteArrayInputStream(bytes) }) { called = true }
        }
        assertFalse(called)
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun oversizedStreamStopsAndCleansFile() = temporary { dir ->
        val stream = object : InputStream() {
            var remaining = DisplayFonts.MAX_BYTES + 1
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(len, remaining)
                b.fill(0, off, off + count)
                remaining -= count
                return count
            }
        }
        assertThrows(DisplayFontException::class.java) { DisplayFonts.importFont(dir, "huge.ttf", { stream }) { } }
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun interruptedCopyCleansFile() = temporary { dir ->
        val stream = object : InputStream() { override fun read(): Int = throw IOException("read failed") }
        assertThrows(IOException::class.java) { DisplayFonts.importFont(dir, "font.ttf", { stream }) { } }
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun otfSignatureGetsOtfStoredExtension() = temporary { dir ->
        val imported = DisplayFonts.importFont(dir, "圆体.otf", { ByteArrayInputStream(fixture(0x4f54544f)) }) { }
        assertTrue(imported.file.endsWith(".otf"))
    }

    @Test fun configurationRestoreAndUndoCarryFontChoices() {
        val source = ConfigurationBackup(ConfigurationBackup.FORMAT, ConfigurationBackup.VERSION, catalog(listOf(a, b), b.file), emptyMap())
        val restored = source.restoredPreferences(listOf(1L), emptyMap(), { true }, portableRestore = true)
        assertEquals(JsonPrimitive(b.file), restored[DisplayFonts.SELECTED_KEY]!!.value)
        assertEquals(listOf(a, b), DisplayFonts.decode((restored[DisplayFonts.CATALOG_KEY]!!.value as JsonPrimitive).content))
        val snapshot = ConfigurationBackup(ConfigurationBackup.FORMAT, ConfigurationBackup.VERSION, catalog(listOf(a), a.file), emptyMap())
        val undo = snapshot.restoredPreferences(listOf(1L), emptyMap(), { true }, portableRestore = false)
        assertEquals(JsonPrimitive(a.file), undo[DisplayFonts.SELECTED_KEY]!!.value)
    }
}
