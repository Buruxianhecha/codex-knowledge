package com.cleo.cleos.ai

import com.cleo.cleos.data.ApiPresets
import com.cleo.cleos.data.SecretStore
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Only the six explicit presets from the "用谁家的" screen are valid fallback targets.
 * A model ID is scoped to its OWN endpoint; keys remain exclusively in SecretStore.
 */
@Serializable
internal data class ProviderPreference(
    val url: String, val model: String, val enabled: Boolean = true, val order: Int = 0
)
@Serializable
internal data class ProviderFailoverState(
    val enabled: Boolean = false,
    val providers: List<ProviderPreference> = emptyList(),
    val activeByOrigin: Map<String, String> = emptyMap()
)
data class ProviderRoute(val name: String, val endpoint: ApiEndpoint,
                         val isPrimary: Boolean, val isSticky: Boolean)
data class ProviderView(val name: String, val baseUrl: String,
                        val model: String, val selected: Boolean,
                        val hasKey: Boolean, val priority: Int)
data class ProviderPanel(val enabled: Boolean, val rows: List<ProviderView>,
                         val activeName: String?)

object ProviderFailoverRules {
    fun mayRetry(failure: KeyFailureKind, outputStarted: Boolean): Boolean =
        !outputStarted && failure in setOf(
            KeyFailureKind.QUOTA_EXHAUSTED,
            KeyFailureKind.INVALID_CREDENTIAL,
            KeyFailureKind.RATE_LIMITED
        )
    fun normalized(url: String): String = ApiEndpoint(url, "", "").chatUrl.lowercase(Locale.ROOT)
    fun originId(url: String, model: String): String = normalized(url) + "\n" + model
}

/** Keystore-backed provider routing. No plaintext API keys are persisted in this structure.
 * Unlike per-provider key rotation, this changes BOTH endpoint and model only for targets
 * explicitly enabled in the existing six-provider preset list.
 */
class ProviderFailoverPool(private val secrets: SecretStore) {
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val storageKey = "provider_failover_v1"

    private fun defaults(): List<ProviderPreference> =
        ApiPresets.all.mapIndexed { index, preset ->
            ProviderPreference(preset.baseUrl, preset.defaultModel, true, index)
        }
    private suspend fun state(): ProviderFailoverState =
        secrets.secret(storageKey).first()?.let {
            runCatching { json.decodeFromString<ProviderFailoverState>(it) }.getOrNull()
        } ?: ProviderFailoverState()
    private suspend fun store(state: ProviderFailoverState) {
        secrets.setSecret(storageKey, json.encodeToString(state))
    }
    private fun entries(state: ProviderFailoverState): List<ProviderPreference> {
        val configured = state.providers.associateBy { ProviderFailoverRules.normalized(it.url) }
        return defaults().map { origin ->
            configured[ProviderFailoverRules.normalized(origin.url)]?.let {
                // A stale or tampered value must not add a new arbitrary network destination.
                origin.copy(model=it.model, enabled=it.enabled, order=it.order)
            } ?: origin
        }.sortedWith(compareBy<ProviderPreference> { it.order }
            .thenBy { ApiPresets.all.indexOfFirst { preset -> preset.baseUrl == it.url } })
    }
    suspend fun panel(origin: ApiEndpoint): ProviderPanel = lock.withLock {
        val current = state()
        val originId = ProviderFailoverRules.originId(origin.baseUrl, origin.model)
        val sticky = current.activeByOrigin[originId]
        val rows = entries(current).mapIndexed { index, p ->
            val preset = ApiPresets.at(p.url)!!
            ProviderView(preset.name, p.url, p.model, p.enabled,
                !secrets.key(p.url).isNullOrBlank(), index + 1)
        }
        val activeName = rows.firstOrNull {
            ProviderFailoverRules.normalized(it.baseUrl) == sticky
        }?.name ?: ApiPresets.at(origin.baseUrl)?.name
        ProviderPanel(current.enabled, rows, activeName)
    }
    suspend fun turnOn(value: Boolean) = lock.withLock {
        val s=state(); store(s.copy(enabled=value))
    }
    suspend fun allow(url: String, value: Boolean) = lock.withLock {
        val canonical = ApiPresets.at(url)?.baseUrl ?: return@withLock
        val s=state()
        store(s.copy(providers=entries(s).map {
            if(it.url==canonical) it.copy(enabled=value) else it
        }))
    }
    suspend fun setModel(url: String, model: String) = lock.withLock {
        val canonical = ApiPresets.at(url)?.baseUrl ?: return@withLock
        val s=state()
        store(s.copy(providers=entries(s).map {
            if(it.url==canonical) it.copy(model=model.trim().take(160)) else it
        }))
    }
    suspend fun move(url: String, delta: Int) = lock.withLock {
        val canonical = ApiPresets.at(url)?.baseUrl ?: return@withLock
        val s=state()
        val current=entries(s).toMutableList()
        val from=current.indexOfFirst { it.url==canonical }
        val to=from+delta
        if(from<0 || to !in current.indices) return@withLock
        val tmp=current[from]; current[from]=current[to]; current[to]=tmp
        store(s.copy(providers=current.mapIndexed { i,p -> p.copy(order=i) }))
    }
    suspend fun resetActive(origin: ApiEndpoint) = lock.withLock {
        val s=state()
        store(s.copy(activeByOrigin=s.activeByOrigin - ProviderFailoverRules.originId(origin.baseUrl,origin.model)))
    }
    suspend fun enabled(): Boolean = lock.withLock { state().enabled }
    suspend fun routes(origin: ApiEndpoint): List<ProviderRoute> = lock.withLock {
        val s=state()
        if(!s.enabled || ApiPresets.at(origin.baseUrl)==null) return@withLock emptyList()
        val originUrl=ProviderFailoverRules.normalized(origin.baseUrl)
        val id=ProviderFailoverRules.originId(origin.baseUrl,origin.model)
        val active=s.activeByOrigin[id]
        val primary=ProviderRoute(ApiPresets.at(origin.baseUrl)!!.name,origin,true,false)
        val others=entries(s).filter { it.enabled && it.model.isNotBlank() &&
                ProviderFailoverRules.normalized(it.url)!=originUrl }
            .mapNotNull { profile ->
                val key=secrets.key(profile.url)?.trim().orEmpty()
                if(key.isBlank()) null else ProviderRoute(
                    ApiPresets.at(profile.url)!!.name,
                    ApiEndpoint(profile.url,key,profile.model),
                    false, ProviderFailoverRules.normalized(profile.url)==active
                )
            }
        val all=listOf(primary)+others
        all.sortedWith(compareByDescending<ProviderRoute> { it.isSticky })
    }
    suspend fun succeeded(origin: ApiEndpoint, chosen: ProviderRoute) = lock.withLock {
        val s=state()
        val key=ProviderFailoverRules.originId(origin.baseUrl,origin.model)
        store(s.copy(activeByOrigin=s.activeByOrigin + (key to
            ProviderFailoverRules.normalized(chosen.endpoint.baseUrl))))
    }
}
