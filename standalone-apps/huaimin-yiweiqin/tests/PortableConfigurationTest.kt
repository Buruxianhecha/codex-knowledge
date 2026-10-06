package com.cleo.cleos.data

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PortableConfigurationTest {
    private fun text(value: String) = BackupPreference("string", JsonPrimitive(value))
    private fun number(value: Long) = BackupPreference("long", JsonPrimitive(value))
    private fun full(
        preferences: Map<String, BackupPreference> = emptyMap(),
        secrets: Map<String, String> = emptyMap(),
    ) = ConfigurationBackup(ConfigurationBackup.FORMAT, ConfigurationBackup.VERSION, preferences, secrets)

    @Test fun everyDataStoreTypeSurvivesPortableRoundTrip() {
        val original = mutablePreferencesOf(
            stringPreferencesKey("address") to "https://www.sui-xiang.net/v1",
            booleanPreferencesKey("pat_buzz") to false,
            intPreferencesKey("chat_text_size") to 19,
            longPreferencesKey("current_companion") to 9L,
            floatPreferencesKey("wallpaper_hue") to 213.5f,
            doublePreferencesKey("future_ratio") to 0.125,
            stringSetPreferencesKey("future_choices") to setOf("second", "first"),
        )
        val restored = mutablePreferencesOf(stringPreferencesKey("stale") to "remove me")
        val decoded = ConfigurationCodec.decode(ConfigurationCodec.encode(full(PreferenceBackup.capture(original))).inputStream())
        PreferenceBackup.replace(decoded.preferences, restored)
        assertEquals(original.asMap(), restored.asMap())
        assertNull(restored[stringPreferencesKey("stale")])
    }

    @Test fun formerlyMissingSettingsAndSelectionsAreKept() {
        val original = mutablePreferencesOf(
            intPreferencesKey("chat_text_size") to 21,
            stringPreferencesKey("pat_verb") to "捏",
            stringPreferencesKey("pat_suffix") to "的脸",
            booleanPreferencesKey("pat_buzz") to false,
            stringPreferencesKey("tools") to "Apps:on,Pat:off",
            stringPreferencesKey("speech_voices") to """{"eleven":"voice-7"}""",
            stringPreferencesKey("current_conversation") to "100",
            longPreferencesKey("current_companion") to 2L,
        )
        val decoded = ConfigurationCodec.decode(ConfigurationCodec.encode(full(PreferenceBackup.capture(original))).inputStream())
        val restored = decoded.restoredPreferences(listOf(1L, 2L), mapOf(100L to 2L), { true }, false)
        original.asMap().forEach { (key, _) -> assertEquals(PreferenceBackup.capture(original)[key.name], restored[key.name]) }
    }

    @Test fun everySavedAddressLegacyVoiceAndMcpSecretTravel() {
        val values = mapOf(
            "api_key:https://www.sui-xiang.net/v1/chat/completions" to "dummy-chat-key",
            "api_key:https://unused.example/v1/chat/completions" to "dummy-unused-key",
            "api_key:https://voice.example/v1/chat/completions" to "dummy-voice-key",
            "api_key" to "dummy-legacy-key",
            "secret:mcp_servers" to """[{"id":"one","name":"custom","url":"https://tools.example/mcp","token":"dummy-token","header":"X-Key: dummy-header","enabled":false,"askFirst":true,"allowed":["search"]}]""",
            "secret:future-service" to "dummy-future-secret",
        )
        val decoded = ConfigurationCodec.decode(ConfigurationCodec.encode(full(secrets = values)).inputStream())
        assertEquals(values, decoded.secrets)
        val server = Json { ignoreUnknownKeys = true }.decodeFromString<List<McpServer>>(decoded.secrets.getValue("secret:mcp_servers")).single()
        assertEquals("dummy-token", server.token)
        assertEquals("X-Key: dummy-header", server.header)
        assertFalse(server.enabled)
        assertTrue(server.askFirst)
        assertEquals(setOf("search"), server.allowed)
    }

    private fun encrypt(value: String, key: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val b64 = Base64.getEncoder()
        return b64.encodeToString(cipher.iv) + ":" + b64.encodeToString(cipher.doFinal(value.toByteArray()))
    }
    private fun decrypt(value: String, key: ByteArray): String {
        val parts = value.split(":", limit = 2)
        val decoder = Base64.getDecoder()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, decoder.decode(parts[0])))
        return String(cipher.doFinal(decoder.decode(parts[1])))
    }

    @Test fun receivingInstallationReencryptsKeysRatherThanCopyingCiphertext() {
        val previousKey = ByteArray(32) { 1 }
        val receivingKey = ByteArray(32) { 2 }
        val plain = mapOf("secret:mcp_servers" to "dummy-mcp", "api_key" to "dummy-api")
        val encrypted = SecretBackup.encryptAll(plain) { encrypt(it, previousKey) }
        val portable = SecretBackup.decryptAll(encrypted) { decrypt(it, previousKey) }
        val received = SecretBackup.prepareRestore(portable) { encrypt(it, receivingKey) }!!
        assertEquals(plain, SecretBackup.decryptAll(received) { decrypt(it, receivingKey) })
        assertNotEquals(encrypted, received)
        assertThrows(BackupException::class.java) { SecretBackup.decryptAll(received) { decrypt(it, previousKey) } }
    }

    @Test fun unreadableKeyFailsTheWholeExportWithoutExposingTheKey() {
        val failure = assertThrows(BackupException::class.java) {
            SecretBackup.decryptAll(mapOf("api_key" to "dummy-private-ciphertext")) { error("dummy-private-ciphertext") }
        }
        assertFalse(failure.message.orEmpty().contains("dummy-private-ciphertext"))
    }

    @Test fun encryptionFailureDoesNotProduceAPartialPreparedRestore() {
        var calls = 0
        assertThrows(BackupException::class.java) {
            SecretBackup.prepareRestore(mapOf("api_key" to "dummy-one", "secret:two" to "dummy-two")) {
                if (++calls == 2) error("dummy-two")
                "encrypted"
            }
        }
        assertEquals(2, calls)
    }

    @Test fun legacyArchivePreservesKeysWhileAnEmptyFullBackupClearsThem() {
        var calls = 0
        val legacy = SecretBackup.prepareRestore(null) { calls++; it }
        assertNull(legacy)
        assertEquals(0, calls)
        val empty = SecretBackup.prepareRestore(emptyMap()) { calls++; it }
        assertNotNull(empty)
        assertTrue(empty!!.isEmpty())
        assertEquals(0, calls)
    }

    @Test fun malformedConfigurationCannotExposeInputOrDowngradeToLegacy() {
        val failure = assertThrows(BackupException::class.java) {
            ConfigurationCodec.decode("""{"format":"huaimin-configuration","version":1,"preferences":{},"secrets":"dummy-private-key"}""".byteInputStream())
        }
        assertFalse(failure.message.orEmpty().contains("dummy-private-key"))
    }

    @Test fun missingCredentialsMemberIsARejectedFullBackup() {
        assertThrows(BackupException::class.java) {
            ConfigurationCodec.decode("""{"format":"huaimin-configuration","version":1,"preferences":{}}""".byteInputStream())
        }
    }

    @Test fun unsupportedVersionAndWrongFormatAreRejected() {
        assertThrows(BackupException::class.java) { full().copy(version = 2).validate() }
        assertThrows(BackupException::class.java) { full().copy(format = "another-app").validate() }
    }

    @Test fun oversizedConfigurationIsBoundedBeforeJsonParsing() {
        val stream = object : InputStream() {
            var remaining = ConfigurationCodec.MAX_BYTES + 1
            override fun read(): Int = if (remaining-- > 0) 32 else -1
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(len, remaining)
                bytes.fill(32, off, off + count)
                remaining -= count
                return count
            }
        }
        assertThrows(BackupException::class.java) { ConfigurationCodec.decode(stream) }
    }

    @Test fun wrongTypeForAKnownSettingIsRejectedBeforeWrites() {
        val values = mapOf("chat_text_size" to text("large"))
        assertThrows(BackupException::class.java) { full(values).validate(mapOf("chat_text_size" to "int")) }
    }

    @Test fun futureSettingsAreRetainedWithTheirOwnTypes() {
        val values = mapOf("future_toggle" to BackupPreference("boolean", JsonPrimitive(true)))
        full(values).validate(mapOf("chat_text_size" to "int"))
        val restored = mutablePreferencesOf()
        PreferenceBackup.replace(values, restored)
        assertTrue(restored[booleanPreferencesKey("future_toggle")]!!)
    }

    @Test fun invalidValueNeverClearsExistingPreferences() {
        val restored = mutablePreferencesOf(stringPreferencesKey("user_name") to "before")
        assertThrows(BackupException::class.java) {
            PreferenceBackup.replace(mapOf("history_size" to BackupPreference("int", JsonPrimitive("40"))), restored)
        }
        assertEquals("before", restored[stringPreferencesKey("user_name")])
    }

    @Test fun unrecognizedCredentialsAndNonfiniteNumbersAreRejected() {
        assertThrows(BackupException::class.java) { SecretBackup.validate(mapOf("unrelated-file" to "dummy")) }
        assertThrows(BackupException::class.java) { SecretBackup.validate(mapOf("api_key" to "")) }
        assertThrows(BackupException::class.java) {
            PreferenceBackup.validate(mapOf("wallpaper_hue" to BackupPreference("float", JsonPrimitive("NaN"))))
        }
    }

    @Test fun validCurrentTaAndItsConversationRemainSelected() {
        val values = mapOf("current_companion" to number(8L), "current_conversation" to text("77"))
        val restored = full(values).restoredPreferences(listOf(1L, 8L), mapOf(77L to 8L), { true }, true)
        assertEquals(number(8L), restored["current_companion"])
        assertEquals(text("77"), restored["current_conversation"])
    }

    @Test fun staleSelectionFallsBackAndCrossTaConversationIsRemoved() {
        val missing = full(mapOf("current_companion" to number(99L), "current_conversation" to text("77")))
            .restoredPreferences(listOf(1L, 8L), mapOf(77L to 8L), { true }, true)
        assertEquals(number(1L), missing["current_companion"])
        assertNull(missing["current_conversation"])
        val crossTa = full(mapOf("current_companion" to number(8L), "current_conversation" to text("77")))
            .restoredPreferences(listOf(1L, 8L), mapOf(77L to 1L), { true }, true)
        assertNull(crossTa["current_conversation"])
    }

    @Test fun missingAndUnsafePicturesAreRemovedButAvailableWallpaperRemains() {
        val values = mapOf("wallpaper" to text("ocean.jpg"), "user_avatar" to text("../elsewhere"), "ai_avatar" to text("missing.jpg"))
        val restored = full(values).restoredPreferences(listOf(1L), emptyMap(), { it == "ocean.jpg" }, true)
        assertEquals(text("ocean.jpg"), restored["wallpaper"])
        assertNull(restored["user_avatar"])
        assertNull(restored["ai_avatar"])
    }

    @Test fun portableRestoreResetsPermissionAskedWhileUndoKeepsIt() {
        val config = full(mapOf("notifications_asked" to BackupPreference("boolean", JsonPrimitive(true))))
        assertEquals(JsonPrimitive(false), config.restoredPreferences(listOf(1L), emptyMap(), { true }, true)["notifications_asked"]!!.value)
        assertEquals(JsonPrimitive(true), config.restoredPreferences(listOf(1L), emptyMap(), { true }, false)["notifications_asked"]!!.value)
    }

    private class Stores {
        var prefs = mapOf("user_name" to BackupPreference("string", JsonPrimitive("before")))
        var secrets = mapOf("api_key" to "encrypted-before")
        suspend fun <T> protect(action: suspend () -> T): T = withConfigurationRollback(
            { prefs.toMap() }, { secrets.toMap() }, { prefs = it }, { secrets = it }, action,
        )
    }

    @Test fun successfulRestoreAndUndoRestoreBothConfigurationAndCredentials() = runBlocking {
        val stores = Stores()
        val snapshot = full(stores.prefs, SecretBackup.decryptAll(stores.secrets) { it.removePrefix("encrypted-") })
        stores.protect {
            stores.prefs = mapOf("user_name" to text("new"))
            stores.secrets = mapOf("api_key" to "encrypted-new")
        }
        val saved = ConfigurationCodec.decode(ConfigurationCodec.encode(snapshot).inputStream())
        stores.protect {
            stores.prefs = saved.preferences
            stores.secrets = SecretBackup.prepareRestore(saved.secrets) { "encrypted-" + it }!!
        }
        assertEquals(text("before"), stores.prefs["user_name"])
        assertEquals("encrypted-before", stores.secrets["api_key"])
    }

    @Test fun failureWritingSecretsRollsPreferencesAndKeysBack() = runBlocking {
        val stores = Stores()
        try {
            stores.protect {
                stores.prefs = mapOf("user_name" to text("after"))
                error("secret write failed")
            }
            fail("restore should fail")
        } catch (_: IllegalStateException) {}
        assertEquals(text("before"), stores.prefs["user_name"])
        assertEquals("encrypted-before", stores.secrets["api_key"])
    }

    @Test fun databaseCommitFailureRollsBackBothCompletedConfigurationWrites() = runBlocking {
        val stores = Stores()
        try {
            stores.protect {
                stores.prefs = mapOf("user_name" to text("after"))
                stores.secrets = mapOf("api_key" to "encrypted-after")
                error("database commit failed")
            }
            fail("restore should fail")
        } catch (_: IllegalStateException) {}
        assertEquals(text("before"), stores.prefs["user_name"])
        assertEquals("encrypted-before", stores.secrets["api_key"])
    }

    @Test fun cancellationStillFinishesBothRollbackWrites() = runBlocking {
        val stores = Stores()
        val job = launch {
            stores.protect {
                stores.prefs = mapOf("user_name" to text("after"))
                stores.secrets = mapOf("api_key" to "encrypted-after")
                cancel()
                yield()
            }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(text("before"), stores.prefs["user_name"])
        assertEquals("encrypted-before", stores.secrets["api_key"])
    }

    @Test fun rollbackFailureStillAttemptsTheOtherStoreAndReportsFailure() = runBlocking {
        var secretRollbackTried = false
        try {
            withConfigurationRollback(
                { mapOf("user_name" to text("before")) }, { mapOf("api_key" to "encrypted-before") },
                { error("disk failure") }, { secretRollbackTried = true },
            ) { error("original failure") }
            fail("restore should fail")
        } catch (e: BackupException) {
            assertTrue(e.message.orEmpty().contains("回滚"))
        }
        assertTrue(secretRollbackTried)
    }
}
