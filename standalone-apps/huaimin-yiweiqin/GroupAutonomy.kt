package com.cleo.cleos.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.cleo.cleos.CleosApp
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Opt-in background group companionship. Android executes periodic work at
 * inexact times, never as a tight background polling loop or an immortal service.
 */
object GroupAutonomy {
    private const val WORK_NAME="huaimin-opt-in-group-conversation"
    const val QUIET_AFTER_MS=50L*60*1000
    const val FRESH_USER_MS=24L*60*60*1000
    const val UNANSWERED_MAX=2
    fun allowed(mode:Int,hour:Int,lastUserAt:Long?,lastReplyAt:Long?,
                unanswered:Int,now:Long,remaining:Int):Boolean =
        mode == GroupAutoMode.MODE && hour in 9..21 &&
            lastUserAt != null && lastUserAt <= now &&
            now-lastUserAt <= FRESH_USER_MS &&
            lastReplyAt != null && lastReplyAt <= now &&
            now-lastReplyAt >= QUIET_AFTER_MS &&
            unanswered < UNANSWERED_MAX && remaining > 0

    fun install(context:Context) {
        val limits=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val task=PeriodicWorkRequestBuilder<GroupAutonomyWorker>(30,TimeUnit.MINUTES)
            .setConstraints(limits).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,ExistingPeriodicWorkPolicy.KEEP,task)
    }
}

class GroupAutonomyWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val c=(applicationContext as CleosApp).container
        val now=System.currentTimeMillis()
        val groups=c.db.conversations().all().filter { it.isGroup && it.groupMode==GroupAutoMode.MODE }
        for (group in groups.take(12)) {
            if (isStopped) break
            try {
                val messages=c.chat.backgroundGroupCycle(group.id,now)
                for (msg in messages) {
                    val speakerId=msg.senderCompanionId ?: continue
                    val ta=c.companions.get(speakerId) ?: continue
                    if (!(c.visible && c.chatOnScreen==group.id))
                        c.notifier.messages(ta,group.id,listOf(msg))
                }
            } catch (e:kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e:Exception) {
                android.util.Log.w("GroupAutonomy","Background group turn failed",e)
            }
        }
        return Result.success()
    }
}
