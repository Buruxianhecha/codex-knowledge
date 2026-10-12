package com.cleo.cleos.ai

import com.cleo.cleos.data.db.AppDatabase
import com.cleo.cleos.data.db.MemoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Per-character growth preferences, stored in the existing backed-up memory table.
 * This is application configuration, NOT model-visible remembered conversation content.
 * Keeping it outside CompanionEntity avoids a schema migration and preserves old backups.
 */
@Serializable
data class PersonalityGrowthConfig(
    val enabled: Boolean = true,
    val minMessages: Int = 14,
    val minHours: Int = 6,
    val manualEnabled: Boolean = true,
) {
    fun normalized() = copy(
        minMessages = minMessages.coerceIn(1, 100),
        minHours = minHours.coerceIn(0, 48),
    )
}

class PersonalityGrowthConfigStore(private val db: AppDatabase) {
    private val mutex = Mutex()
    private val codec = Json { ignoreUnknownKeys = true }

    companion object {
        const val NAME = "AI 性格成长设置"
        fun isConfig(row: MemoryEntity) = row.name == NAME &&
            row.kind == "self" && row.source == MemoryEntity.SOURCE_ME
    }

    private fun decode(rows: List<MemoryEntity>): PersonalityGrowthConfig =
        rows.firstOrNull(::isConfig)?.summary?.let {
            runCatching { codec.decodeFromString<PersonalityGrowthConfig>(it).normalized() }.getOrNull()
        } ?: PersonalityGrowthConfig()

    fun observe(companionId: Long): Flow<PersonalityGrowthConfig> =
        db.memories().observeFor(companionId).map(::decode)

    suspend fun get(companionId: Long): PersonalityGrowthConfig =
        decode(db.memories().allFor(companionId))

    /** Always serialize writes so quick toggle/slider updates cannot create duplicate rows. */
    suspend fun update(companionId: Long, update: (PersonalityGrowthConfig) -> PersonalityGrowthConfig) {
        if (companionId <= 0L) return
        mutex.withLock {
            val owner = db.companions().get(companionId) ?: return@withLock
            val rows = db.memories().allFor(owner.id)
            val config = update(decode(rows)).normalized()
            val current = rows.firstOrNull(::isConfig)
            val now = System.currentTimeMillis()
            val json = codec.encodeToString(config)
            if (current == null) {
                db.memories().insert(MemoryEntity(
                    companionId = companionId, kind = "self", name = NAME,
                    summary = json, details = "[]", source = MemoryEntity.SOURCE_ME,
                    createdAt = now, updatedAt = now,
                ))
            } else if (current.summary != json) {
                db.memories().update(current.copy(summary = json, updatedAt = now))
            }
        }
    }
}
