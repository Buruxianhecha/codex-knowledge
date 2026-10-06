package com.cleo.cleos.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Values retain their DataStore type, including settings introduced by later versions. */
@Serializable
internal data class BackupPreference(val kind: String, val value: JsonElement)

internal object PreferenceBackup {
    fun capture(prefs: Preferences): Map<String, BackupPreference> = prefs.asMap().entries.associate { (key, value) ->
        key.name to when (value) {
            is String -> BackupPreference("string", JsonPrimitive(value))
            is Boolean -> BackupPreference("boolean", JsonPrimitive(value))
            is Int -> BackupPreference("int", JsonPrimitive(value))
            is Long -> BackupPreference("long", JsonPrimitive(value))
            is Float -> BackupPreference("float", JsonPrimitive(value))
            is Double -> BackupPreference("double", JsonPrimitive(value))
            is Set<*> -> {
                check(value.all { it is String }) { "不支持的配置类型" }
                BackupPreference("string-set", JsonArray(value.filterIsInstance<String>().sorted().map(::JsonPrimitive)))
            }
            else -> throw BackupException("有配置无法备份，请先更新软件")
        }
    }.also { validate(it) }

    fun validate(values: Map<String, BackupPreference>, expected: Map<String, String> = emptyMap()) {
        if (values.size > 2000) invalid()
        for ((name, stored) in values) {
            if (name.isBlank() || name.length > 1024 || name.any { it.isISOControl() }) invalid()
            if (expected[name]?.let { it != stored.kind } == true) invalid()
            val p = stored.value as? JsonPrimitive
            val valid = when (stored.kind) {
                "string" -> p?.isString == true && p.content.length <= ConfigurationCodec.MAX_BYTES
                "boolean" -> p?.isString == false && p.booleanOrNull != null
                "int" -> p?.isString == false && p.intOrNull != null
                "long" -> p?.isString == false && p.longOrNull != null
                "float" -> p?.isString == false && p.floatOrNull?.isFinite() == true
                "double" -> p?.isString == false && p.doubleOrNull?.isFinite() == true
                "string-set" -> (stored.value as? JsonArray)?.let { array ->
                    array.size <= 2000 && array.all {
                        it is JsonPrimitive && it.isString && it.content.length <= ConfigurationCodec.MAX_BYTES
                    }
                } == true
                else -> false
            }
            if (!valid) invalid()
        }
    }

    /** Validation finishes before clearing the destination. */
    fun replace(values: Map<String, BackupPreference>, prefs: MutablePreferences) {
        validate(values)
        prefs.clear()
        for ((name, stored) in values) {
            val p = stored.value as? JsonPrimitive
            when (stored.kind) {
                "string" -> prefs[stringPreferencesKey(name)] = p!!.content
                "boolean" -> prefs[booleanPreferencesKey(name)] = p!!.boolean
                "int" -> prefs[intPreferencesKey(name)] = p!!.int
                "long" -> prefs[longPreferencesKey(name)] = p!!.long
                "float" -> prefs[floatPreferencesKey(name)] = p!!.float
                "double" -> prefs[doublePreferencesKey(name)] = p!!.double
                "string-set" -> prefs[stringSetPreferencesKey(name)] =
                    (stored.value as JsonArray).map { it.jsonPrimitive.content }.toSet()
            }
        }
    }

    private fun invalid(): Nothing = throw BackupException("备份里的配置无效，没有恢复")
}

/** Every API address and every named secret (including encrypted MCP connections) travels. */
internal object SecretBackup {
    fun recognized(name: String): Boolean = name == "api_key" ||
        (name.startsWith("api_key:") && name.length > 8) ||
        (name.startsWith("secret:") && name.length > 7)

    fun validate(values: Map<String, String>) {
        if (values.size > 2000 || values.any { (name, value) ->
            !recognized(name) || name.length > 4096 || name.any { it.isISOControl() } ||
                value.isEmpty() || value.length > 2 * 1024 * 1024
        }) throw BackupException("备份里的凭据无效，没有恢复")
    }

    /** Do not silently skip a stored key that the current installation cannot decrypt. */
    fun decryptAll(stored: Map<String, String>, decrypt: (String) -> String): Map<String, String> {
        return try {
            stored.mapValues { (_, value) -> decrypt(value) }.also(::validate)
        } catch (_: Exception) {
            throw BackupException("有已保存的 Key 或连接凭据无法读取，请重新保存后再导出；备份未完成")
        }
    }

    /** Null means an old archive: it must preserve existing credentials. Empty means clear. */
    fun prepareRestore(values: Map<String, String>?, encrypt: (String) -> String): Map<String, String>? =
        values?.let { encryptAll(it, encrypt) }

    /** Complete all encryption before any preference is removed or written. */
    fun encryptAll(values: Map<String, String>, encrypt: (String) -> String): Map<String, String> {
        validate(values)
        return try {
            values.mapValues { (_, value) -> encrypt(value) }
        } catch (_: Exception) {
            throw BackupException("无法加密保存备份里的凭据，没有恢复")
        }
    }
}

/** Additive archive entry: backup.json remains compatible with historical backups. */
@Serializable
internal data class ConfigurationBackup(
    val format: String,
    val version: Int,
    val preferences: Map<String, BackupPreference>,
    val secrets: Map<String, String>,
) {
    fun validate(expected: Map<String, String> = emptyMap()) {
        if (format != FORMAT || version != VERSION) throw BackupException("备份配置版本不支持，请先更新软件")
        PreferenceBackup.validate(preferences, expected)
        SecretBackup.validate(secrets)
    }

    /**
     * Keep the selected TA/conversation only when they exist and belong together.
     * Android permission grants are local to the installation, not portable settings.
     */
    fun restoredPreferences(
        companionIds: List<Long>,
        conversationOwners: Map<Long, Long>,
        pictureExists: (String) -> Boolean,
        portableRestore: Boolean,
    ): Map<String, BackupPreference> {
        require(companionIds.isNotEmpty())
        val restored = preferences.toMutableMap()
        val requested = preferences["current_companion"]?.value?.jsonPrimitive?.longOrNull
        val selected = requested?.takeIf { it in companionIds } ?: companionIds.first()
        restored["current_companion"] = BackupPreference("long", JsonPrimitive(selected))
        val conversation = preferences["current_conversation"]?.value?.jsonPrimitive?.content?.toLongOrNull()
        if (conversation == null || conversationOwners[conversation] != selected) restored.remove("current_conversation")
        for (name in listOf("wallpaper", "user_avatar", "ai_avatar")) {
            val file = restored[name]?.value?.jsonPrimitive?.content ?: continue
            if (file.isBlank() || '/' in file || '\\' in file || file.startsWith(".") || !pictureExists(file)) {
                restored.remove(name)
            }
        }
        if (portableRestore) restored["notifications_asked"] = BackupPreference("boolean", JsonPrimitive(false))
        return restored
    }

    companion object {
        const val FORMAT = "huaimin-configuration"
        const val VERSION = 1
        const val ENTRY = "configuration.json"
    }
}

internal object ConfigurationCodec {
    const val MAX_BYTES = 16 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(value: ConfigurationBackup): ByteArray {
        value.validate()
        return json.encodeToString(value).toByteArray(Charsets.UTF_8).also {
            if (it.size > MAX_BYTES) throw BackupException("配置太大，备份未完成")
        }
    }

    /** A corrupt/oversized new entry is a failed restore, never a legacy restore. */
    fun decode(input: InputStream): ConfigurationBackup {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (bytes.size() + count > MAX_BYTES) throw BackupException("备份配置太大，没有恢复")
            bytes.write(buffer, 0, count)
        }
        return try {
            json.decodeFromString<ConfigurationBackup>(bytes.toString(Charsets.UTF_8.name())).also { it.validate() }
        } catch (e: BackupException) {
            throw e
        } catch (_: Exception) {
            // Parser errors can include the input, which contains Keys. Keep them out of UI/logs.
            throw BackupException("备份里的配置读不出来，没有恢复")
        }
    }
}

/**
 * Room rolls back its rows; these two DataStores must roll back too, including cancellation
 * and a failure when committing Room. The encrypted rollback state never leaves app storage.
 */
internal suspend fun <T> withConfigurationRollback(
    readPreferences: suspend () -> Map<String, BackupPreference>,
    readEncryptedSecrets: suspend () -> Map<String, String>,
    writePreferences: suspend (Map<String, BackupPreference>) -> Unit,
    writeEncryptedSecrets: suspend (Map<String, String>) -> Unit,
    action: suspend () -> T,
): T {
    val beforePreferences = readPreferences()
    val beforeSecrets = readEncryptedSecrets()
    try {
        return action()
    } catch (failure: Throwable) {
        var rollbackFailed = false
        withContext(NonCancellable) {
            try { writePreferences(beforePreferences) } catch (_: Throwable) { rollbackFailed = true }
            try { writeEncryptedSecrets(beforeSecrets) } catch (_: Throwable) { rollbackFailed = true }
        }
        if (rollbackFailed) {
            throw BackupException("恢复没有完成，部分配置未能回滚；请保留备份，重新恢复或撤销")
        }
        throw failure
    }
}
