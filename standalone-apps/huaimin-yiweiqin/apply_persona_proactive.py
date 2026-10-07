#!/usr/bin/env python3
from pathlib import Path
import shutil, sys

ROOT = Path(sys.argv[1]).resolve()
HERE = Path(__file__).resolve().parent

def rep(rel, old, new):
    p = ROOT / rel
    s = p.read_text(encoding="utf-8")
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{rel}: expected one match, got {n}: {old[:100]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")

def cp(name, rel, tests=False):
    src = HERE / ("tests" if tests else "src") / name
    dst = ROOT / rel
    if not src.is_file():
        raise SystemExit(f"missing {src}")
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)

for name, rel in [
    ("FollowUpRules.kt","app/src/main/java/com/cleo/cleos/ai/FollowUpRules.kt"),
    ("FollowUps.kt","app/src/main/java/com/cleo/cleos/ai/FollowUps.kt"),
    ("FreeTopicRules.kt","app/src/main/java/com/cleo/cleos/ai/FreeTopicRules.kt"),
    ("FreeTopics.kt","app/src/main/java/com/cleo/cleos/ai/FreeTopics.kt"),
    ("FreeTopicSection.kt","app/src/main/java/com/cleo/cleos/ui/settings/FreeTopicSection.kt"),
    ("ProactiveHistorySection.kt","app/src/main/java/com/cleo/cleos/ui/settings/ProactiveHistorySection.kt"),
]:
    cp(name, rel)
for name in ("FreeTopicRulesTest.kt","FollowUpRulesTest.kt"):
    cp(name, f"app/src/test/java/com/cleo/cleos/ai/{name}", True)

entities="app/src/main/java/com/cleo/cleos/data/db/Entities.kt"
rep(entities,
'''    @ColumnInfo(defaultValue = "0")
    val deepThinking: Boolean = false,
    /** May note things down to come back to, and say them on its own when they come due (ai/Later.kt). */''',
'''    @ColumnInfo(defaultValue = "0")
    val deepThinking: Boolean = false,
    @ColumnInfo(defaultValue = "0") val followUpEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "60") val followUpDelaySeconds: Int = 60,
    @ColumnInfo(defaultValue = "0") val freeTopicEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "-1") val freeTopicLevel: Int = -1,
    @ColumnInfo(defaultValue = "1") val freeTopicQuietOn: Boolean = true,
    @ColumnInfo(defaultValue = "1380") val freeTopicQuietStart: Int = 1380,
    @ColumnInfo(defaultValue = "480") val freeTopicQuietEnd: Int = 480,
    /** May note things down to come back to, and say them on its own when they come due (ai/Later.kt). */''')
rep(entities,
'''@Serializable
@Entity(tableName = "conversations", indices = [Index("companionId")])''',
'''/** Scheduling state is ephemeral and intentionally not part of portable backups. */
@Entity(
    tableName = "free_topics",
    foreignKeys = [ForeignKey(entity = CompanionEntity::class, parentColumns = ["id"], childColumns = ["companionId"], onDelete = ForeignKey.CASCADE)],
)
data class FreeTopicStateEntity(
    @PrimaryKey val companionId: Long,
    val nextAt: Long,
    val attemptDay: Long? = null,
    val attempts: Int = 0,
)

@Serializable
@Entity(tableName = "conversations", indices = [Index("companionId")])''')
rep(entities,
'''    val recapUntilAt: Long? = null,
    val recapUntilId: Long? = null,
)''',
'''    val recapUntilAt: Long? = null,
    val recapUntilId: Long? = null,
    @kotlinx.serialization.Transient val followUpMessageId: Long? = null,
    @kotlinx.serialization.Transient val followUpAt: Long? = null,
)''')

db="app/src/main/java/com/cleo/cleos/data/db/AppDatabase.kt"
rep(db,
'''        StickerEntity::class,
    ],
    version = 16,''',
'''        StickerEntity::class,
        FreeTopicStateEntity::class,
    ],
    version = 17,''')
rep(db,
'''        AutoMigration(from = 15, to = 16),
    ],''',
'''        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
    ],''')
rep(db,
'''abstract class AppDatabase : RoomDatabase() {
    abstract fun companions(): CompanionDao''',
'''abstract class AppDatabase : RoomDatabase() {
    abstract fun freeTopics(): FreeTopicDao
    abstract fun companions(): CompanionDao''')

daos="app/src/main/java/com/cleo/cleos/data/db/Daos.kt"
rep(daos,
'''@Dao
interface ConversationDao {''',
'''@Dao
interface FreeTopicDao {
    @Query("SELECT * FROM free_topics WHERE companionId = :id")
    suspend fun get(id: Long): FreeTopicStateEntity?

    @Upsert
    suspend fun put(state: FreeTopicStateEntity)

    @Query("UPDATE free_topics SET nextAt = :next WHERE companionId = :id AND nextAt = :expected")
    suspend fun move(id: Long, expected: Long, next: Long): Int

    @Query("UPDATE free_topics SET nextAt = :next, attemptDay = :day, attempts = CASE WHEN attemptDay = :day THEN attempts + 1 ELSE 1 END WHERE companionId = :id AND nextAt = :expected AND (attemptDay IS NOT :day OR attempts < :maximum)")
    suspend fun claim(id: Long, expected: Long, next: Long, day: Long, maximum: Int): Int

    @Query("DELETE FROM free_topics")
    suspend fun clear()
}

@Dao
interface ConversationDao {''')
rep(daos,
'''    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)
''',
'''    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE conversations SET followUpMessageId = :anchor, followUpAt = :at WHERE id = :id")
    suspend fun planFollowUp(id: Long, anchor: Long, at: Long)

    @Query("UPDATE conversations SET followUpMessageId = NULL, followUpAt = NULL WHERE id = :id")
    suspend fun cancelFollowUp(id: Long)

    @Query("UPDATE conversations SET followUpMessageId = NULL, followUpAt = NULL WHERE id = :id AND followUpMessageId = :anchor AND followUpAt <= :now")
    suspend fun claimFollowUp(id: Long, anchor: Long, now: Long): Int
''')
rep(daos,
'''@Dao
interface MessageDao {
''',
'''@Dao
interface MessageDao {
    @Query("SELECT MAX(m.createdAt) FROM messages m JOIN conversations c ON c.id = m.conversationId WHERE c.companionId = :companionId AND m.role = 'user' AND m.note IS NULL")
    suspend fun lastUserFor(companionId: Long): Long?

    @Query("SELECT MAX(m.createdAt) FROM messages m JOIN conversations c ON c.id = m.conversationId WHERE c.companionId = :companionId AND m.role IN ('user', 'assistant', 'pat', 'call') AND m.note IS NULL")
    suspend fun lastActivityFor(companionId: Long): Long?

    @Query("SELECT m.* FROM messages m JOIN conversations c ON c.id = m.conversationId WHERE c.companionId = :companionId AND m.role = 'assistant' AND m.proactive = 1 AND m.error IS NULL AND m.content != '' ORDER BY m.createdAt DESC, m.id DESC LIMIT :limit")
    suspend fun recentProactiveFor(companionId: Long, limit: Int): List<MessageEntity>
''')
rep(daos,
'''    @Query("SELECT * FROM wakes WHERE companionId = :companionId ORDER BY at DESC, id DESC LIMIT 1")
    fun observeLatest(companionId: Long): Flow<WakeEntity?>

    /** Times a TA's wakes said something since [since]. */''',
'''    @Query("SELECT * FROM wakes WHERE companionId = :companionId ORDER BY at DESC, id DESC LIMIT 1")
    fun observeLatest(companionId: Long): Flow<WakeEntity?>

    @Query("SELECT * FROM wakes WHERE companionId = :companionId ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeRecent(companionId: Long, limit: Int): Flow<List<WakeEntity>>

    /** Times a TA's wakes said something since [since]. */''')
rep(daos,
'''    /** All but the newest [keep] of a TA's. */
    @Query(
        "DELETE FROM wakes WHERE companionId = :companionId AND id NOT IN " +
            "(SELECT id FROM wakes WHERE companionId = :companionId ORDER BY at DESC, id DESC LIMIT :keep)",
    )
    suspend fun prune(companionId: Long, keep: Int)''',
'''    /** Keep recent status plus the last two sent turns, so quiet checks cannot erase the unanswered fuse. */
    @Query(
        "DELETE FROM wakes WHERE companionId = :companionId AND id NOT IN " +
            "(SELECT id FROM wakes WHERE companionId = :companionId ORDER BY at DESC, id DESC LIMIT :keep) " +
            "AND id NOT IN (SELECT id FROM wakes WHERE companionId = :companionId AND outcome = 'sent' ORDER BY at DESC, id DESC LIMIT 2)",
    )
    suspend fun prune(companionId: Long, keep: Int)''')

chat="app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
rep(chat,
'''    private val replied: suspend (ta: CompanionEntity, conversationId: Long, said: List<MessageEntity>) -> Unit = { _, _, _ -> },
    /** The song playing''',
'''    private val replied: suspend (ta: CompanionEntity, conversationId: Long, said: List<MessageEntity>) -> Unit = { _, _, _ -> },
    private val interrupted: (Long) -> Unit = {},
    /** The song playing''')
rep(chat,
'''    fun typing(conversationId: Long, now: Boolean) {
        if (now) typingIn += conversationId else typingIn -= conversationId
    }''',
'''    fun typing(conversationId: Long, now: Boolean) {
        if (now) {
            cancelFollowUp(conversationId)
            interrupted(conversationId)
            typingIn += conversationId
        } else typingIn -= conversationId
    }''')
rep(chat,
'''    private fun answerSoon(conversationId: Long) {
        synchronized(lock) {''',
'''    private fun answerSoon(conversationId: Long) {
        cancelFollowUp(conversationId)
        interrupted(conversationId)
        synchronized(lock) {''')
rep(chat,
'''    suspend fun wake(conversationId: Long, instruction: String): WakeResult {
        val outcome = CompletableDeferred<WakeResult>()
        synchronized(lock) {
            if (busy(conversationId)) return WakeResult.Busy
            val seen = sends[conversationId]
            launchFor(conversationId) {
                outcome.complete(
                    try {
                        wakeTurn(conversationId, instruction)
                    } catch (e: CancellationException) {
                        outcome.complete(WakeResult.Failed(STOPPED))
                        throw e
                    } catch (e: Exception) {
                        WakeResult.Failed(e.message ?: e.javaClass.simpleName)
                    },
                )
                synchronized(lock) {
                    // Nothing sent meanwhile: done, off the map under the lock (see answerUntilQuiet).
                    if (sends[conversationId] == seen) {
                        jobs.remove(conversationId, coroutineContext.job)
                        return@launchFor
                    }
                }
                answerUntilQuiet(conversationId)
            }
            // Stopped before it began (its TA deleted meanwhile): an answer all the same.
            jobs[conversationId]?.invokeOnCompletion { outcome.complete(WakeResult.Failed(STOPPED)) }
        }
        return outcome.await()
    }''',
'''    private val followUpJobs = ConcurrentHashMap<Long, Job>()

    fun cancelFollowUp(conversationId: Long) {
        synchronized(lock) { followUpJobs.remove(conversationId)?.cancel() }
    }

    suspend fun wake(
        conversationId: Long,
        instruction: String,
        followUp: Boolean = false,
        allowed: suspend () -> Boolean = { true },
    ): WakeResult {
        val outcome = CompletableDeferred<WakeResult>()
        synchronized(lock) {
            if (busy(conversationId)) return WakeResult.Busy
            val seen = sends[conversationId]
            launchFor(conversationId) {
                outcome.complete(
                    try {
                        if (allowed()) wakeTurn(conversationId, instruction) else WakeResult.Skipped("这次主动机会已取消")
                    } catch (e: CancellationException) {
                        outcome.complete(WakeResult.Failed(STOPPED))
                        throw e
                    } catch (e: Exception) {
                        WakeResult.Failed(e.message ?: e.javaClass.simpleName)
                    },
                )
                followUpJobs.remove(conversationId, coroutineContext.job)
                synchronized(lock) {
                    if (sends[conversationId] == seen) {
                        jobs.remove(conversationId, coroutineContext.job)
                        return@launchFor
                    }
                }
                answerUntilQuiet(conversationId)
            }
            if (followUp) jobs[conversationId]?.let { job ->
                followUpJobs[conversationId] = job
                job.invokeOnCompletion { followUpJobs.remove(conversationId, job) }
            }
            jobs[conversationId]?.invokeOnCompletion { outcome.complete(WakeResult.Failed(STOPPED)) }
        }
        return outcome.await()
    }''')
rep(chat,
'''                            conversationId, AssistantBubbleSplitter.split(text, MAX_MESSAGES),
                            step.thought?.let(MessageThoughts::encode), proactive = true, quiet = true,''',
'''                            conversationId, AssistantBubbleSplitter.split(text, 3),
                            step.thought?.let(MessageThoughts::encode), proactive = true, quiet = true,''')

app="app/src/main/java/com/cleo/cleos/CleosApp.kt"
rep(app,
'''        replied = { ta, conversationId, said -> if (!(visible && chatOnScreen == conversationId)) notifier.messages(ta, conversationId, said) },
        listening = { Listening(music, lyrics).line() },''',
'''        replied = { ta, conversationId, said ->
            if (!(visible && chatOnScreen == conversationId)) notifier.messages(ta, conversationId, said)
            followUps.plan(ta, conversationId, said)
        },
        interrupted = { followUps.cancel(it); freeTopics.interrupt(it) },
        listening = { Listening(music, lyrics).line() },''')
rep(app,
'''    val letters = Letters(db, settings, secrets, chatClient, appScope, written = { later.letterWritten(it) })''',
'''    val followUps = com.cleo.cleos.ai.FollowUps(context, db, chat, notifier, appScope) { id -> visible && chatOnScreen == id }
    val freeTopics = com.cleo.cleos.ai.FreeTopics(
        context, db, chat, notifier, appScope,
        showing = { id -> visible && chatOnScreen == id },
        onEnabled = { later.wantsNotifications.value = true },
    )
    val letters = Letters(db, settings, secrets, chatClient, appScope, written = { later.letterWritten(it) })''')
rep(app,
'''        // Notes still waiting and letters on their way get their background work back, if it was lost.
        later.reconcile()''',
'''        // Restore all still-valid proactive background work.
        later.reconcile()
        followUps.restore()
        freeTopics.restore()''')

vm="app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt"
rep(vm,
'''    var deepThinking by mutableStateOf(false)
        private set
    var proactive by mutableStateOf(true)''',
'''    var followUpEnabled by mutableStateOf(false)
    var followUpDelaySeconds by mutableStateOf(60)
    var freeTopicEnabled by mutableStateOf(false)
    var freeTopicLevel by mutableStateOf(com.cleo.cleos.ai.FreeTopicRules.PERSONALITY)
    var freeTopicQuietOn by mutableStateOf(true)
    var freeTopicQuietStart by mutableStateOf(1380)
    var freeTopicQuietEnd by mutableStateOf(480)
    var deepThinking by mutableStateOf(false)
        private set
    var proactive by mutableStateOf(true)''')
rep(vm,
'''    val lastWake: StateFlow<WakeEntity?> = snapshotFlow { companionId }
        .flatMapLatest { c.db.wakes().observeLatest(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
''',
'''    val lastWake: StateFlow<WakeEntity?> = snapshotFlow { companionId }
        .flatMapLatest { c.db.wakes().observeLatest(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val wakeHistory: StateFlow<List<WakeEntity>> = snapshotFlow { companionId }
        .flatMapLatest { c.db.wakes().observeRecent(it, 50) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
''')
rep(vm,
'''        persona = ta.persona
        deepThinking = ta.deepThinking
        proactive = ta.proactive''',
'''        persona = ta.persona
        followUpEnabled = ta.followUpEnabled
        followUpDelaySeconds = com.cleo.cleos.ai.FollowUpRules.seconds(ta.followUpDelaySeconds)
        freeTopicEnabled = ta.freeTopicEnabled
        freeTopicLevel = com.cleo.cleos.ai.FreeTopicRules.level(ta.freeTopicLevel).id
        freeTopicQuietOn = ta.freeTopicQuietOn
        freeTopicQuietStart = com.cleo.cleos.ai.FreeTopicRules.minute(ta.freeTopicQuietStart, 1380)
        freeTopicQuietEnd = com.cleo.cleos.ai.FreeTopicRules.minute(ta.freeTopicQuietEnd, 480)
        deepThinking = ta.deepThinking
        proactive = ta.proactive''')
rep(vm,
'''    /** Takes effect at once, like the tool switches. */
    fun setThinking(on: Boolean) {''',
'''    fun setFollowUp(on: Boolean) {
        followUpEnabled = on
        val id = companionId
        viewModelScope.launch {
            c.companions.update(id) { it.copy(followUpEnabled = on) }
            if (!on) c.followUps.cancelFor(id)
        }
    }

    fun setFollowUpDelay(seconds: Int) {
        val v = com.cleo.cleos.ai.FollowUpRules.seconds(seconds)
        followUpDelaySeconds = v
        val id = companionId
        viewModelScope.launch { c.companions.update(id) { it.copy(followUpDelaySeconds = v) } }
    }

    fun setFreeTopic(on: Boolean) {
        freeTopicEnabled = on
        changeFreeTopic { it.copy(freeTopicEnabled = on) }
    }

    fun chooseFreeTopicLevel(level: Int) {
        val v = com.cleo.cleos.ai.FreeTopicRules.level(level).id
        freeTopicLevel = v
        changeFreeTopic { it.copy(freeTopicLevel = v) }
    }

    fun setFreeTopicQuiet(on: Boolean) {
        freeTopicQuietOn = on
        changeFreeTopic { it.copy(freeTopicQuietOn = on) }
    }

    fun setFreeTopicTime(start: Boolean, minutes: Int) {
        val v = com.cleo.cleos.ai.FreeTopicRules.minute(minutes, if (start) 1380 else 480)
        if (start) freeTopicQuietStart = v else freeTopicQuietEnd = v
        changeFreeTopic { if (start) it.copy(freeTopicQuietStart = v) else it.copy(freeTopicQuietEnd = v) }
    }

    private fun changeFreeTopic(change: (CompanionEntity) -> CompanionEntity) {
        val id = companionId
        viewModelScope.launch {
            c.companions.update(id, change)
            c.freeTopics.configure(id)
        }
    }

    /** Takes effect at once, like the tool switches. */
    fun setThinking(on: Boolean) {''')

pages="app/src/main/java/com/cleo/cleos/ui/settings/TaPages.kt"
rep(pages,
'''    if (vm.proactive) {
        Section("主动找你的时候") { ReachOutStatus(vm) }
    }
}''',
'''    Section("聊完再说一点") {
        ExplainedSwitch(
            "聊完再说一点",
            "正常回复结束后，再给 TA 一次自己决定是否补充的机会",
            "默认关闭。每轮正常回复最多一次；你开始输入或发送新消息就取消，补充不会继续触发补充。",
            vm.followUpEnabled,
        ) { vm.setFollowUp(it) }
        if (vm.followUpEnabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.cleo.cleos.ai.FollowUpRules.OPTIONS.forEach { seconds ->
                    Chip(if (seconds == 30) "30 秒" else (seconds / 60).toString() + " 分钟", selected = vm.followUpDelaySeconds == seconds) {
                        vm.setFollowUpDelay(seconds)
                    }
                }
            }
        }
    }
    FreeTopicSection(vm)
    if (vm.proactive) {
        Section("主动找你的时候") { ReachOutStatus(vm) }
    }
    ProactiveHistorySection(vm)
}''')

# Restoring a portable backup keeps user settings but discards stale scheduler runtime state.
rep(vm,
'''                        try { action() } finally { reloadBackupFields() }''',
'''                        try {
                            val restored = action()
                            c.freeTopics.resetAfterRestore()
                            restored
                        } finally { reloadBackupFields() }''')

print("人格驱动主动聊天补丁已应用。")
