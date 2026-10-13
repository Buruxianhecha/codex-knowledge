package com.cleo.cleos.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cleo.cleos.CleosApp
import com.cleo.cleos.data.LifeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Small pure rules shared with persistence/recovery tests. */
object LifeGateRules {
    fun atOrBefore(at: Long, id: Long, answeredAt: Long, answeredId: Long): Boolean =
        at < answeredAt || (at == answeredAt && id <= answeredId)

    fun goalToken(purpose: String): Pair<String, Int>? {
        if (!purpose.startsWith("goal:")) return null
        val separator = purpose.lastIndexOf(':')
        if (separator <= 5) return null
        val id = purpose.substring(5, separator)
        val version = purpose.substring(separator + 1).toIntOrNull()?.takeIf { it > 0 }
            ?: return null
        return id.takeIf { it.isNotBlank() }?.let { it to version }
    }

    /** Assistant-only metadata; ordinary tool ids and malformed data never acknowledge inputs. */
    fun receiptToken(raw: String?): Triple<Long, Long, Long>? {
        val parts = raw?.takeIf { it.startsWith("life-cursor:") }?.split(':') ?: return null
        if (parts.size != 4) return null
        val actor = parts[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val at = parts[2].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val id = parts[3].toLongOrNull()?.takeIf { it > 0L } ?: return null
        return Triple(actor, at, id)
    }

    fun nextWake(now: Long, boundaries: List<Long>): Long =
        boundaries.filter { it > now }.minOrNull()
            ?.coerceAtMost(now + 15 * 60_000L) ?: (now + 15 * 60_000L)
}

/** WorkManager is a best-effort wake, with a 15 minute recovery fallback. No exact alarm. */
class LifeWakeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as CleosApp).container
        return try {
            c.life.reconcileCompanions(c.db.companions().all().map { it.id }.toSet())
            c.life.tick()
            c.chat.resumeLifePending()
            // Optional character-autonomous overtime decisions; safely bounded to two models per cycle.
            // No user-configured API key, no overtime decision. Worker can be delayed by Android.
            LifeOvertimeAutonomy(c).tick()
            // Existing Later rows carry the original note and expiry. Do not copy its
            // instruction into the life queue or send an obsolete follow-up on restart.
            for ((actor, marker) in c.life.pendingReady().filter { it.second.purpose == "wake" }) {
                if (c.chat.busy(marker.conversationId) || c.life.availability(actor).isBusy) continue
                if (!c.life.beginPendingAttempt(actor, marker.id)) continue
                val notes = c.db.later().dueFor(actor, System.currentTimeMillis())
                for (note in notes) {
                    val wait = c.later.wake(note.id)
                    if (wait != null) c.later.again(Later.KIND_WAKE, note.id, wait)
                }
                if (!c.life.availability(actor).isBusy)
                    c.life.completePending(actor, marker.id, marker.messageCursor, marker.messageId)
            }
            reconcile(applicationContext, c.life, afterCurrent = true)
            Result.success()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (error: Exception) {
            android.util.Log.w("LifeWake", "life recovery failed; pending data retained", error)
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "huaimin-life-periodic-v1"
        private const val NEXT = "huaimin-life-next-v1"
        private const val AT_TAG = "huaimin-life-at:"
        private val scheduling = Mutex()

        fun install(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LifeWakeWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        suspend fun reconcile(context: Context, life: LifeStore, afterCurrent: Boolean = false) =
            scheduling.withLock {
                val now = System.currentTimeMillis()
                val profiles = life.state.value.profiles.values
                val boundaries = profiles.mapNotNull {
                    life.availability(it.companionId, now).busyUntil
                } + profiles.flatMap { p -> p.pending.filter { it.attempts < 3 }
                    .map { it.nextAttemptAt }.filter { it > now } }
                var at = LifeGateRules.nextWake(now, boundaries)
                // Recovery attempt backoff lives in LifeStore; only ready entries ask for
                // an early worker. Foreground explicit wake also resumes immediately.
                if (life.pendingReady(now).isNotEmpty()) at = minOf(at, now + 1500L)
                val manager = WorkManager.getInstance(context)
                val existing = withContext(Dispatchers.IO) {
                    manager.getWorkInfosForUniqueWork(NEXT).get()
                }
                val running = existing.any { it.state == WorkInfo.State.RUNNING }
                if (running && !afterCurrent) return@withLock
                val plannedAt = existing.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
                    ?.tags?.firstOrNull { it.startsWith(AT_TAG) }
                    ?.removePrefix(AT_TAG)?.toLongOrNull()
                if (!running && plannedAt != null && kotlin.math.abs(plannedAt - at) < 2000L)
                    return@withLock
                val request = OneTimeWorkRequestBuilder<LifeWakeWorker>()
                    .setInitialDelay((at - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    .addTag(AT_TAG + at)
                    .build()
                // A cursor commit must never REPLACE a running worker/model request.
                manager.enqueueUniqueWork(NEXT,
                    if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                    request)
            }
    }
}
