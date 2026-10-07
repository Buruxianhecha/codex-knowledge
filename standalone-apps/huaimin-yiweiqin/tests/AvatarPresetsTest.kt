package com.cleo.cleos.data

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AvatarPresetsTest {
    @Test fun choosing_the_same_preset_does_not_share_a_deletable_file() {
        val dir = Files.createTempDirectory("avatars").toFile()
        try {
            val preset = AvatarPresets.all.first()
            val original = byteArrayOf(4, 8, 15, 16, 23, 42)
            val first = AvatarPresets.copyToStore(preset, dir) { ByteArrayInputStream(original) }
            val second = AvatarPresets.copyToStore(preset, dir) { ByteArrayInputStream(original) }
            assertNotEquals(first, second)
            assertArrayEquals(original, dir.resolve(first).readBytes())
            assertTrue(preset.isSelected(first))
            assertTrue(preset.isSelected(second))
            dir.resolve(first).delete()
            assertArrayEquals(original, dir.resolve(second).readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun a_failed_copy_leaves_no_incomplete_avatar() {
        val dir = Files.createTempDirectory("avatars").toFile()
        try {
            try {
                AvatarPresets.copyToStore(AvatarPresets.all.first(), dir) {
                    object : InputStream() {
                        private var reads = 0
                        override fun read(): Int {
                            if (reads++ < 5) return 12
                            throw IOException("read failed")
                        }
                    }
                }
                fail("Copy should fail")
            } catch (_: IOException) { }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun rapid_changes_and_reset_finish_in_tap_order() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val queue = AvatarSaveQueue(scope)
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val result = Collections.synchronizedList(mutableListOf<String>())
            queue.submit { started.complete(Unit); release.await(); result.add("first preset") }
            withTimeout(5000) { started.await() }
            val second = queue.submit { result.add("second preset") }
            val reset = queue.submit { result.add("default") }
            assertFalse(second.isCompleted)
            assertFalse(reset.isCompleted)
            release.complete(Unit)
            withTimeout(5000) { reset.join() }
            assertEquals(listOf("first preset", "second preset", "default"), result)
        } finally { scope.cancel() }
    }
}
