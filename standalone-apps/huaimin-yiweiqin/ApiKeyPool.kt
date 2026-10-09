package com.cleo.cleos.ai

import com.cleo.cleos.data.SecretStore
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable internal data class StoredPool(
    val primaryEnabled: Boolean = true,
    val primaryHealth: String = "UNKNOWN",
    val primarySignature: String = "",
    val backups: List<StoredBackup> = emptyList()
)
@Serializable internal data class StoredBackup(
    val id: String, val name: String, val secret: String,
    val enabled: Boolean = true, val priority: Int = 10,
    val modelScope: String = "", val health: String = "UNKNOWN",
    val cooldownUntil: Long = 0
)
data class KeyCandidate(val id: String, val alias: String, val value: String,
                        val priority: Int, val health: String = "UNKNOWN",
                        val enabled: Boolean = true, val modelScope: String = "",
                        val cooldownUntil: Long = 0)
data class KeyDisplay(val id: String, val alias: String, val priority: Int,
                      val enabled: Boolean, val health: String, val modelScope: String)
object KeySelection {
    fun compatible(all: List<KeyCandidate>, model: String, now: Long): List<KeyCandidate> =
        all.filter { it.enabled && (it.modelScope.isBlank() || it.modelScope == model) &&
            it.health != "EXHAUSTED" && it.health != "INVALID" &&
            !(it.health == "RATE_LIMITED" && now < it.cooldownUntil) }
            .sortedWith(compareBy<KeyCandidate> { it.priority }.thenBy { it.id })
}

/** Existing master Key and new extra keys stay encrypted inside the same Keystore-backed SecretStore.
 * The entire pool is isolated by normalized chat URL. Never sends a key to a new address.
 */
class ApiKeyPool(private val secrets: SecretStore) {
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun name(url: String) = "multi_key_pool:" + ApiEndpoint(url,"","").chatUrl.lowercase()
    private suspend fun read(url: String): StoredPool =
        secrets.secret(name(url)).first()?.let { runCatching { json.decodeFromString<StoredPool>(it) }.getOrNull() }
            ?: StoredPool()
    private suspend fun write(url: String, state: StoredPool) =
        secrets.setSecret(name(url), json.encodeToString(state))
    private fun fingerprint(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    suspend fun enabled(): Boolean = secrets.secret("multi_key_auto_enabled").first() != "false"
    suspend fun setEnabled(on: Boolean) = secrets.setSecret("multi_key_auto_enabled", if (on) "true" else "false")
    private suspend fun all(url: String): List<KeyCandidate> {
        val s = read(url)
        val primary = secrets.key(url)?.trim().orEmpty()
        return buildList {
            if (primary.isNotBlank()) add(KeyCandidate("primary", "原有密钥", primary, 0,
                if (fingerprint(primary) == s.primarySignature) s.primaryHealth else "UNKNOWN", s.primaryEnabled))
            s.backups.forEach {
                add(KeyCandidate(it.id, it.name, it.secret, it.priority,
                    it.health, it.enabled, it.modelScope, it.cooldownUntil))
            }
        }
    }
    suspend fun candidates(url: String, model: String, preferred: String): List<KeyCandidate> =
        lock.withLock {
            val all = all(url)
            // Never silently rotate a key passed for some other endpoint/account.
            if (all.none { it.value == preferred }) return@withLock emptyList()
            KeySelection.compatible(all, model, System.currentTimeMillis())
                .sortedWith(compareBy<KeyCandidate> { it.value != preferred }.thenBy { it.priority })
        }
    suspend fun display(url: String): List<KeyDisplay> = lock.withLock {
        all(url).map { KeyDisplay(it.id,it.alias,it.priority,it.enabled,it.health,it.modelScope) }
    }
    suspend fun addBackup(url: String, alias: String, secret: String, model: String = "") {
        require(url.startsWith("https://") && secret.isNotBlank())
        lock.withLock {
            val pool=read(url)
            require(pool.backups.size < 12) { "备用密钥最多 12 把" }
            require(pool.backups.none { it.secret == secret.trim() }) { "密钥已经存在" }
            val next=StoredBackup(UUID.randomUUID().toString(),alias.trim().ifBlank {"备用密钥"}.take(32),
                secret.trim(),priority=pool.backups.size+1,modelScope=model.trim())
            write(url,pool.copy(backups=pool.backups+next))
        }
    }
    suspend fun enable(url: String,id: String,on: Boolean) = lock.withLock {
        val pool=read(url)
        write(url,if(id=="primary") pool.copy(primaryEnabled=on) else
            pool.copy(backups=pool.backups.map { if(it.id==id) it.copy(enabled=on) else it }))
    }
    suspend fun remove(url: String,id: String) = lock.withLock {
        require(id!="primary")
        val pool=read(url)
        write(url,pool.copy(backups=pool.backups.filterNot { it.id==id }))
    }
    suspend fun reset(url: String,id: String) = lock.withLock {
        val pool=read(url)
        write(url,if(id=="primary") pool.copy(primaryHealth="UNKNOWN") else pool.copy(
            backups=pool.backups.map { if(it.id==id) it.copy(health="UNKNOWN",cooldownUntil=0) else it }))
    }
    suspend fun record(url: String,id: String,kind:KeyFailureKind) = lock.withLock {
        val p=read(url)
        val status=when(kind) {
            KeyFailureKind.QUOTA_EXHAUSTED -> "EXHAUSTED"
            KeyFailureKind.INVALID_CREDENTIAL -> "INVALID"
            KeyFailureKind.RATE_LIMITED -> "RATE_LIMITED"
            else -> "UNKNOWN"
        }
        val cooldown=if(kind==KeyFailureKind.RATE_LIMITED) System.currentTimeMillis()+30000 else 0L
        if(id=="primary") {
            val original=secrets.key(url)?.trim().orEmpty()
            write(url,p.copy(primaryHealth=status,primarySignature=fingerprint(original)))
        } else write(url,p.copy(backups=p.backups.map {
            if(it.id==id) it.copy(health=status,cooldownUntil=cooldown) else it
        }))
    }
    suspend fun success(url: String,id: String) = reset(url,id)
    suspend fun allConfirmedExhausted(url: String,model: String): Boolean {
        val rows=display(url).filter { it.enabled && (it.modelScope.isBlank() || it.modelScope==model) }
        return rows.isNotEmpty() && rows.all { it.health=="EXHAUSTED" }
    }
}
