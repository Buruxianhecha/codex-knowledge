package com.cleo.cleos.ai

import androidx.room.withTransaction

import com.cleo.cleos.data.AppSettings
import com.cleo.cleos.data.CallRecord
import com.cleo.cleos.data.CallRecords
import com.cleo.cleos.data.Companions
import com.cleo.cleos.data.ImageStore
import com.cleo.cleos.data.LifeStore
import com.cleo.cleos.data.LifePending
import com.cleo.cleos.data.MessageAudio
import com.cleo.cleos.data.MessageAudios
import com.cleo.cleos.data.MessageImage
import com.cleo.cleos.data.MessageImages
import com.cleo.cleos.data.ChatTables
import com.cleo.cleos.data.MessageQuote
import com.cleo.cleos.data.MessageQuotes
import com.cleo.cleos.data.MessageReactions
import com.cleo.cleos.data.MessageThought
import com.cleo.cleos.data.MessageThoughts
import com.cleo.cleos.data.PatRecord
import com.cleo.cleos.data.Pats
import com.cleo.cleos.data.Recalls
import com.cleo.cleos.data.MessageEdits
import com.cleo.cleos.data.MessageEditException
import com.cleo.cleos.data.ReactionEvents
import com.cleo.cleos.data.GroupReactionRules
import com.cleo.cleos.data.SecretStore
import com.cleo.cleos.data.SettingsRepository
import com.cleo.cleos.data.StickerBook
import com.cleo.cleos.data.StickerText
import com.cleo.cleos.data.db.AppDatabase
import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.ConversationMemberEntity
import com.cleo.cleos.data.db.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** The reply being written right now. */
data class StreamingReply(
    val conversationId: Long,
    val text: String,
    val thinking: Boolean,
    /** Set once [text] has been stored; the screen then shows the stored copy instead. */
    val savedId: Long? = null,
    /** A tool running between two parts of the reply: 在查天气. */
    val activity: String? = null,
    /** The reply is over; this is only the hand-over to the stored bubble. */
    val finished: Boolean = false,
    /** A call to an outside service waiting for the person to allow it. */
    val asking: McpAsk? = null,
    /** What the model has thought so far, before [text]. */
    val thought: String = "",
    /** How long it thought, once it is done thinking; null while it still is. */
    val thoughtMs: Long? = null,
)

/**
 * Sending and receiving. Replies run in the app-wide scope, not a screen's: switching
 * to the diary mid-reply should not cut the reply off.
 *
 * One reply at a time per conversation, but conversations don't wait for each other: the
 * person can go and talk to another TA while the first one is still answering.
 *
 * A reply with tools is a loop: the model answers with text, tool calls or both; the
 * calls run, their results go back, and the model continues, until it answers without
 * calling anything. Each step is stored as it happens (the text, the calls, every
 * result), so what was done survives a crash or a stop, and a retry continues from the
 * results instead of adding the same todo twice.
 */
class ChatRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val client: ChatClient,
    private val tools: ToolBox,
    private val images: ImageStore,
    private val companions: Companions,
    private val recaps: Recaps,
    private val mcp: McpHub,
    private val transcriber: Transcriber,
    private val speaker: Speaker,
    private val scope: CoroutineScope,
    private val personalityGrowth: PersonalityGrowth? = null,
    private val goalStore: GoalStore? = null,
    private val life: LifeStore? = null,
    /**
     * A reply has been written: what the TA said in it, in order. The app shows it as a
     * notification unless that chat is on screen (AppContainer).
     */
    private val replied: suspend (ta: CompanionEntity, conversationId: Long, said: List<MessageEntity>) -> Unit = { _, _, _ -> },
    private val interrupted: (Long) -> Unit = {},
    /** The song playing, as a reply is told it (Listening), while 一起听歌 is on; null when nothing plays. */
    private val listening: suspend () -> String? = { null },
) {
    /** The replies being written, by conversation. */
    private val _streaming = MutableStateFlow<Map<Long, StreamingReply>>(emptyMap())
    val streaming: StateFlow<Map<Long, StreamingReply>> = _streaming.asStateFlow()

    /** A message found somewhere else (a search) that the chat should bring into view. */
    data class Focus(val conversationId: Long, val messageId: Long)

    private val _focus = MutableStateFlow<Focus?>(null)
    val focus: StateFlow<Focus?> = _focus.asStateFlow()

    /** Opens [message]'s conversation and asks the chat to show it. */
    suspend fun show(message: MessageEntity) {
        settings.setCurrentConversation(message.conversationId)
        _focus.value = Focus(message.conversationId, message.id)
    }

    /** The chat has shown it (or given up): nothing to bring into view any more. */
    fun shown() {
        _focus.value = null
    }

    /** Voice messages being turned into text, by message id. */
    private val _transcribing = MutableStateFlow<Set<Long>>(emptySet())
    val transcribing: StateFlow<Set<Long>> = _transcribing.asStateFlow()
    private val jobs = ConcurrentHashMap<Long, Job>()
    /** Which exact goal VERSION is executing this conversation; stale callbacks cannot commit. */
    private val goalExecuting = ConcurrentHashMap<Long, Pair<String, Int>>()
    private val recalling = ConcurrentHashMap.newKeySet<Long>()
    private val recallCoordinator by lazy {
        RecallCoordinator(
            pause = { id -> synchronized(lock) { recalling += id } },
            stop = { id -> jobs[id]?.cancelAndJoin() },
            resume = { id ->
                synchronized(lock) { recalling -= id }
                answerSoon(id)
            },
        )
    }

    /**
     * Endpoint and model pairs that turned down a request with tools, in this run of the
     * app. They are asked without tools from then on instead of failing every message.
     */
    private val refusesTools = ConcurrentHashMap.newKeySet<String>()

    /** The same for pictures: models that can't look at images are sent the text only. */
    private val refusesImages = ConcurrentHashMap.newKeySet<String>()

    /** And for the thinking switch: models that don't take it are asked the plain way. */
    private val refusesThinking = ConcurrentHashMap.newKeySet<String>()

    /** The person's answer to a card asking whether the TA may use an outside service's tool. */
    enum class Answer { Yes, Always, No }

    /** Calls waiting on the person, by conversation. */
    private val asks = ConcurrentHashMap<Long, CompletableDeferred<Answer>>()

    /** The conversation a phone call is on in (ai/Call.kt), if any. */
    @Volatile
    private var calling: Long? = null

    /** Whether a reply is waiting or under way in [conversationId], including while its tools run, or a call is on there. */
    fun busy(conversationId: Long): Boolean = conversationId in recalling || jobs[conversationId]?.isActive == true || calling == conversationId

    /** Guards starting and ending a conversation's job, which messages sent from anywhere can race. */
    private val lock = Any()

    /** Runs [block] as [conversationId]'s reply; false, and nothing runs, while one is under way there. */
    private fun start(conversationId: Long, block: suspend () -> Unit): Boolean {
        synchronized(lock) {
            if (busy(conversationId)) return false
            launchFor(conversationId) { block() }
            return true
        }
    }

    /**
     * Conversations with a reply (or a wake) waiting or under way: while there are any, leaving the
     * app keeps it running until they are done (ReplyKeeper).
     */
    private val _working = MutableStateFlow<Set<Long>>(emptySet())
    val working: StateFlow<Set<Long>> = _working.asStateFlow()

    /** [block] as [conversationId]'s job: registered before it starts, so a quick one can't finish before it is on the map. */
    private fun launchFor(conversationId: Long, block: suspend CoroutineScope.() -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { block() } catch (_: LifeDeferred) { hide(conversationId) }
        }
        jobs[conversationId] = job
        _working.update { it + conversationId }
        job.invokeOnCompletion {
            jobs.remove(conversationId, job)
            // Unless the next one here has started meanwhile.
            if (jobs[conversationId] == null) _working.update { it - conversationId }
        }
        job.start()
    }

    // Several messages in a row, the way people send them: the reply waits until the person
    // has stopped for a moment, and whatever they send while it is being written is answered
    // right after it.

    /** How many times the person has sent something, by conversation: a reply can tell whether more came meanwhile. */
    private val sends = ConcurrentHashMap<Long, Long>()

    /** When they last sent something, by conversation. */
    private val lastSent = ConcurrentHashMap<Long, Long>()

    /** Conversations where the person is typing right now. */
    private val typingIn = ConcurrentHashMap.newKeySet<Long>()

    /** Voice messages still being turned into text, counted by conversation: a reply waits for their words. */
    private val holds = ConcurrentHashMap<Long, Int>()

    /** The newest of the person's messages the last reply here took in (its time), by conversation. */
    private val answeredUpTo = ConcurrentHashMap<Long, Long>()

    private val lastStamp = AtomicLong(0)

    /** Now, but always after the last one handed out: messages sent in quick succession keep their order. */
    private fun stamp(): Long = lastStamp.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) }

    /** Whether the person is writing something in [conversationId]: a reply waits for them, a while. */
    fun typing(conversationId: Long, now: Boolean) {
        if (now) {
            cancelFollowUp(conversationId)
            interrupted(conversationId)
            typingIn += conversationId
        } else typingIn -= conversationId
    }

    /** Whether something is in the person's input box in [conversationId] right now. */
    fun isTyping(conversationId: Long): Boolean = conversationId in typingIn

    /**
     * The person sent something here: a reply comes once they have stopped for a moment. One
     * waiting already just waits a little longer; one being written is followed by another,
     * for what came meanwhile.
     */
    private fun answerSoon(conversationId: Long) {
        cancelFollowUp(conversationId)
        interrupted(conversationId)
        synchronized(lock) {
            sends.merge(conversationId, 1L) { a, b -> a + b }
            lastSent[conversationId] = System.currentTimeMillis()
            if (busy(conversationId)) return
            launchFor(conversationId) { answerUntilQuiet(conversationId) }
        }
    }

    /** Replies, as the conversation's job, until the person has sent nothing that is not answered. */
    private suspend fun answerUntilQuiet(conversationId: Long) {
        val job = currentCoroutineContext().job
        while (true) {
            awaitQuiet(conversationId)
            val seen = sends[conversationId]
            if (db.conversations().get(conversationId) != null && unanswered(conversationId)) reply(conversationId)
            synchronized(lock) {
                // Nothing sent meanwhile: done. Off the map here, under the lock, so a message
                // sent from now on starts a reply of its own instead of counting on this one.
                if (sends[conversationId] == seen) {
                    jobs.remove(conversationId, job)
                    return
                }
            }
        }
    }

    /**
     * Until the person has stopped: [REPLY_WAIT] since they last sent something, and not
     * typing or waiting on a voice message's words, which hold it up to [HOLD_MAX] at most.
     */
    private suspend fun awaitQuiet(conversationId: Long) {
        while (true) {
            val since = System.currentTimeMillis() - (lastSent[conversationId] ?: 0L)
            val held = (conversationId in typingIn || (holds[conversationId] ?: 0) > 0) &&
                db.messages().newest(conversationId, 1).none {
                    Recalls.isEvent(it) || ReactionEvents.isEvent(it) || WalletReactiveEvents.isEvent(it)
                }
            if (since >= REPLY_WAIT && (!held || since >= HOLD_MAX)) return
            delay(if (since < REPLY_WAIT) REPLY_WAIT - since else HOLD_CHECK)
        }
    }

    /** Whether the person has said or done something since what the last reply took in. */
    private suspend fun unanswered(conversationId: Long): Boolean {
        val upTo = answeredUpTo[conversationId] ?: Long.MIN_VALUE
        return db.messages().newest(conversationId, UNANSWERED_LOOKBACK).any { m ->
            val userMessage = m.role == "user" && m.note == null && m.error == null &&
                (m.content.isNotBlank() || m.images != null)
            val patOnTa = m.role == "pat" && Pats.decode(m.content)?.who == Pats.AI
            m.createdAt > upTo && (userMessage || patOnTa || Recalls.isEvent(m) || ReactionEvents.isEvent(m))
        }
    }

    /** [block] with the reply here held back (a voice message being turned into words). */
    private suspend fun <T> holding(conversationId: Long, block: suspend () -> T): T {
        holds.merge(conversationId, 1) { a, b -> a + b }
        try {
            return block()
        } finally {
            holds.compute(conversationId) { _, n -> if (n == null || n <= 1) null else n - 1 }
        }
    }

    private fun show(reply: StreamingReply) = _streaming.update { it + (reply.conversationId to reply) }

    private fun hide(conversationId: Long) = _streaming.update { it - conversationId }

    private class LifeDeferred : RuntimeException()

    private suspend fun lifeCanContinue(conversationId: Long, actor: Long, purpose: String): Boolean {
        if (life?.availability(actor)?.isBusy != true) return true
        val runningGoal = goalExecuting[conversationId]?.let { goalStore?.byId(it.first) }
        if (runningGoal != null) deferGoalForLife(runningGoal)
            else deferForLife(conversationId, actor, purpose)
        return false
    }

    /** Only event cursors live in the queue. The original Room rows remain authoritative. */
    private fun lifeInput(m: MessageEntity): Boolean = m.call == null && m.error == null &&
        ((m.role == "user" && (m.content.isNotBlank() || m.images != null || m.audio != null)) ||
            (m.role == "pat" && Pats.decode(m.content)?.who == Pats.AI) ||
            Recalls.isEvent(m) || ReactionEvents.isEvent(m))

    private suspend fun latestLifeInput(conversationId: Long): MessageEntity? =
        db.messages().newest(conversationId, 1000).firstOrNull(::lifeInput)

    /** Initialize only to an actual saved reply; enabling life must never replay old history. */
    private suspend fun lifeBaseline(companionId: Long, conversationId: Long) {
        val store = life ?: return
        val room = db.conversations().get(conversationId) ?: return
        val rows = db.messages().newest(conversationId, 5000)
        // Final assistant rows contain the exact consumed input cursor in the same Room
        // write. A process death before the AtomicFile update cannot duplicate the reply.
        val receipt = rows.asSequence().filter {
            it.role == "assistant" && it.error == null && it.call == null
        }.mapNotNull { LifeGateRules.receiptToken(it.toolCallId) }
            .filter { it.first == companionId }
            .maxWithOrNull(compareBy<Triple<Long, Long, Long>> { it.second }.thenBy { it.third })
        if (receipt != null) store.recordAnswer(companionId, conversationId, receipt.second, receipt.third)
        if (store.answeredCursor(companionId, conversationId) > 0L) return
        val previous = rows.firstOrNull {
            it.role == "assistant" && it.call == null && !it.proactive && it.error == null &&
                (it.content.isNotBlank() || it.images != null || it.audio != null) &&
                (!room.isGroup || (it.senderCompanionId ?: room.companionId) == companionId)
        } ?: return
        store.recordAnswer(companionId, conversationId, previous.createdAt, previous.id)
    }

    /** This is a real request gate, before credentials, tool discovery and model generation. */
    private suspend fun deferForLife(conversationId: Long, companionId: Long,
        purpose: String = "reply", input: MessageEntity? = null): Boolean {
        val store = life ?: return false
        store.tick()
        if (!store.availability(companionId).isBusy) return false
        if (purpose == "passive") { hide(conversationId); return true }
        val event = input ?: latestLifeInput(conversationId)
        if (purpose == "reply") {
            if (event == null) { hide(conversationId); return true }
            lifeBaseline(companionId, conversationId)
        }
        store.enqueue(companionId, conversationId, event?.createdAt ?: System.currentTimeMillis(),
            purpose = purpose, messageId = event?.id ?: 0L)
        hide(conversationId)
        return true
    }

    private suspend fun consumedLife(companionId: Long, conversationId: Long,
        input: MessageEntity?) {
        val store = life ?: return
        val event = lifeBatchEnd.remove(companionId to conversationId) ?: input ?: return
        store.recordAnswer(companionId, conversationId, event.createdAt, event.id)
        // Complete only the snapshot actually answered. A concurrent new input stays queued.
        for (pending in store.profile(companionId).pending.filter {
            it.conversationId == conversationId &&
                (it.purpose == "reply" || it.purpose.startsWith("retry:"))
        }) store.completePending(companionId, pending.id, event.createdAt, event.id)
    }

    private val lifeBatchEnd = ConcurrentHashMap<Pair<Long, Long>, MessageEntity>()
    private val lifeTurnInputs = ConcurrentHashMap<Long, Pair<Long, MessageEntity>>()

    private fun lifeReceipt(conversationId: Long): String? {
        if (goalExecuting.containsKey(conversationId)) return null
        val (actor, event) = lifeTurnInputs[conversationId] ?: return null
        val end = lifeBatchEnd[actor to conversationId] ?: event
        return "life-cursor:$actor:${end.createdAt}:${end.id}"
    }

    private suspend fun recordLifeQuietDecision(conversationId: Long, actor: Long) {
        val receipt = lifeReceipt(conversationId) ?: return
        // A silent control row, not an invented visible reply. Its marker is durable in Room.
        db.messages().insert(MessageEntity(conversationId = conversationId,
            role = "assistant", content = "", createdAt = stamp(),
            toolCallId = receipt, senderCompanionId = actor))
    }

    /** Gather pending user/event rows in chronological order, across recap/window boundaries. */
    private suspend fun deferredLifeContext(companionId: Long, conversationId: Long): String? {
        val store = life ?: return null
        if (store.profile(companionId).pending.none {
                it.conversationId == conversationId && it.purpose == "reply" }) return null
        lifeBaseline(companionId, conversationId)
        val after = store.answeredCursor(companionId, conversationId).coerceAtLeast(0L)
        val afterId = store.answeredMessageId(companionId, conversationId)
        val rows = db.messages().after(conversationId, after, afterId)
            .filter(::lifeInput)
        if (rows.isEmpty()) return null
        lifeBatchEnd[companionId to conversationId] = rows.take(80).last()
        return buildString {
            appendLine("【生活日程结束后待回复的真实输入】你先前因工作、休息或睡眠没有完整处理这些输入。按下面时间顺序综合回复；如历史已有本轮的部分气泡或工具结果，只补充尚未交付部分，不要重发已发送文字或重做有真实结果的工具。")
            // A bounded prompt never silently treats omitted inputs as read.
            for (m in rows.take(80)) {
                appendLine("消息 #${m.id} / ${m.createdAt} / ${m.role}：${m.content.take(1200)}")
                if (m.images != null) appendLine("此输入包含图片；图片仍在真实聊天历史中。")
                if (m.audio != null) appendLine("此输入包含语音；转写内容如上。")
            }
            if (rows.size > 80) appendLine("另有 ${rows.size - 80} 条输入未在本段展开。请通过聊天历史工具读取，不可声称已阅读。")
        }
    }

    /** Durable goal token: a later manual pause/edit/cancel must defeat automatic recovery. */
    private suspend fun deferGoalForLife(goal: GoalRecord): Boolean {
        val store = life ?: return false
        store.tick()
        val availability = store.availability(goal.companionId)
        if (!availability.isBusy) return false
        store.enqueue(goal.companionId, goal.conversationId, goal.updatedAt,
            purpose = "goal:${goal.id}:${goal.version}")
        goalStore?.checkpoint(goal.id, goal.version,
            kotlinx.serialization.json.buildJsonObject {
                put("blocking_reason", LIFE_GOAL_WAIT + availability.label + "，恢复可用后自动继续原检查点。")
            })
        hide(goal.conversationId)
        return true
    }

    private val lifeRecovery = Mutex()

    /** Awaited by WorkManager; no model job is left behind after the worker claims success. */
    suspend fun resumeLifePending() {
        if (!lifeRecovery.tryLock()) return
        try {
            val store = life ?: return
            store.tick()
            kotlinx.coroutines.coroutineScope {
                store.pendingReady().groupBy { it.second.conversationId }.map { (conversationId, ready) ->
                    async { recoverLifeConversation(store, conversationId, ready) }
                }.awaitAll()
            }
        } finally { lifeRecovery.unlock() }
    }

    private suspend fun recoverLifeConversation(store: LifeStore, conversationId: Long,
        ready: List<Pair<Long, LifePending>>) {
        if (busy(conversationId)) return
        val room = db.conversations().get(conversationId)
        if (room == null) {
            for ((actor, item) in ready) store.completePending(actor, item.id)
            return
        }
        val members = if (room.isGroup) db.groupMembers().idsFor(conversationId).toSet()
            else setOf(room.companionId)
        val valid = ready.filter { (actor, item) ->
            if (actor !in members) {
                store.completePending(actor, item.id)
                false
            } else true
        }
        val replies = valid.filter { it.second.purpose == "reply" }.filter { (actor, item) ->
            lifeBaseline(actor, conversationId)
            val row = latestLifeInput(conversationId)
            val done = LifeGateRules.atOrBefore(item.messageCursor, item.messageId,
                store.answeredCursor(actor, conversationId), store.answeredMessageId(actor, conversationId))
            if (row == null || done) {
                store.completePending(actor, item.id, item.messageCursor, item.messageId)
                false
            } else true
        }
        val dispatch = replies.filter { (actor, item) ->
            store.beginPendingAttempt(actor, item.id)
        }
        if (dispatch.isNotEmpty()) {
            val actors = dispatch.map { it.first }.toSet()
            val done = CompletableDeferred<Unit>()
            val launched = start(conversationId) {
                try {
                    if (room.isGroup) groupReply(conversationId, room, resumeActors = actors)
                    else reply(conversationId)
                } finally { done.complete(Unit) }
            }
            if (launched) done.await()
        }
        for ((actor, item) in valid.filter { it.second.purpose.startsWith("retry:") }) {
            val messageId = item.purpose.removePrefix("retry:").toLongOrNull()
            if (messageId == null || db.messages().get(messageId) == null) {
                store.completePending(actor, item.id)
            } else if (!busy(conversationId) && store.beginPendingAttempt(actor, item.id)) {
                retry(conversationId, messageId)
                jobs[conversationId]?.join()
            }
        }
        for ((actor, item) in valid.filter { it.second.purpose.startsWith("goal:") }) {
            val token = LifeGateRules.goalToken(item.purpose)
            val goal = token?.let { goalStore?.byId(it.first) }
            if (token == null || goal == null || goal.version != token.second ||
                goal.companionId != actor || goal.conversationId != conversationId ||
                goal.status != "BLOCKED" || !goal.lastError.startsWith(LIFE_GOAL_WAIT) ||
                goal.inFlight != null || goal.pending != null) {
                store.completePending(actor, item.id)
                continue
            }
            if (busy(conversationId) || store.availability(actor).isBusy) continue
            if (goalStore?.resume(goal.id)?.status == "RUNNING") {
                // Remove the old scheduling marker before execution. If it is busy again,
                // deferGoalForLife writes a fresh token and a stopped budget clock.
                store.completePending(actor, item.id)
                val done = CompletableDeferred<Unit>()
                if (start(conversationId) {
                        try { goalLoop(conversationId) } finally { done.complete(Unit) }
                    }) done.await()
            }
        }
    }

    /** Public UI/command API. Commands never create fake user messages. */
    fun goalCreate(conversationId: Long, companionId: Long, description: String,
        constraints: String, criteria: List<String>, steps: List<String>, budget: GoalBudget,
        replaceActive: Boolean = false) {
        scope.launch {
            val store=goalStore ?: return@launch
            val room=db.conversations().get(conversationId)
            if(room==null || (room.isGroup && companionId !in db.groupMembers().idsFor(conversationId)) ||
                (!room.isGroup && room.companionId!=companionId)) {
                note(conversationId,"目标未创建：角色不属于这个会话。")
                return@launch
            }
            val made=store.create(conversationId,companionId,description,constraints,criteria,steps,budget,replaceActive)
            if(made==null) note(conversationId,"目标未创建：请填写目标与至少一项验收标准；如已有活动目标，请先暂停、取消或清除。")
            else {
                note(conversationId,"目标模式已创建：${made.description.take(100)}。已保存检查点，开始执行。")
                if(replaceActive) jobs[conversationId]?.cancel()
                store.resume(made.id)
                scheduleGoal(conversationId)
            }
        }
    }

    fun goalControl(conversationId: Long, companionId: Long, action: String,
        description: String = "", constraints: String = "",
        criteria: List<String> = emptyList(), steps: List<String> = emptyList(),
        budget: GoalBudget = GoalBudget(), criterionIndex: Int = -1) {
        scope.launch {
            val store=goalStore ?: return@launch
            val g=store.active(conversationId,companionId) ?: return@launch
            when(action) {
                "pause" -> { store.pause(g.id); jobs[conversationId]?.cancel() }
                "cancel","clear" -> { store.cancel(g.id); jobs[conversationId]?.cancel() }
                "edit" -> { store.edit(g.id,description,constraints,criteria,steps,budget);
                    jobs[conversationId]?.cancel() }
                "resume" -> { if(store.resume(g.id)?.status=="RUNNING") scheduleGoal(conversationId) }
                "approve" -> { if(store.approve(g.id)?.status=="RUNNING") scheduleGoal(conversationId) }
                "reconcile" -> store.reconcile(g.id)
                "delivery_chat","delivery_markdown" -> {
                    // Explicit user action only. Original file obligations are replaced with
                    // new acceptance, the version increases, and stale callbacks are rejected.
                    val selected=if(action=="delivery_chat") "CHAT_TEXT" else "MARKDOWN"
                    val changed=store.chooseDelivery(g.id,selected)
                    if(changed!=null && changed.version!=g.version) {
                        jobs[conversationId]?.cancel()
                        if(store.resume(g.id)?.status=="RUNNING") scheduleGoal(conversationId)
                    }
                }
                "budget" -> store.adjustBudget(g.id,budget)
                "pass" -> { store.verify(g.id,criterionIndex,true); jobs[conversationId]?.cancel() }
                "fail" -> { store.verify(g.id,criterionIndex,false); jobs[conversationId]?.cancel() }
            }
        }
    }

    fun goalCommand(conversationId: Long, companionId: Long, input: String): Boolean {
        if (!input.startsWith("/goal") || (input.length>5 && !input[5].isWhitespace())) return false
        val tail=input.removePrefix("/goal").trim()
        val current=goalStore?.active(conversationId,companionId)
        when {
            tail.isBlank() -> scope.launch {
                note(conversationId, current?.let {
                    "目标：${it.description.take(100)} · ${it.status} · ${it.rounds}/${it.budget.maxRounds}轮；验收${it.criteria.count { c -> c.status=="PASSED" }}/${it.criteria.size}项通过。"
                } ?: "目前没有活动目标。点聊天栏的「目标」可以创建。")
            }
            tail=="pause" -> goalControl(conversationId,companionId,"pause")
            tail=="resume" -> goalControl(conversationId,companionId,"resume")
            tail=="clear" -> goalControl(conversationId,companionId,"clear")
            tail.startsWith("edit ") -> {
                if(current!=null) goalControl(conversationId,companionId,"edit",tail.removePrefix("edit "),
                    current.constraints,current.criteria.map { it.text },current.steps.map { it.title },current.budget)
            }
            current!=null -> scope.launch { note(conversationId,"已有活动目标，请先在目标卡片中处理原目标，避免同时执行两个任务。") }
            else -> goalCreate(conversationId,companionId,tail,"",
                listOf("交付与目标相符的结果，并提供可检查的证据。"),emptyList(),GoalBudget())
        }
        return true
    }

    private fun scheduleGoal(conversationId: Long) {
        scope.launch {
            // Wait for any previous reply/tool to stop before the new execution lease starts.
            jobs[conversationId]?.join()
            start(conversationId) { goalLoop(conversationId) }
        }
    }

    private suspend fun goalLoop(conversationId: Long) {
        val store=goalStore ?: return
        while (true) {
            currentCoroutineContext().ensureActive()
            val room=db.conversations().get(conversationId) ?: return
            val goal=store.records.value.lastOrNull { it.conversationId==conversationId &&
                it.status=="RUNNING" && (room.isGroup || it.companionId==room.companionId) } ?: return
            if(room.isGroup && goal.companionId !in db.groupMembers().idsFor(conversationId)) {
                store.pause(goal.id);return
            }
            if (deferGoalForLife(goal)) return
            try {
                goalExecuting[conversationId]=goal.id to goal.version
                goalTurn(conversationId,goal)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.checkpoint(goal.id,goal.version,
                    kotlinx.serialization.json.buildJsonObject {
                        put("blocking_reason","模型或网络执行失败：${e.javaClass.simpleName}")
                    })
            } finally {
                goalExecuting.remove(conversationId,goal.id to goal.version)
            }
            val after=store.byId(goal.id) ?: return
            if(after.version!=goal.version || after.status!="RUNNING") return
            // Progress means plan/criteria changed or a real tool receipt was recorded.
            val progressed=after.steps!=goal.steps || after.criteria!=goal.criteria ||
                after.receipts.size!=goal.receipts.size
            if(!progressed) store.noProgress(goal.id,goal.version)
            if(store.byId(goal.id)?.status!="RUNNING") return
            delay(700)
        }
    }

    /** Deliberately reuses the real chat model / tool / MCP permission path. No fake user turn. */
    private suspend fun goalTurn(conversationId: Long, goal: GoalRecord) {
        val store=goalStore ?: return
        val room=db.conversations().get(conversationId) ?: return
        val ta=companions.get(goal.companionId) ?: return
        if (deferGoalForLife(goal)) return
        val s=settings.current()
        val key=secrets.key(ta.apiBaseUrl)
        if(key.isNullOrBlank()) {
            store.checkpoint(goal.id,goal.version,
                kotlinx.serialization.json.buildJsonObject { put("blocking_reason","当前角色没有可用模型密钥") })
            return
        }
        val endpoint=ApiEndpoint(ta.apiBaseUrl,key,ta.apiModel)
        val groups=groupsFor(s,ta)
        val outside=mcp.tools()
        val history=Recap.sent(recaps.live(room),s.historySize)
        val (stickers, sendStickers)=stickersFor(s)
        val memories=PersonalityGrowthRules.forPrompt(db.memories().allFor(ta.id),ToolGroup.Memory in s.tools)
        val withImages=MessagePictures.hasImages(history,stickers)
        val prompt=Prompt.messages(
            s,ta,history,ZonedDateTime.now(),groups,withImages,memories,room.recap,outside,
            emptyList(),null,stickers,sendStickers,
            extraContext=listOfNotNull(store.context(goal),
                life?.context(ta.id, goal.description)).joinToString("\n\n"),
        )
        var messages=prepare(prompt)
        val specs=tools.specs(groups)+outside.map { it.spec }+GoalCheckpointTool.spec
        for (round in 0 until 4) {
            currentCoroutineContext().ensureActive()
            if (deferGoalForLife(goal)) return
            if(!store.startCall(goal.id,goal.version)) return
            when(val step=step(conversationId,endpoint,messages,specs,
                mayRefuse=false,thinking=false,showThought=ta.deepThinking,
                speakerCompanionId=ta.id)) {
                is Step.Ended -> return
                is Step.Said -> return
                Step.Refused -> {
                    store.checkpoint(goal.id,goal.version,
                        kotlinx.serialization.json.buildJsonObject {
                            put("blocking_reason","当前模型不支持目标执行所需的工具调用。")
                        })
                    return
                }
                is Step.Called -> {
                    if(store.byId(goal.id)?.status!="RUNNING") return
                    val results=runTools(conversationId,step,s,ta.id,outside,ta.name)
                    if(store.byId(goal.id)?.status!="RUNNING") return
                    if(step.message.toolCalls.all { it.name in ToolSpecs.speaking }) return
                    messages=messages+step.message+results
                }
            }
        }
    }

    suspend fun newConversation(companionId: Long): Long {
        val now = System.currentTimeMillis()
        return db.conversations().insert(
            ConversationEntity(title = DEFAULT_TITLE, createdAt = now, updatedAt = now, companionId = companionId),
        )
    }

    /** A shared room with two or more existing TAs. */
    suspend fun newGroupConversation(memberIds: List<Long>): Long {
        val valid = memberIds.distinct().mapNotNull { companions.get(it) }.take(GroupChats.MAX_MEMBERS)
        require(valid.size >= 2) { "群聊至少需要两个角色" }
        val now = System.currentTimeMillis()
        val title = valid.take(3).joinToString("、") { it.name.trim().ifEmpty { "TA" } } +
            if (valid.size > 3) " 等${valid.size}人" else ""
        return db.withTransaction {
            val id = db.conversations().insert(
                ConversationEntity(title = title, createdAt = now, updatedAt = now, companionId = valid.first().id, isGroup = true),
            )
            db.groupMembers().insertAll(valid.mapIndexed { index, ta -> ConversationMemberEntity(id, ta.id, index) })
            id
        }
    }

    /** Change an existing group's members without deleting any historical messages. */
    suspend fun updateGroupConversationMembers(conversationId: Long, memberIds: List<Long>) {
        val group = db.conversations().get(conversationId) ?: return
        require(group.isGroup) { "这不是群聊" }
        val valid = memberIds.distinct().mapNotNull { companions.get(it) }.take(GroupChats.MAX_MEMBERS)
        require(valid.size >= 2) { "群聊至少保留两个角色" }
        db.withTransaction {
            db.groupMembers().deleteFor(conversationId)
            db.groupMembers().insertAll(valid.mapIndexed { index, ta -> ConversationMemberEntity(conversationId, ta.id, index) })
            if (valid.none { it.id == group.companionId }) {
                db.conversations().reassignCompanion(conversationId, valid.first().id)
            }
        }
    }

    /** A real one-to-one chat even when the remembered screen is currently a group. */
    suspend fun singleConversation(companionId: Long): Long =
        db.conversations().latestFor(companionId)?.id ?: newConversation(companionId)

    /**
     * The conversation to show with TA [companionId]: the remembered one if it is still
     * there and theirs, else their latest, else a new one.
     */
    suspend fun resolveConversation(remembered: Long?, companionId: Long): Long {
        if (remembered != null) {
            val saved = db.conversations().get(remembered)
            if (saved?.isGroup == true || saved?.companionId == companionId) return remembered
        }
        return db.conversations().latestFor(companionId)?.id ?: newConversation(companionId)
    }

    /** The TA a conversation is with (the first one if the conversation is gone). */
    private suspend fun taOf(conversationId: Long) =
        db.conversations().get(conversationId)?.companionId?.let { companions.get(it) } ?: companions.current()

    /**
     * The tools [ta] gets: the ones switched on, send_voice only once there is a voice to speak
     * with, and note_for_later only while it may reach out on its own (a switch of its own, not
     * one of the shared ones). Stickers are not a tool: they go along even when tools can't
     * ([stickersFor]), and a request asking for no tools but stickers would still count as one
     * with tools, and be refused over and over by a model that takes none.
     */
    private fun groupsFor(s: AppSettings, ta: CompanionEntity): Set<ToolGroup> {
        val on = (if (Speech.ready(s)) s.tools else s.tools - ToolGroup.Speak) - ToolGroup.Stickers
        return if (ta.proactive) on + ToolGroup.Later else on
    }

    /** The collection, read once for a request, and whether the TA may send from it (its switch). */
    private suspend fun stickersFor(s: AppSettings): Pair<StickerBook, Boolean> =
        StickerBook(db.stickers().all()) to (ToolGroup.Stickers in s.tools)

    /**
     * A voice message: stored at once, so it shows while it is being turned into text, then
     * answered like a typed one, even if it was sent while a reply was being written.
     */
    fun sendVoice(conversationId: Long, clip: MessageAudio, quote: MessageQuote? = null) {
        val at = stamp()
        scope.launch {
            val id = db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "user",
                    content = "",
                    createdAt = at,
                    audio = MessageAudios.encode(clip),
                    quote = quote?.let(MessageQuotes::encode),
                ),
            )
            db.conversations().touch(conversationId, at)
            if (holding(conversationId) { transcribe(id, clip) }) answerSoon(conversationId)
        }
    }

    /** A voice message that couldn't be turned into text, tried again; answered if it can be now. */
    fun retryVoice(conversationId: Long, messageId: Long) {
        scope.launch {
            val clip = db.messages().get(messageId)?.let { MessageAudios.decode(it.audio) } ?: return@launch
            db.messages().setError(messageId, null)
            if (holding(conversationId) { transcribe(messageId, clip) }) answerSoon(conversationId)
        }
    }

    /** Message [id]'s recording into its text; false, with the reason on the message, when it can't be. */
    private suspend fun transcribe(id: Long, clip: MessageAudio): Boolean {
        _transcribing.update { it + id }
        try {
            val s = settings.current()
            if (s.voiceBaseUrl.isBlank() || s.voiceModel.isBlank()) throw ChatException("还没接语音转文字的服务，去设置「发语音」里选一个。")
            val text = transcriber.transcribe(s.voiceBaseUrl, s.voiceModel, images.file(clip.file)).trim()
            if (text.isEmpty()) throw ChatException("没听清，再说一次？")
            if (db.messages().get(id)?.role != "user") return false
            db.messages().setContent(id, text)
            return true
        } catch (e: ChatException) {
            withContext(NonCancellable) { db.messages().setError(id, "没转成文字：${e.message}") }
            return false
        } finally {
            _transcribing.update { it - id }
        }
    }

    /**
     * Sends at once, also while a reply is being written; the answer comes once the person has
     * stopped for a moment ([answerSoon]). [quote]: the message this one answers. False only
     * when there is nothing to send.
     */
    fun send(conversationId: Long, text: String, pictures: List<MessageImage> = emptyList(),
        quote: MessageQuote? = null, mentions: Map<Long, String> = emptyMap()): Boolean {
        val content = text.trim()
        if (content.isEmpty() && pictures.isEmpty()) return false
        val at = stamp()
        scope.launch {
            val convoForMentions = db.conversations().get(conversationId)
            val verified = if (convoForMentions?.isGroup == true) {
                val current = db.groupMembers().idsFor(conversationId).toSet()
                GroupChats.selectedMentionIds(content, mentions).filter { it in current }.sorted()
            } else emptyList()
            goalStore?.active(conversationId, convoForMentions?.companionId ?: -1L)?.let { g ->
                if(g.status=="RUNNING") {
                    goalStore.pause(g.id)
                    jobs[conversationId]?.cancel()
                }
                goalStore.userNote(g.id,content)
            }
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "user",
                    content = content,
                    createdAt = at,
                    images = MessageImages.encode(pictures),
                    quote = quote?.let(MessageQuotes::encode),
                    mentionedCompanionIds = verified.takeIf { it.isNotEmpty() }?.joinToString(","),
                ),
            )
            val conversation = db.conversations().get(conversationId)
            if (conversation != null && conversation.title == DEFAULT_TITLE) {
                db.conversations().rename(conversationId, StickerText.plain(content).lineSequence().first().take(24).ifBlank { "[图片]" })
            }
            db.conversations().touch(conversationId, at)
            answerSoon(conversationId)
        }
        return true
    }

    /**
     * A successful virtual-money action is a real user interaction, just like sending a
     * chat message. It starts the existing serialized conversation reply pipeline.
     * Enqueue only AFTER AtomicFile wallet commit; never charge AI tokens on failed sends.
     * The synthetic row is durable for model context but hidden from UI (the orange ledger
     * card is its single visible representation).
     */
    suspend fun walletTransaction(
        conversationId: Long, packet: Boolean, receiptId: String, recipients: List<Long>,
    ) {
        val room = db.conversations().get(conversationId)
            ?: throw IllegalStateException("会话不存在")
        val authorized = if (room.isGroup) db.groupMembers().idsFor(conversationId).toSet()
            else setOf(room.companionId)
        val target = recipients.filter { it > 0L }.toSet()
        require(target.isNotEmpty() && recipients.distinct().size == recipients.size &&
            target.all { it in authorized }) {
            "当前会话收款人不匹配"
        }
        if (!packet) require(target.size == 1) { "单次转账只能有一个收款对象" }
        val wallet = WalletChatBridge.store?.state?.value ?: error("钱包未初始化")
        if (packet) {
            val saved = wallet.packets.singleOrNull { it.id == receiptId && it.conversationId == conversationId &&
                it.sender == 0L } ?: error("没有找到已经发送的真实红包")
            require(saved.recipients.toSet() == (target +
                if (saved.allowSenderClaim && room.isGroup && saved.random) setOf(0L) else emptySet())) {
                "红包目标已变化"
            }
        } else {
            val saved = wallet.transfers.singleOrNull { it.id == receiptId &&
                it.conversationId == conversationId && it.from == 0L &&
                it.state == "pending" }
                ?: error("没有找到真实待收款转账")
            require(saved.to in target) { "转账收款对象不匹配" }
        }
        val at = stamp()
        db.messages().insert(MessageEntity(
            conversationId = conversationId,
            role = "user",
            content = WalletReactiveEvents.messageText(packet, receiptId),
            createdAt = at,
            mentionedCompanionIds = target.sorted().joinToString(","),
        ))
        db.conversations().touch(conversationId, at)
        answerSoon(conversationId)
        // Wallet actions are not held behind the ordinary typing debounce. A queued network
        // request still follows existing per-conversation exclusivity and group quota.
        lastSent[conversationId] = at - REPLY_WAIT
    }

    /** A sticker keeps its token for display; Prompt resolves its artwork for the model. */
    fun sendSticker(conversationId: Long, name: String, quote: MessageQuote? = null): Boolean =
        send(conversationId, StickerText.token(name), quote = quote)

    /** Taken one at a time: two quick taps must not each put back what the other changed. */
    private val reacting = Mutex()

    /** A new menu emoji is a real user turn; removing it does not request another reply. */
    fun react(messageId: Long, emoji: String) {
        scope.launch {
            var notify: Long? = null
            reacting.withLock {
                db.withTransaction {
                    val m = db.messages().get(messageId) ?: return@withTransaction
                    val at = stamp()
                    val change = ReactionEvents.change(m, emoji, at) ?: return@withTransaction
                    // After reopening the app, removing a queued event must not replay old user messages.
                    answeredUpTo.putIfAbsent(m.conversationId, db.messages().lastAnsweredAt(m.conversationId) ?: Long.MIN_VALUE)
                    db.messages().setReactions(messageId, change.reactions)
                    if (change.event != null) {
                        db.messages().insert(change.event)
                        db.conversations().touch(m.conversationId, at)
                        notify = m.conversationId
                    } else {
                        val through = answeredUpTo[m.conversationId] ?: Long.MIN_VALUE
                        val pending = db.messages().reactionEventsAfter(m.conversationId, through)
                        for (id in ReactionEvents.pendingIds(pending, m.conversationId, messageId, emoji, through)) {
                            db.messages().delete(id)
                        }
                    }
                }
            }
            notify?.let { answerSoon(it) }
        }
    }

    private val patting = Mutex()

    /**
     * 拍一拍: leaves a line in [conversationId], or adds one to the run of pats just before it.
     * Nothing is answered: the TA hears of it with the person's next message (Prompt).
     */
    fun pat(conversationId: Long, who: String, verb: String, suffix: String, targetCompanionId: Long? = null) {
        scope.launch {
            patting.withLock {
                val last = db.messages().newest(conversationId, 1).firstOrNull()?.takeIf { it.role == "pat" }
                val now = stamp()
                val record = Pats.again(
                    Pats.decode(last?.content),
                    last?.createdAt ?: 0L,
                    who,
                    verb,
                    suffix,
                    now,
                    targetCompanionId = targetCompanionId,
                )
                if (last != null && record.count > 1) {
                    db.messages().setPat(last.id, Pats.encode(record), now)
                } else {
                    db.messages().insert(
                        MessageEntity(conversationId = conversationId, role = "pat", content = Pats.encode(record), createdAt = now),
                    )
                }
            }
            if (who == Pats.AI) answerSoon(conversationId)
        }
    }

    /** The TA patting the person (pat_user): a line in the chat, after what it has said so far. */
    suspend fun patBack(conversationId: Long, suffix: String, actor: Long? = null) {
        val verb = settings.current().patVerb
        withContext(NonCancellable) {
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "pat",
                    content = Pats.encode(PatRecord(Pats.FROM_AI, 1, verb, Pats.cleanSuffix(suffix),
                        targetCompanionId = actor)),
                    createdAt = stamp(),
                ),
            )
        }
    }

    /** Patted so many times in a row: once the person stops, let the TA answer with a word or two. */
    private fun answerHeavyPats(conversationId: Long, patId: Long) {
        scope.launch {
            if (ToolGroup.Pat !in settings.current().tools) return@launch
            while (true) {
                val row = db.messages().newest(conversationId, 1).firstOrNull()
                    ?.takeIf { it.id == patId && it.role == "pat" } ?: return@launch
                val quietFor = System.currentTimeMillis() - row.createdAt
                if (quietFor >= Pats.STREAK_MS + 500) break
                delay(Pats.STREAK_MS + 500 - quietFor)
            }
            val ta = taOf(conversationId)
            if (secrets.key(ta.modelFor(heard = false).baseUrl).isNullOrBlank()) return@launch
            // The switch may have been turned off while waiting for the person to stop patting.
            if (ToolGroup.Pat !in settings.current().tools) return@launch
            start(conversationId) { reply(conversationId) }
        }
    }

    /** Throw away [assistantMessageId] (a failed or unwanted reply) and ask again. */
    fun retry(conversationId: Long, assistantMessageId: Long) {
        start(conversationId) {
            val row = db.messages().get(assistantMessageId) ?: return@start
            if (row.conversationId != conversationId || row.role != "assistant") return@start
            val convo = db.conversations().get(conversationId) ?: return@start
            if (!convo.isGroup) {
                if (deferForLife(conversationId, convo.companionId,
                        purpose = "retry:$assistantMessageId")) return@start
                db.messages().delete(assistantMessageId)
                reply(conversationId)
            } else {
                // Keep the original until a complete replacement is actually on disk.
                val members = db.groupMembers().idsFor(conversationId).mapNotNull { companions.get(it) }
                val speaker = members.firstOrNull { it.id == row.senderCompanionId } ?: return@start
                if (deferForLife(conversationId, speaker.id,
                        purpose = "retry:$assistantMessageId")) return@start
                if (secrets.key(speaker.apiBaseUrl).isNullOrBlank()) return@start
                val history = Recap.sent(recaps.live(convo), settings.current().historySize)
                    .filterNot { it.id == row.id }
                val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }
                val shaped = GroupChats.historyFor(history, speaker.id, names, convo.companionId)
                val query = history.lastOrNull { it.role == "user" }?.content.orEmpty()
                val priorIds = db.messages().newest(conversationId, 16).map { it.id }.toSet()
                val produced = groupTurn(conversationId, speaker, members, shaped,
                    worldContext(conversationId, query, settings.current()), targeted = true)
                if (produced) {
                    val replacement = db.messages().newest(conversationId, 16).any {
                        it.id !in priorIds && it.role == "assistant" &&
                        it.senderCompanionId == speaker.id && it.error == null &&
                        it.content.isNotBlank()
                    }
                    if (replacement) db.messages().delete(row.id)
                }
            }
        }
    }

    fun stop(conversationId: Long) {
        jobs[conversationId]?.cancel()
    }

    /**
     * Stops the replies in these conversations and waits until they have let go. A reply
     * still writing into a conversation that is being deleted would fail on the missing
     * row, so this comes first.
     */
    suspend fun stopReplies(conversationIds: Collection<Long>) {
        conversationIds.mapNotNull { jobs[it] }.forEach { it.cancelAndJoin() }
    }

    /** Before one TA goes, with every conversation they had. */
    suspend fun stopRepliesOf(companionId: Long) = stopReplies(db.conversations().idsFor(companionId))

    /** Before a restore replaces every conversation. */
    suspend fun stopAll() = stopReplies(jobs.keys.toList())

    /** The row and the pictures sent with it: nothing else points at those files. A call goes with all that was said in it. */
    /** Withdraw only this conversation's ordinary user messages, then answer the event. */
    fun recallMessage(conversationId: Long, id: Long) {
        scope.launch {
            recallCoordinator.perform(conversationId,
                eligible = { db.messages().get(id)?.let { it.conversationId == conversationId && Recalls.canRecall(it) } == true },
            ) {
                try {
                    // Snapshot the chosen wording before opening the recall transaction.
                    val recallNotice = settings.current().recallDisplayText
                    recaps.withoutFolding(conversationId) {
                        db.withTransaction {
                            val conversation = db.conversations().get(conversationId) ?: return@withTransaction
                            val current = db.messages().get(id)
                                ?.takeIf { it.conversationId == conversationId && Recalls.canRecall(it) } ?: return@withTransaction
                            val at = stamp()
                            db.messages().updateForRecall(Recalls.withdrawn(current, recallNotice))
                            for (quoted in db.messages().quotesIn(conversationId)) {
                                val redacted = Recalls.redactQuote(quoted.quote, current.id)
                                if (redacted != quoted.quote) db.messages().setQuote(quoted.id, redacted)
                            }
                            if (Recalls.wasFolded(current, conversation)) db.conversations().clearRecap(conversationId)
                            db.messages().insert(Recalls.event(current, at))
                            db.conversations().touch(conversationId, at)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (db.conversations().get(conversationId) != null) {
                        db.messages().insert(MessageEntity(conversationId = conversationId, role = "note", content = "",
                            note = "撤回失败，请再试一次。", createdAt = stamp()))
                    }
                }
            }
        }
    }

    /** A new continuation at the edited turn, with the original conversation retained. */
    suspend fun editAndResend(expected: MessageEntity, text: String): Long {
        val originalId = expected.conversationId
        if (calling == originalId) throw MessageEditException("请先结束电话，再编辑消息。")
        var branchId: Long? = null
        var editedAt = 0L
        val beforeSends = sends[originalId]
        val accepted = recallCoordinator.perform(originalId,
            eligible = { calling != originalId && db.messages().get(expected.id) == expected && MessageEdits.canSubmit(expected, text) },
            resumeWith = { id ->
                synchronized(lock) { recalling -= id }
                // Do not re-execute historical inputs in the retained original conversation.
                // Only something actually sent while editing may need a reply there.
                if (sends[id] != beforeSends) answerSoon(id)
            },
        ) {
            recaps.withoutFolding(originalId) {
                val source = db.conversations().get(originalId) ?: throw MessageEditException("原对话已经删除。")
                val snapshot = MessageEdits.prefix(db.messages().prefixForEdit(originalId, expected.createdAt, expected.id), expected, text)
                val copied = mutableMapOf<String, String>()
                val staged = mutableListOf<String>()
                commitMessageEdit<Long>(
                    stage = {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val files = snapshot.flatMap { m -> MessageImages.decode(m.images).map { it.file } +
                                listOfNotNull(MessageAudios.decode(m.audio)?.file) }.distinct()
                            for (name in files) {
                                currentCoroutineContext().ensureActive()
                                if (java.io.File(name).name != name || name == "." || name == "..")
                                    throw MessageEditException("附件路径无效，无法重新发送。")
                                val old = images.file(name)
                                // A file already missing in the original remains unavailable, without blocking text edits.
                                if (!old.isFile) continue
                                val extension = old.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
                                val fresh = "edit-" + java.util.UUID.randomUUID().toString() + extension
                                staged += fresh
                                old.copyTo(images.file(fresh), overwrite = false)
                                copied[name] = fresh
                            }
                        }
                    },
                    commit = {
                        db.withTransaction {
                            if (calling == originalId || db.conversations().get(originalId) != source ||
                                db.messages().prefixForEdit(originalId, expected.createdAt, expected.id) != snapshot)
                                throw MessageEditException("原消息或前文已经变化，请重新打开编辑。")
                            val at = stamp()
                            val ids = mutableMapOf<Long, Long>()
                            val newId = db.conversations().insert(MessageEdits.copyConversation(source, expected, ids, at))
                            if (source.isGroup) {
                                db.groupMembers().insertAll(
                                    db.groupMembers().forConversation(originalId).map { it.copy(conversationId = newId) },
                                )
                            }
                            for (row in snapshot) {
                                val copy = MessageEdits.copyRow(row, newId, ids, copied)
                                val next = if (row.id == expected.id) copy.copy(content = text.trim(), error = null, createdAt = at) else copy
                                ids[row.id] = db.messages().insert(next)
                            }
                            db.conversations().updateForMessageEdit(MessageEdits.copyConversation(source, expected, ids, at).copy(id = newId))
                            editedAt = at
                            newId
                        }
                    },
                    rollback = { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { images.delete(staged) } },
                    answer = { newId ->
                        branchId = newId
                        // The explicit attempt handles this input even if no Key is configured.
                        // A later genuine input can still enter the ordinary reply queue.
                        answeredUpTo.merge(newId, editedAt) { a, b -> maxOf(a, b) }
                        // An explicit resend answers at once, even while a separate draft is in the input.
                        if (!start(newId) { reply(newId); answerUntilQuiet(newId) }) answerSoon(newId)
                    },
                )
            }
        }
        if (!accepted) throw MessageEditException("原消息已经变化或正在通话，请重新打开编辑。")
        return branchId ?: throw MessageEditException("重新发送失败，请再试一次。")
    }

    fun deleteMessage(id: Long) {
        scope.launch {
            val m = db.messages().get(id)
            if (m?.role == "call") {
                db.messages().deleteCall(id)
                return@launch
            }
            db.messages().delete(id)
            images.delete(MessageImages.decode(m?.images).map { it.file } + listOfNotNull(MessageAudios.decode(m?.audio)?.file))
        }
    }

    // Phone calls (ai/Call.kt). What the two say goes into the conversation as messages marked as
    // the call's, so the TA remembers it like anything typed. While a call is on, replies and wakes
    // there wait (busy): nothing comes between what the two say.

    /**
     * A call begins in [conversationId]: a reply still under way there stops, since the person has
     * moved on to calling, and the call's row goes in. Returns its id.
     */
    suspend fun beginCall(conversationId: Long): Long {
        val room = db.conversations().get(conversationId) ?: throw ChatException("这段对话已经删了")
        life?.tick()
        val actors = if (room.isGroup) db.groupMembers().idsFor(conversationId)
            else listOf(room.companionId)
        if (actors.isNotEmpty() && actors.all { life?.availability(it)?.isBusy == true })
            throw ChatException("当前角色正在工作、休息或睡眠，暂时无法接听。可在聊天页明确唤醒后再拨打。")
        calling = conversationId
        stopReplies(listOf(conversationId))
        val at = stamp()
        val id = db.messages().insert(
            MessageEntity(conversationId = conversationId, role = "call", content = CallRecords.encode(CallRecord()), createdAt = at),
        )
        db.conversations().touch(conversationId, at)
        return id
    }

    /** The TA picked up, [at] then: the call's length counts from here. */
    suspend fun callAnswered(callId: Long, at: Long) = changeCall(callId) { it.copy(answeredAt = at) }

    /** Something said in the call, by the person ("user") or the TA ("assistant"). */
    suspend fun callLine(conversationId: Long, callId: Long, role: String, text: String,
                         speakerCompanionId: Long? = null) {
        withContext(NonCancellable) {
            val at = stamp()
            db.messages().insert(MessageEntity(conversationId = conversationId, role = role,
                content = text, createdAt = at, call = callId, senderCompanionId = speakerCompanionId))
            db.conversations().touch(conversationId, at)
        }
    }

    /** The call is over: its row says when, replies there can come again, and the recap catches up with it. */
    suspend fun endCall(conversationId: Long, callId: Long) {
        withContext(NonCancellable) {
            changeCall(callId) { it.copy(endedAt = System.currentTimeMillis()) }
            db.conversations().touch(conversationId, stamp())
            if (calling == conversationId) calling = null
            recaps.foldLater(conversationId)
        }
    }

    /** Calls left open by the app stopping mid-call (killed, crashed): ended where the last thing was said in them. */
    suspend fun closeOpenCalls() {
        for (row in db.messages().calls()) {
            val record = CallRecords.decode(row.content) ?: continue
            if (record.endedAt != null || row.conversationId == calling) continue
            val last = db.messages().lastInCall(row.id) ?: record.answeredAt ?: row.createdAt
            db.messages().setContent(row.id, CallRecords.encode(record.copy(endedAt = maxOf(last, record.answeredAt ?: last))))
        }
    }

    /** The calls [history] has lines of but not the row of (the window begins mid-call): how long each went on is in the row. */
    private suspend fun callsOutside(history: List<MessageEntity>): Map<Long, CallRecord> {
        val missing = history.mapNotNullTo(HashSet()) { it.call } - history.filter { it.role == "call" }.map { it.id }.toSet()
        if (missing.isEmpty()) return emptyMap()
        return db.messages().byIds(missing).mapNotNull { m -> CallRecords.decode(m.content)?.let { m.id to it } }.toMap()
    }

    private suspend fun changeCall(callId: Long, change: (CallRecord) -> CallRecord) {
        withContext(NonCancellable) {
            val row = db.messages().get(callId) ?: return@withContext
            db.messages().setContent(callId, CallRecords.encode(change(CallRecords.decode(row.content) ?: CallRecord())))
        }
    }

    /**
     * The TA's turn in a call: its words go to [say] as they are written, for the call to speak
     * sentence by sentence. Tools as in a reply, those that make sense on the phone (CALL_TOOLS),
     * their calls and results stored as the call's, since they happened. The words aren't stored
     * here: the call stores what of them was heard (callLine). No thinking: on the phone a silence
     * that long is a dropped line. [instruction]: a turn nobody asked for (the line went quiet), told
     * the TA unseen, the way a wake is. Returns all it said; throws ChatException when there is no answer.
     */
    /** A genuine multi-person telephone turn: each eligible TA reads the current group
     * transcript, chooses to speak or stay quiet, and responds with its own CALL model.
     * onLine returns only complete, non-SKIP text for ordered TTS playback. Phone-side
     * actions/MCP are never offered implicitly during an ambient group conversation.
     * Each network request is counted against the group's configured daily budget.
     */
    suspend fun groupCallReply(
        conversationId: Long, callId: Long, instruction: String? = null,
        onLine: suspend (CompanionEntity, String) -> Unit,
    ): Int {
        val conversation = db.conversations().get(conversationId) ?: return 0
        if (!conversation.isGroup) return 0
        val s = settings.current()
        val all = db.groupMembers().idsFor(conversationId).mapNotNull { companions.get(it) }
        val muted = conversation.groupMutedIds.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        life?.tick()
        val eligible = all.filterNot { it.id in muted || life?.availability(it.id)?.isBusy == true }
        if (eligible.isEmpty()) return 0
        val initial = Recap.sent(recaps.live(conversation), s.historySize)
        val latestText = initial.lastOrNull { it.role == "user" && it.call == callId }?.content.orEmpty()
        val mentioned = GroupChats.mentioned(latestText, eligible)
        val last = initial.lastOrNull { it.role == "assistant" }?.senderCompanionId
        val startAt = eligible.indexOfFirst { it.id == last }
            .let { if (it < 0) 0 else (it + 1) % eligible.size }
        val rotated = eligible.drop(startAt) + eligible.take(startAt)
        val candidates = GroupLiveCall.select(rotated, mentioned,
            conversation.groupMaxReplies, conversation.groupMode, initial.isEmpty())
        val names = all.associate { it.id to it.name.trim().ifBlank { "TA" } }
        var count = 0
        for (ta in candidates) {
            currentCoroutineContext().ensureActive()
            val fresh = db.conversations().get(conversationId) ?: break
            if (!fresh.isGroup || ta.id in fresh.groupMutedIds.split(",").mapNotNull { it.toLongOrNull() })
                continue
            if (life?.availability(ta.id)?.isBusy == true) continue
            if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) == 0)
                break
            val voiceModel = ta.modelFor(heard = true)
            val key = secrets.key(voiceModel.baseUrl)?.takeIf { it.isNotBlank() } ?: continue
            val endpoint = ApiEndpoint(voiceModel.baseUrl, key, voiceModel.model)
            val current = db.conversations().get(conversationId) ?: break
            val history = GroupChats.historyFor(
                Recap.sent(recaps.live(current), s.historySize), ta.id, names, current.companionId)
            val memo = PersonalityGrowthRules.forPrompt(db.memories().allFor(ta.id), ToolGroup.Memory in s.tools)
            val stickers = StickerBook(db.stickers().all())
            val turnRule = GroupLiveCall.instruction(ta.name, eligible.map { it.name },
                instruction, mentioned.any { it.id == ta.id })
            val prompt = Prompt.messages(s, ta, history, ZonedDateTime.now(),
                tools = emptySet(), images = false, memories = memo, recap = current.recap,
                stickers = stickers, call = callId, calls = callsOutside(history),
                extraContext = listOfNotNull(turnRule, life?.context(ta.id, latestText))
                    .joinToString("\n\n"))
            try {
                val buffer = StringBuilder()
                val result = callStep(conversationId, callId, endpoint, prepare(prompt),
                    emptyList(), mayRefuse = false, speakerCompanionId = ta.id) { buffer.append(it) }
                val text = buffer.toString().trim()
                if (result is Step.Said && text.isNotBlank() && !GroupChats.isSkip(text)) {
                    onLine(ta, text.take(GroupLiveCall.MAX_SPOKEN_CHARS))
                    count++
                    if (count >= fresh.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)) break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One failed model does not kill the whole call or fabricate speech for it.
                android.util.Log.w("GroupLiveCall", "model request failed for group role", e)
            }
        }
        return count
    }

    suspend fun callReply(conversationId: Long, callId: Long, instruction: String? = null, say: (String) -> Unit): String {
        val s = settings.current()
        val ta = taOf(conversationId)
        life?.tick()
        life?.availability(ta.id)?.takeIf { it.isBusy }?.let {
            throw ChatException("${ta.name.trim().ifBlank { "TA" }}${it.label}，暂时无法接听；可明确唤醒后再拨打。")
        }
        // Everything said on the phone is heard: the model the TA has for that, when it has one.
        val use = ta.modelFor(heard = true)
        val key = secrets.key(use.baseUrl)?.takeIf { it.isNotBlank() }
            ?: throw ChatException(if (use.forHeard) "打电话用的模型还没有填 API Key" else "还没有填 API Key")
        val conversation = db.conversations().get(conversationId) ?: throw ChatException("这段对话已经删了")
        val endpoint = ApiEndpoint(use.baseUrl, key, use.model)
        val endpointKey = endpoint.chatUrl + "|" + endpoint.model
        val history = Recap.sent(recaps.live(conversation), s.historySize)
        var groups = if (endpointKey in refusesTools) emptySet() else (groupsFor(s, ta) intersect CALL_TOOLS).let { if (instruction == null) it else it - ToolGroup.Apps }
        val memories = PersonalityGrowthRules.forPrompt(db.memories().allFor(ta.id), ToolGroup.Memory in s.tools)
        // The person's stickers in what came before, told in words: none are sent on the phone.
        val stickers = StickerBook(db.stickers().all())
        val calls = callsOutside(history)
        fun build() = Prompt.messages(
            s, ta, history, ZonedDateTime.now(), groups, false, memories, conversation.recap, stickers = stickers, call = callId, calls = calls,
            extraContext = life?.context(ta.id, history.lastOrNull { it.role == "user" }?.content.orEmpty()),
        ).let { if (instruction != null) Prompt.withWake(it, instruction) else it }
        var messages = build()
        val said = StringBuilder()
        var rounds = 0
        val galleryBudget = GalleryToolBudget(ordinaryRoundLimit = MAX_TOOL_ROUNDS)
        while (true) {
            val step = callStep(conversationId, callId, endpoint, messages, tools.specs(groups), mayRefuse = rounds == 0 && groups.isNotEmpty()) {
                said.append(it)
                say(it)
            }
            when (step) {
                Step.Refused -> {
                    groups = emptySet()
                    messages = build()
                }
                is Step.Called -> {
                    if (!galleryBudget.accept(step.message.toolCalls)) {
                        note(conversationId, galleryBudget.lastError)
                        return said.toString()
                    }
                    val results = runTools(conversationId, step, s.copy(tools = groups), ta.id, emptyList(), ta.name, inCall = callId)
                    messages = messages + step.message + results
                    rounds++
                }
                else -> return said.toString()
            }
        }
    }

    /**
     * One request in a call: the words to [say] as they come, nothing shown in the chat. Calls are
     * stored at once (they are about to be acted on); an answer without calls comes back as
     * [Step.Said], unstored. A failure is thrown, unless it is the refusal [mayRefuse] allows for.
     */
    private suspend fun callStep(
        conversationId: Long,
        callId: Long,
        endpoint: ApiEndpoint,
        messages: List<ApiMessage>,
        specs: List<ToolSpec>,
        mayRefuse: Boolean,
        speakerCompanionId: Long? = null,
        say: (String) -> Unit,
    ): Step {
        val actor = speakerCompanionId ?: taOf(conversationId).id
        life?.tick()
        life?.availability(actor)?.takeIf { it.isBusy }?.let {
            throw ChatException("${it.label}，本次通话没有继续调用模型。可明确唤醒后继续。")
        }
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var calls = emptyList<ToolCall>()
        try {
            client.stream(endpoint, messages, specs,
                notice={ state -> note(conversationId,state) }).collect { event ->
                if (!lifeCanContinue(conversationId, actor, "passive"))
                    throw ChatException("工作、休息或睡眠已开始，通话本轮暂停，没有继续生成语音。")
                when (event) {
                    is ChatEvent.Delta -> {
                        text.append(event.text)
                        say(event.text)
                    }
                    is ChatEvent.Reasoning -> if (event.sendBack) reasoning.append(event.text)
                    is ChatEvent.ToolCalls -> {
                        // Sending messages isn't offered on the phone. A model used to it may send one
                        // all the same: what it meant to send is said instead.
                        val (sends, others) = event.calls.partition { it.name in ToolSpecs.speaking }
                        for (send in sends) {
                            val words = ToolArgs.parse(send.arguments)?.let { ToolArgs.text(it, "text") }?.trim().orEmpty()
                            if (words.isNotEmpty()) {
                                val line = if (text.isEmpty()) words else "\n$words"
                                text.append(line)
                                say(line)
                            }
                        }
                        calls = others
                    }
                }
            }
        } catch (e: ChatException) {
            if (mayRefuse && text.isEmpty() && e.status in REFUSED_STATUSES) return Step.Refused
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ChatException("出错了：${e.message ?: e.javaClass.simpleName}")
        }
        if (calls.isEmpty()) return Step.Said(text.toString(), null, null)
        val sentBack = reasoning.toString().ifEmpty { null }
        val id = withContext(NonCancellable) {
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "",
                    createdAt = stamp(),
                    toolCalls = ToolCallCodec.encode(calls),
                    reasoning = sentBack,
                    call = callId,
                ),
            )
        }
        return Step.Called(ApiMessage("assistant", text.toString(), calls, reasoning = sentBack), savedId = id)
    }

    /** A conversation, its pictures and recordings: the rows go by cascade, the files would stay behind. */
    fun deleteConversation(id: Long) {
        scope.launch {
            stopReplies(listOf(id))
            val pictures = db.messages().imagesIn(id).flatMap { MessageImages.decode(it) }.map { it.file } +
                db.messages().audioIn(id).mapNotNull { MessageAudios.decode(it)?.file }
            db.conversations().delete(id)
            images.delete(pictures)
        }
    }

    /**
     * The person's answer to a request card: show that secret this once, or don't. Either
     * way it is their turn in the conversation, so the model answers it.
     */
    fun answerSecretRequest(conversationId: Long, requestMessageId: Long, grant: Boolean) {
        start(conversationId) answer@{
            val row = db.messages().get(requestMessageId) ?: return@answer
            val request = SecretRequests.decode(row.content)?.takeIf { it.status == SecretRequest.PENDING } ?: return@answer
            val ai = taOf(conversationId).name.trim().ifEmpty { "TA" }
            val day = LocalDate.ofEpochDay(request.day)
            val entry = db.diary().get(request.diaryId)
            if (grant && entry == null) {
                db.messages().setContent(row.id, SecretRequests.encode(request.copy(status = SecretRequest.GONE)))
                note(conversationId, "这个小秘密已经删掉了，没法给${ai}看")
                return@answer
            }
            val status = if (grant) SecretRequest.GRANTED else SecretRequest.DECLINED
            db.messages().setContent(row.id, SecretRequests.encode(request.copy(status = status)))
            val now = System.currentTimeMillis()
            val today = LocalDate.now()
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "user",
                    content = if (entry != null && grant) SecretRequests.shared(entry, today) else SecretRequests.declined(day, today),
                    createdAt = now,
                    note = (if (grant) "给${ai}看了" else "没给${ai}看") + "${Describe.monthDay(day)}的小秘密",
                ),
            )
            db.conversations().touch(conversationId, now)
            reply(conversationId)
        }
    }

    /** What a wake came to (see Later). */
    sealed interface WakeResult {
        /** What the TA sent, in order. */
        class Sent(val messages: List<MessageEntity>) : WakeResult

        /** It decided not to; [why] is what it said after SKIP. */
        class Skipped(val why: String) : WakeResult

        /** A reply was under way there: nothing was done. */
        data object Busy : WakeResult

        class Failed(val why: String) : WakeResult
    }

    /**
     * The TA reading something it noted, now that it is due, and deciding whether to say it:
     * [instruction] goes after the conversation as a turn the person never sees (Prompt.withWake).
     * It runs as the conversation's job, like a reply, so the two never write at once, and what
     * the person sends meanwhile is answered right after. Nothing shows while the TA decides:
     * typing dots that came to nothing would be a message that never came.
     */
    private val followUpJobs = ConcurrentHashMap<Long, Job>()

    fun cancelFollowUp(conversationId: Long) {
        synchronized(lock) { followUpJobs.remove(conversationId)?.cancel() }
    }

    suspend fun wake(
        conversationId: Long,
        instruction: String,
        followUp: Boolean = false,
        allowed: suspend () -> Boolean = { true },
    ): WakeResult {
        val room = db.conversations().get(conversationId)
            ?: return WakeResult.Failed("对话已经删了")
        if (deferForLife(conversationId, room.companionId, purpose = "wake"))
            return WakeResult.Busy
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
    }

    private suspend fun wakeTurn(conversationId: Long, instruction: String): WakeResult {
        val s = settings.current()
        val ta = taOf(conversationId)
        if (deferForLife(conversationId, ta.id, purpose = "wake")) return WakeResult.Busy
        val key = secrets.key(ta.apiBaseUrl)
        if (key.isNullOrBlank()) return WakeResult.Failed("还没填 API Key")
        val conversation = db.conversations().get(conversationId) ?: return WakeResult.Failed("对话已经删了")
        val endpoint = ApiEndpoint(ta.apiBaseUrl, key, ta.apiModel)
        val endpointKey = endpoint.chatUrl + "|" + endpoint.model
        val history = Recap.sent(recaps.live(conversation), s.historySize)
        val now = ZonedDateTime.now()
        val since = System.currentTimeMillis()
        // No outside services and no pictures: nobody is there to allow a call, and deciding
        // whether to say something shouldn't cost what answering a picture does. No music either:
        // a song is paused or skipped when the person asks, and a wake is nobody asking.
        var groups = if (endpointKey in refusesTools) emptySet() else groupsFor(s, ta) - setOf(ToolGroup.Music, ToolGroup.Apps)
        var thinking = ta.deepThinking && endpointKey !in refusesThinking
        val memories = PersonalityGrowthRules.forPrompt(db.memories().allFor(ta.id), ToolGroup.Memory in s.tools)
        val (stickers, sendStickers) = stickersFor(s)
        val calls = callsOutside(history)
        fun build() = Prompt.withWake(
            Prompt.messages(s, ta, history, now, groups, false, memories, conversation.recap, stickers = stickers, sendStickers = sendStickers, calls = calls,
                extraContext = life?.context(ta.id, instruction)),
            instruction,
        )
        var messages = build()
        var rounds = 0
        val galleryBudget = GalleryToolBudget(ordinaryRoundLimit = MAX_TOOL_ROUNDS)
        suspend fun sent() = db.messages().proactiveSince(conversationId, since)
        suspend fun result(why: String): WakeResult {
            val said = sent()
            if (said.isEmpty()) return WakeResult.Skipped(why)
            recaps.foldLater(conversationId)
            return WakeResult.Sent(said)
        }
        try {
            while (true) {
                val mayRefuse = rounds == 0 && (groups.isNotEmpty() || thinking)
                when (val step = step(conversationId, endpoint, messages, tools.specs(groups), mayRefuse, thinking,
                    showThought = ta.deepThinking, wake = true, lifePurpose = "wake")) {
                    is Step.Said -> {
                        val error = step.error
                        if (error != null) return if (sent().isEmpty()) WakeResult.Failed(error) else result("")
                        val text = step.text.trim()
                        if (text.isEmpty() || LaterRules.isSkip(text)) return result(LaterRules.skipReason(text))
                        storeAssistantBubbles(
                            conversationId, AssistantBubbleSplitter.split(text, 3),
                            step.thought?.let(MessageThoughts::encode), proactive = true, quiet = true,
                        )
                        return result("")
                    }
                    Step.Refused -> {
                        // As in a reply: the thinking switch goes first, then the tools. The chat is told
                        // about it at the next reply, not by a line appearing out of nowhere.
                        if (thinking) thinking = false else groups = emptySet()
                        messages = build()
                    }
                    is Step.Called -> {
                        if (!galleryBudget.accept(step.message.toolCalls)) return result(galleryBudget.lastError)
                        val results = runTools(conversationId, step, s.copy(tools = groups), ta.id, emptyList(), ta.name, wake = true)
                        if (step.message.toolCalls.all { it.name in ToolSpecs.speaking }) return result("")
                        messages = messages + step.message + results
                        rounds++
                    }
                    is Step.Ended -> return if (!step.ok && life?.availability(ta.id)?.isBusy == true)
                        WakeResult.Busy else result("")
                }
            }
        } catch (_: LifeDeferred) {
            return WakeResult.Busy
        } finally {
            // Nothing said: none of it happened, as far as the chat goes. Its calls (putting the note
            // off, say) and its thinking would otherwise sit there under "自己想起来的" with no message
            // after them, and the person would see what was meant to stay unsaid. What it did is in
            // the settings line; a note taken again is in its own table.
            withContext(NonCancellable) {
                if (sent().isEmpty()) {
                    db.messages().deleteProactiveSince(conversationId, since)
                    // Nor did it bring the conversation up the list.
                    val newest = db.messages().newest(conversationId, 1).firstOrNull()?.createdAt ?: 0L
                    db.conversations().touch(conversationId, maxOf(conversation.updatedAt, newest))
                }
            }
        }
    }

    private suspend fun searchHistoryCall(conversationId: Long, call: ToolCall): ToolOutcome {
        val owner = db.conversations().get(conversationId)
            ?: return ToolOutcome("当前会话不存在，无法查阅聊天记录。", "查记录失败")
        val args = ToolArgs.parse(call.arguments)
            ?: return ToolOutcome("查询参数不是合法 JSON，请重新调用。", "查记录参数无效")
        if (call.name == ToolSpecs.readChatMessage.name) {
            val id = ToolArgs.id(args["message_id"])
                ?: return ToolOutcome("需要一个有效的 message_id。", "缺少消息编号")
            val local = if (owner.isGroup) db.messages().groupOriginalMessage(conversationId, id) else null
            val row = local ?: run {
                if (owner.isGroup && !owner.groupShareOutside)
                    return ToolOutcome("只能读本群记录；外部会话共享已关闭。", "群聊未授权跨会话阅读")
                db.messages().authorizedOriginalMessage(conversationId, id)
            } ?: return ToolOutcome("找不到可读取的原始消息：可能不存在、已删除或源会话未共享。", "这条原文不可读取")
            val offset = (ToolArgs.int(args["start"]) ?: 0).coerceIn(0, row.content.length)
            return ToolOutcome(ChatHistorySearch.fullMessage(row, offset, 12_000), "读取了原始消息 #$id")
        }
        if (call.name != ToolSpecs.searchChatHistory.name)
            return ToolOutcome("本轮没有提供这个工具，请直接用文字回答。", "工具不可用")

        val name = ToolArgs.text(args, "companion")?.trim().orEmpty()
        val target = if (name.isNotEmpty()) {
            val matches = db.companions().all().filter { it.name.trim().equals(name, ignoreCase = true) }
            if (matches.size != 1)
                return ToolOutcome("角色「$name」找不到唯一匹配，请改用已创建角色的完整名字。", "角色名称不明确")
            matches.single().id
        } else null
        val scope = (ToolArgs.text(args,"scope")?.trim()?.lowercase()
            ?: if (target == null) "all" else "private")
        if (scope !in setOf("private", "group", "all"))
            return ToolOutcome("scope 只能是 private、group 或 all。", "查询范围错误")
        val date = ToolArgs.text(args,"date")?.trim().orEmpty()
        val bounds = if (date.isNotEmpty()) ChatHistorySearch.dayBounds(date) else null
        if (date.isNotEmpty() && bounds == null)
            return ToolOutcome("日期不正确，请写 YYYY-MM-DD。", "查询日期有误")
        val since = bounds?.first
        val before = bounds?.second
        val keyword = ToolArgs.text(args,"keyword")?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
        val sourceId = ToolArgs.id(args["conversation_id"])
        val offset = (ToolArgs.int(args["offset"]) ?: 0).coerceIn(0, 200_000)
        val limit = (ToolArgs.int(args["limit"]) ?: ChatHistorySearch.PAGE_DEFAULT)
            .coerceIn(1, ChatHistorySearch.PAGE_MAX)
        val dao = db.messages()
        if (owner.isGroup && sourceId == conversationId) {
            val ownTotal = dao.groupHistoryCount(conversationId, keyword)
            val own = dao.groupHistoryRows(conversationId, keyword, limit, offset)
            val realNames = db.companions().all().associate { it.id to it.name.trim().ifEmpty { "TA" } }
            return ToolOutcome(ChatHistorySearch.page(own, ownTotal, offset, realNames, settings.current().userName),
                "检索了本群真实历史：符合条件 $ownTotal 条")
        }
        if (owner.isGroup && !owner.groupShareOutside)
            return ToolOutcome("本群记录可通过 conversation_id 查询；其他会话共享已关闭。", "群聊未授权跨会话阅读")
        val total = dao.countAuthorizedHistory(conversationId, target, sourceId, scope, keyword, since, before)
        val rows = dao.queryAuthorizedHistory(conversationId, target, sourceId, scope, keyword, since, before, limit, offset)
        val names = db.companions().all().associate { it.id to it.name.trim().ifEmpty { "TA" } }
        return ToolOutcome(
            ChatHistorySearch.page(rows, total, offset, names, settings.current().userName),
            "检索了允许共享的真实聊天记录：符合条件 $total 条",
        )
    }

    private suspend fun worldContext(conversationId: Long, latestText: String, s: AppSettings): String? {
        val owner = db.conversations().get(conversationId)
        if (owner?.isGroup == true && !owner.groupShareOutside) {
            return if (GroupMemoryBridge.isHistoryQuestion(latestText))
                "这个群的「共享其他会话记录」已关闭，无法读取别的私聊。若用户希望互通，可在群聊设置里自行开启；不要谎称看过未授权内容。"
            else null
        }
        val all = db.companions().all()
        val named = all.filter { ta -> ta.name.trim().takeIf { it.isNotEmpty() }?.let { latestText.contains(it) } == true }.take(3)
        val keywords = latestText
            .split(' ', '，', '。', '！', '？', '、', ':', '：', ',', '.', '!', '?', '@', '＠')
            .map { it.trim() }
            .filter { it.length in 2..22 && it !in setOf("你们", "他们", "我们", "什么", "今天", "昨天", "知道") }
            .take(3)
        val rows = buildList {
            for (term in keywords) {
                val safe = term.replace("%", "").replace("_", "")
                addAll(db.messages().sharedMatches(conversationId, "%$safe%", 12))
            }
            for (ta in named) addAll(db.messages().sharedForCompanion(conversationId, ta.id, 12))
            addAll(db.messages().sharedRecent(conversationId, 8))
        }
        // A directly named private conversation takes priority over general recently shared
        // messages. Group members may read real excerpts from other chats when the original
        // conversation and this group both permit sharing. Do not infer hidden records.
        val isHistory = ChatHistorySearch.wanted(latestText)
        val directSections = if (isHistory && named.isNotEmpty()) {
            GroupMemoryBridge.orderedTargets(latestText, named).take(2).map { ta ->
                val total = db.messages().countAuthorizedHistory(
                    conversationId, ta.id, null, "private", null, null, null,
                )
                val found = db.messages().queryAuthorizedHistory(
                    conversationId, ta.id, null, "private", null, null, null, 30, 0,
                )
                "【针对「${ta.name}」私聊的实际数据库查询】\n" +
                    ChatHistorySearch.page(found, total, 0, all.associate { it.id to it.name }, s.userName)
            }
        } else emptyList()
        // General context is secondary, and should not be portrayed as a complete record.
        val ordinary = if (isHistory) null else GroupChats.sharedContext(rows, all, s.userName)
        val hint = if (isHistory)
            "用户明确要求查看聊天历史：本机已提供原始消息页。如需更多页、指定日期、关键词或查看某条长消息，请调用 search_chat_history / read_chat_message；没有查询的地方不能推断，更不能说只能看摘要。只有原会话允许共享才能读取，已删除内容无法恢复。"
            else null
        return (directSections + listOfNotNull(hint, ordinary)).joinToString("\n\n").ifBlank { null }
    }

    /** When asked about its past, seek real rows in this group's own Room history,
     * separate from recap and from other conversations' sharing permissions. */
    private suspend fun groupHistoryContext(conversationId: Long, userText: String, userName: String): String? {
        val explicit = GroupHistoryRecall.requested(userText)
        if (!explicit && userText.trim().length < 6) return null
        val dao = db.messages()
        val candidates = mutableListOf<com.cleo.cleos.data.db.SharedMessageRow>()
        // Exact-match retrieval is fast; offline character-bigram ranking finds more
        // distant phrasings without inventing a record or uploading an embedding index.
        for (keyword in GroupHistoryRecall.keywords(userText)) {
            candidates += dao.groupHistoryRows(conversationId, keyword, 12, 0)
        }
        candidates += dao.groupHistoryRows(conversationId, null, if (explicit) 700 else 250, 0)
        val ranked = if (explicit) GroupMemoryRanker.rank(userText, candidates, 12)
            else GroupMemoryRanker.related(userText, candidates)
        val names = db.companions().all().associate { it.id to it.name.trim().ifEmpty { "TA" } }
        return GroupHistoryRecall.format(ranked, names, userName)
    }

    /** The WorkManager cycle is awaited by its worker (not fire-and-forget).
     * Its reply path shares the existing per-room busy lock and explicit daily API cap.
     * Two unanswered background lines at most; it never wakes a stale abandoned group.
     */
    suspend fun backgroundGroupCycle(conversationId: Long, now: Long): List<MessageEntity> {
        val room = db.conversations().get(conversationId) ?: return emptyList()
        if (!room.isGroup || room.groupMode != GroupAutoMode.MODE || busy(conversationId))
            return emptyList()
        val recent = db.messages().newest(conversationId, 60)
        val user = recent.firstOrNull { it.role == "user" && it.note == null && it.error == null }
        val newestAI = recent.firstOrNull { it.role == "assistant" &&
            it.error == null && it.content.isNotBlank() }
        val unanswered = recent.takeWhile { it.id != user?.id }
            .count { it.role == "assistant" && it.proactive && it.error == null }
        val remaining = Int.MAX_VALUE // Unlimited per day; background activity remains bounded.
        val hour = java.time.LocalTime.now().hour
        if (!GroupAutonomy.allowed(room.groupMode, hour, user?.createdAt,
                newestAI?.createdAt, unanswered, now, remaining))
            return emptyList()
        val current = db.conversations().get(conversationId) ?: return emptyList()
        if (current.groupMode != GroupAutoMode.MODE || busy(conversationId)) return emptyList()
        val beforeId = recent.firstOrNull()?.id ?: 0
        val completed = CompletableDeferred<List<MessageEntity>>()
        val launched = start(conversationId) {
            try {
                groupReply(conversationId, current, continued = true, background = true)
                val sent = db.messages().newest(conversationId, 12)
                    .filter { it.id > beforeId && it.role == "assistant" &&
                        it.proactive && it.error == null && it.content.isNotBlank() }
                completed.complete(sent)
            } catch (e: CancellationException) {
                completed.cancel()
                throw e
            } catch (e: Exception) {
                completed.complete(emptyList())
                throw e
            }
        }
        return if (launched) completed.await() else emptyList()
    }

    /** A user-invoked, cancellable continuation; never an endless background chat. */
    private val groupContinuationAt = ConcurrentHashMap<Long, Long>()

    fun continueGroup(conversationId: Long) {
        scope.launch {
            val convo = db.conversations().get(conversationId) ?: return@launch
            if (!convo.isGroup || convo.groupMode == 2) return@launch
            val now = System.currentTimeMillis()
            val previous = groupContinuationAt[conversationId] ?: 0L
            if (now - previous < 10_000) return@launch
            groupContinuationAt[conversationId] = now
            start(conversationId) {
                repeat(3) { round ->
                    currentCoroutineContext().ensureActive()
                    val current = db.conversations().get(conversationId) ?: return@start
                    if (!current.isGroup || current.groupMode == 2) return@start
                    val before = db.messages().newest(conversationId, 16).firstOrNull {
                        it.role == "assistant" && it.senderCompanionId != null &&
                            it.error == null && it.content.isNotBlank()
                    }?.id
                    groupReply(conversationId, current, continued = true)
                    val after = db.messages().newest(conversationId, 16).firstOrNull {
                        it.role == "assistant" && it.senderCompanionId != null &&
                            it.error == null && it.content.isNotBlank()
                    }?.id
                    if (after == before) return@start
                    if (round < 2) delay(1900)
                }
            }
        }
    }

    /** One natural group turn: every member sees the prior member's just-stored message. */
    private suspend fun groupReply(conversationId: Long, conversation: ConversationEntity,
                                  continued: Boolean = false, background: Boolean = false,
                                  resumeActors: Set<Long>? = null) {
        val s = settings.current()
        val members = db.groupMembers().idsFor(conversationId).mapNotNull { companions.get(it) }
        if (members.isEmpty()) return
        val firstHistory = Recap.sent(recaps.live(conversation), s.historySize)
        val trigger = firstHistory.lastOrNull { m ->
            (m.role == "user" && m.note == null && m.error == null) ||
                (m.role == "pat" && Pats.decode(m.content)?.who == Pats.AI) ||
                Recalls.isEvent(m) || ReactionEvents.isEvent(m)
        }
        trigger?.let { m -> answeredUpTo.merge(conversationId, m.createdAt) { a, b -> maxOf(a, b) } }
        val pat = trigger?.takeIf { it.role == "pat" }?.let { Pats.decode(it.content) }
        val patTarget = if (continued) null else pat?.targetCompanionId
        val reactedMessageId = if (continued) null else trigger
            ?.takeIf { ReactionEvents.isEvent(it) }
            ?.let { ReactionEvents.decode(it.content)?.messageId }
        val reactedOwnerId = reactedMessageId?.let { messageId ->
            val original = db.messages().get(messageId)
            if (original == null || original.conversationId != conversationId ||
                original.role != "assistant") null
            else original.senderCompanionId ?: conversation.companionId
        }
        // "Continue" begins after the last TA, not after an old @user request.
        val latestAssistant = firstHistory.lastOrNull { it.role == "assistant" && it.error == null && it.content.isNotBlank() }
        if (continued && latestAssistant == null) return
        val moneyEvent = if (continued) null else trigger?.takeIf(WalletReactiveEvents::isEvent)
        // A wallet event is an independent trigger: never inherit an older @ mention burst.
        val userBurst = if (moneyEvent != null) listOf(moneyEvent)
            else if (continued || trigger?.role != "user") emptyList()
            else GroupTurnRecovery.activeUserMessages(firstHistory)
        val latestText = if (continued) latestAssistant?.content.orEmpty()
            else GroupTurnRecovery.text(userBurst).ifBlank { trigger?.takeIf { it.role == "user" }?.content.orEmpty() }
        val mentions = if (continued) emptyList() else if (moneyEvent != null) {
            val allowed = WalletReactiveEvents.targetIds(moneyEvent.mentionedCompanionIds,
                members.map { it.id }.toSet())
            members.filter { it.id in allowed }
        } else GroupChats.targeted(
            latestText, GroupTurnRecovery.mentionIds(userBurst) ?: trigger?.mentionedCompanionIds, members,
        )
        val isQuestion = !continued && GroupTurnRecovery.isQuestion(latestText)
        val eventTurn = !continued && trigger?.let {
            Recalls.isEvent(it) || ReactionEvents.isEvent(it)
        } == true
        // A user action is not permission for an autonomous continuation to operate the phone.
        val allowToolCalls = !continued && trigger?.role == "user"
        val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }
        val lastSpeaker = firstHistory.lastOrNull { it.role == "assistant" }?.senderCompanionId
        val start = members.indexOfFirst { it.id == lastSpeaker }.let { if (it < 0) 0 else (it + 1) % members.size }
        val naturalOrder = members.drop(start) + members.take(start)
        val muted = conversation.groupMutedIds.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        val eligible = members.filterNot { it.id in muted }
        val plannedCandidates = if (resumeActors != null) eligible.filter { it.id in resumeActors } else when {
            patTarget != null -> eligible.filter { it.id == patTarget }
            mentions.isNotEmpty() -> mentions.filter { it.id !in muted }
            reactedMessageId != null -> eligible.filter { it.id == reactedOwnerId }
            eventTurn -> naturalOrder.filter { it.id !in muted }.take(1)
            conversation.groupMode == 2 -> emptyList()
            conversation.groupMode == 1 -> eligible
            else -> naturalOrder.filter { it.id !in muted }.take(GroupChats.MAX_OPPORTUNITIES)
        }
        if (plannedCandidates.isEmpty() && !continued && conversation.groupMode == 2 && mentions.isEmpty() && resumeActors == null) {
            note(conversationId, "这个群开启了「仅被 @ 时回复」，请 @ 一位群成员，或在群设置切换为自然聊天。")
            return
        }
        if (plannedCandidates.isEmpty() && mentions.isNotEmpty() && resumeActors == null) {
            note(conversationId, "被 @ 的角色已被设为静音，或者不在这个群中，请检查群成员设置。")
            return
        }
        val candidates = plannedCandidates.filter { ta ->
            if (continued || background) {
                life?.tick()
                life?.availability(ta.id)?.isBusy != true
            } else !deferForLife(conversationId, ta.id, input = trigger)
        }
        // No eligible actor means no model, location/weather, MCP or speech request.
        // Busy actors remain durably queued; the other actors are never re-added on wake.
        if (candidates.isEmpty()) return
        val groupListening = if (ToolGroup.Music in s.tools)
            runCatching { listening() }.getOrNull() else null
        val weatherRequest = if (!continued && trigger?.role == "user") GroupWeatherRules.parse(latestText) else null
        val groupLocation = if (!continued && trigger?.role == "user" &&
            GroupLocationRules.explicitlyRequested(latestText) && weatherRequest?.usePhoneLocation != true) {
            if (ToolGroup.Location !in s.tools) {
                note(conversationId, "查位置没成：尚未开启查位置")
                GroupLocationRules.context("用户没有在设置里开启「查位置」功能。", verified = false)
            } else {
                val outcome = try {
                    tools.run(
                        ToolCall("group-location-" + conversationId + "-" + trigger.id, ToolSpecs.getLocation.name, "{}"),
                        s, conversationId, candidates.first().id,
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ToolOutcome("定位工具暂时出错，请稍后重试。", "查位置没成：工具出错")
                }
                note(conversationId, outcome.note.ifBlank { "查位置没成：未返回结果" })
                GroupLocationRules.context(outcome.result, verified = outcome.note.startsWith("查了你的位置"))
            }
        } else null
        val groupWeather = weatherRequest?.let { request ->
            val outcome = try {
                // Uses ToolBox's existing permission switch for weather and (optionally) location.
                tools.run(
                    ToolCall("group-weather-" + conversationId + "-" + trigger?.id,
                        ToolSpecs.getWeather.name, GroupWeatherRules.arguments(request)),
                    s, conversationId, candidates.first().id,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ToolOutcome("天气服务暂时无法使用，请稍后再试。", "查天气没成：工具出错")
            }
            note(conversationId, outcome.note.ifBlank { "天气查询没有返回可用结果" })
            GroupWeatherRules.context(outcome, outcome.note.startsWith("查了"), request)
        }
        // Compute shared evidence once per group turn, not repeatedly for all AI members.
        val groupRecall = if (!continued && trigger?.role == "user")
            groupHistoryContext(conversationId, latestText, s.userName) else null
        var said = 0
        var toolExecutorReserved = false
        for (ta in candidates.take(GroupChats.MAX_MEMBERS)) {
            currentCoroutineContext().ensureActive()
            val replyLimit = if (background) 1 else if (moneyEvent != null) {
                // A group red packet addressed to three AIs invites three genuine immediate
                // reactions, subject to existing daily AI-call budget and muted members.
                mentions.size.coerceIn(1, GroupChats.MAX_MEMBERS)
            } else conversation.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)
            if (said >= replyLimit) break
            val latestConversation = db.conversations().get(conversationId) ?: return
            val raw = Recap.sent(recaps.live(latestConversation), s.historySize)
            val shaped = GroupChats.historyFor(raw, ta.id, names, conversation.companionId)
            val context = listOfNotNull(
                groupRecall,
                worldContext(conversationId, latestText, s), groupLocation, groupWeather)
                .joinToString("\n\n").ifBlank { null }
            val canOperateTools = allowToolCalls && !toolExecutorReserved
            if (canOperateTools) toolExecutorReserved = true
            show(StreamingReply(conversationId, "", thinking = false, activity = "${ta.name.trim().ifEmpty { "TA" }}正在输入"))
            try {
                if (groupTurn(conversationId, ta, members, shaped, context,
                    targeted = patTarget != null || mentions.isNotEmpty() || conversation.groupMode == 1 ||
                        eventTurn || groupLocation != null || groupWeather != null || (isQuestion && said == 0),
                    historyRequested = !continued && ChatHistorySearch.wanted(latestText),
                    allowToolCalls = canOperateTools,
                    currentMusic = groupListening,
                    background = background, lifeEvent = trigger,
                    ordinaryInput = !continued && !background)) said++
            } catch (_: LifeDeferred) {
                // This actor's interval began during streaming/delivery. Its durable
                // pending entry stays; other currently available group actors may proceed.
            } finally {
                hide(conversationId)
            }
        }
        // Keep a real group-level rolling recap as the conversation exceeds the live window.
        // The recap is only ever read by participants in this same group.
        recaps.foldLater(conversationId)
        // Each member grows only from group conversations they actually participate in.
        // No group text is copied into another member's preset personality field.
        if (said > 0) members.forEach { member ->
            scope.launch { runCatching { personalityGrowth?.maybeGrow(member.id, conversationId) } }
        }
        if (said == 0 && candidates.isNotEmpty() &&
            (mentions.isNotEmpty() || isQuestion || eventTurn || groupLocation != null || groupWeather != null)) {
            note(conversationId, "本轮群聊没有生成有效回复。可点名重试；若频繁出现，请确认角色模型与 API Key 可用，以及群聊调用额度未用完。")
        }
    }

    private suspend fun groupTurn(
        conversationId: Long,
        ta: CompanionEntity,
        members: List<CompanionEntity>,
        history: List<MessageEntity>,
        shared: String?,
        targeted: Boolean,
        historyRequested: Boolean = false,
        allowToolCalls: Boolean = false,
        currentMusic: String? = null,
        background: Boolean = false,
        lifeEvent: MessageEntity? = null,
        ordinaryInput: Boolean = true,
    ): Boolean {
        val event = if (ordinaryInput) lifeEvent ?: latestLifeInput(conversationId) else null
        val purpose = if (ordinaryInput) "reply" else "passive"
        if (deferForLife(conversationId, ta.id, purpose = purpose, input = event)) return false
        val s = settings.current()
        val key = secrets.key(ta.apiBaseUrl)
        if (key.isNullOrBlank()) {
            withContext(NonCancellable) {
                val at = stamp()
                db.messages().insert(MessageEntity(conversationId = conversationId, role = "assistant", content = "", createdAt = at,
                    error = "${ta.name.trim().ifEmpty { "TA" }}还没有可用的 API Key。", senderCompanionId = ta.id))
                db.conversations().touch(conversationId, at)
            }
            return true
        }
        val endpoint = ApiEndpoint(ta.apiBaseUrl, key, ta.apiModel)
        val endpointKey = endpoint.chatUrl + "|" + endpoint.model
        val memories = PersonalityGrowthRules.forPrompt(db.memories().allFor(ta.id), ToolGroup.Memory in s.tools)
        val (stickers, sendStickers) = stickersFor(s)
        var withImages = endpointKey !in refusesImages && MessagePictures.hasImages(history, stickers)
        var thinking = ta.deepThinking && endpointKey !in refusesThinking
        var retryTargeted = false
        var toolSupport = allowToolCalls && endpointKey !in refusesTools
        var historyTools = historyRequested && endpointKey !in refusesTools
        var toolRounds = 0
        val galleryBudget = GalleryToolBudget(ordinaryRoundLimit = 3)
        var patUsedThisTurn = false
        val recap = db.conversations().get(conversationId)?.recap
        // Use the exact per-character permissions and model, not the group's owner.
        // Location/weather are handled once per explicit group request before model turns.
        // Later is deliberately excluded: a group reply must not schedule autonomous wakes.
        val permittedGroups = groupsFor(s, ta) -
            setOf(ToolGroup.Location, ToolGroup.Weather, ToolGroup.Later)
        val externalTools = if (toolSupport) mcp.tools() else emptyList()
        val groupRule = GroupChats.turnInstruction(ta, members, targeted)
        val groupOptions = db.conversations().get(conversationId)
        val announcement = groupOptions?.groupAnnouncement?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "这段群公告由用户填写，仅供本群参与者参考：$it" }
        val extra = listOfNotNull(shared, WalletChatBridge.context(conversationId, ta.id),
            life?.context(ta.id, event?.content.orEmpty()),
            if (ordinaryInput) deferredLifeContext(ta.id, conversationId) else null,
            announcement, groupRule).joinToString("\n\n")
        if (event != null) lifeTurnInputs[conversationId] = ta.id to event
            else lifeTurnInputs.remove(conversationId)
        fun build() = Prompt.messages(
            s, ta, history, ZonedDateTime.now(),
            tools = if (toolSupport) permittedGroups else
                ((setOf(ToolGroup.Memory, ToolGroup.Pat, ToolGroup.Messages) intersect s.tools) +
                    (if (ToolGroup.Speak in s.tools && Speech.ready(s)) setOf(ToolGroup.Speak) else emptySet())),
            images = withImages, memories = memories,
            recap = recap, outside = if (toolSupport) externalTools else emptyList(),
            listening = currentMusic, stickers = stickers,
            sendStickers = sendStickers,
            extraContext = extra +
                (if (historyTools) "\n\n本群会话编号是 $conversationId。查询本群原文请用 search_chat_history 的 conversation_id=$conversationId，不需要开放其他会话的共享；查询外部会话则必须遵守各自分享开关。需要更早记录用 offset 翻页，不要猜测未读原文。" else "") +
                (if (retryTargeted) "\n\n用户已经明确提问或 @，上次没有发出有效文本。请直接回应，不要再写 SKIP。" else ""),
        )
        var messages = prepare(build())
        while (true) {
            if (deferForLife(conversationId, ta.id, purpose = purpose, input = event)) return false
            // Count actual outgoing attempts, not merely an "opportunity". Refusals and SKIP cost calls too.
            if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) == 0) return false
            val inputProxy = history.sumOf { it.content.length }.toLong() + extra.length
            db.conversations().recordGroupText(conversationId, inputProxy.coerceAtMost(200000), 0)
            // All speakers may actually send chat/voice messages; only one per user
            // request can operate on external devices, tasks, calendars or MCP.
            val speechOnly = listOf(ToolSpecs.sendMessage) +
                (if (ToolGroup.Speak in s.tools && Speech.ready(s))
                    listOf(ToolSpecs.sendVoice) else emptyList())
            val toolPool = (if (toolSupport) tools.specs(permittedGroups) +
                externalTools.map { it.spec } else speechOnly +
                    (if (ToolGroup.Memory in s.tools) tools.specs(setOf(ToolGroup.Memory)) else emptyList()) +
                    (if (ToolGroup.Pat in s.tools) tools.specs(setOf(ToolGroup.Pat)) else emptyList())) +
                (if (historyTools) listOf(ToolSpecs.searchChatHistory, ToolSpecs.readChatMessage) else emptyList())
            // WorkManager must not operate the user's device, send voice or launch
            // external services without explicit interaction on that same turn.
            val groupSpecs = if (background) emptyList() else (toolPool + ToolSpecs.feedTools).distinctBy { it.name }
            when (val step = step(conversationId, endpoint, messages, groupSpecs,
                mayRefuse = thinking || withImages || groupSpecs.isNotEmpty(),
                thinking = thinking, showThought = ta.deepThinking, wake = true,
                speakerCompanionId = ta.id, lifePurpose = purpose)) {
                Step.Refused -> {
                    if (thinking) {
                        thinking = false
                        refusesThinking += endpointKey
                    } else if (withImages) {
                        withImages = false
                        refusesImages += endpointKey
                    } else if (toolSupport || historyTools) {
                        toolSupport = false
                        historyTools = false
                        refusesTools += endpointKey
                    } else return false
                    messages = prepare(build())
                }
                is Step.Said -> {
                    val error = step.error
                    if (error != null) {
                        withContext(NonCancellable) {
                            val at = stamp()
                            db.messages().insert(MessageEntity(conversationId = conversationId, role = "assistant", content = step.text.trim(),
                                createdAt = at, error = error, thought = step.thought?.let(MessageThoughts::encode), senderCompanionId = ta.id))
                            db.conversations().touch(conversationId, at)
                        }
                        return true
                    }
                    val body = step.text.trim()
                    // Count visible response characters including SKIP, not hidden model reasoning.
                    db.conversations().recordGroupText(conversationId, 0, body.length.toLong())
                    if (body.isEmpty() || GroupChats.isSkip(body)) {
                        if (targeted && !retryTargeted) {
                            retryTargeted = true
                            messages = prepare(build())
                            continue
                        }
                        if (ordinaryInput) {
                            recordLifeQuietDecision(conversationId, ta.id)
                            consumedLife(ta.id, conversationId, event)
                        }
                        return false
                    }
                    val groupEmoji = GroupReactionRules.parse(body)
                    if (groupEmoji != null) {
                        val target = GroupReactionRules.target(history, ta.id) ?: return false
                        val saved = db.withTransaction {
                            val current = db.messages().get(target.id) ?: return@withTransaction false
                            if (current.conversationId != conversationId || current.role !in setOf("user", "assistant") ||
                                current.error != null || current.content.isBlank()) return@withTransaction false
                            val old = MessageReactions.decode(current.reactions)
                            val updated = GroupReactionRules.add(old, groupEmoji, ta.id, stamp())
                            if (old == updated) return@withTransaction false
                            db.messages().setReactions(current.id, MessageReactions.encode(updated))
                            if (ordinaryInput) recordLifeQuietDecision(conversationId, ta.id)
                            true
                        }
                        if (saved && ordinaryInput) consumedLife(ta.id, conversationId, event)
                        return saved
                    }
                    storeAssistantBubbles(
                        conversationId,
                        AssistantBubbleSplitter.split(body, GroupChats.MAX_BUBBLES, null),
                        step.thought?.let(MessageThoughts::encode),
                        quiet = true,
                        proactive = background,
                        speakerCompanionId = ta.id,
                        lifeReceipt = if (ordinaryInput) lifeReceipt(conversationId) else null,
                    )
                    if (ordinaryInput) consumedLife(ta.id, conversationId, event)
                    return true
                }
                is Step.Called -> {
                    if (!galleryBudget.accept(step.message.toolCalls)) {
                        note(conversationId, galleryBudget.lastError)
                        return false
                    }
                    val offeredNames = groupSpecs.map { it.name }.toSet()
                    var spoke = false
                    val results = mutableListOf<ApiMessage>()
                    for (call in step.message.toolCalls) {
                        currentCoroutineContext().ensureActive()
                        val lifeBlocked = deferForLife(conversationId, ta.id, purpose = purpose, input = event)
                        var outcome = try {
                            when {
                                lifeBlocked -> ToolOutcome("角色已进入工作、休息或睡眠，本次工具尚未执行。恢复可用后请继续原请求。", "生活日程：工具等待")
                                call.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name) &&
                                    historyTools && call.name in offeredNames ->
                                    searchHistoryCall(conversationId, call)
                                call.name in ToolSpecs.speaking && call.name in offeredNames -> {
                                    val args = ToolArgs.parse(call.arguments)
                                    val words = args?.let { ToolArgs.text(it, "text") }?.trim().orEmpty()
                                    if (words.isBlank()) {
                                        ToolOutcome("没有文本，不能发出消息。", "消息内容为空")
                                    } else {
                                        val voice = if (call.name == ToolSpecs.sendVoice.name) {
                                            val selectedVoice = ta.speechVoiceOverride?.trim().orEmpty()
                                            val voiceSettings = if (selectedVoice.isEmpty()) s else
                                                s.copy(speechVoices = s.speechVoices +
                                                    (s.speechEngine to selectedVoice), speechVoice = selectedVoice)
                                            try {
                                                speaker.speak(voiceSettings, words)
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                note(conversationId, "语音生成失败，已改为文字：" +
                                                    (e.message ?: e.javaClass.simpleName))
                                                null
                                            }
                                        } else null
                                        storeAssistantBubbles(conversationId,
                                            if (voice == null) AssistantBubbleSplitter.split(
                                                words, GroupChats.MAX_BUBBLES, null) else listOf(words),
                                            voice = voice, quiet = true, speakerCompanionId = ta.id,
                                            lifeReceipt = lifeReceipt(conversationId).takeIf {
                                                ordinaryInput && step.message.toolCalls.all { it.name in ToolSpecs.speaking } &&
                                                    call == step.message.toolCalls.lastOrNull()
                                            })
                                        spoke = true
                                        ToolOutcome(ToolSpecs.SENT, "")
                                    }
                                }
                                call.name == ToolSpecs.patUser.name && call.name in offeredNames &&
                                    patUsedThisTurn ->
                                    ToolOutcome("这个角色本轮已经拍过对方，请用文字继续交流。", "本轮拍一拍次数已达上限")
                                call.name in offeredNames -> {
                                    if (call.name == ToolSpecs.patUser.name) patUsedThisTurn = true
                                    val external = externalTools.firstOrNull { it.fnName == call.name }
                                    if (external != null) {
                                        val live = StreamingReply(conversationId, "", thinking = false,
                                            activity = "在用" + external.serverName)
                                        runOutside(conversationId, external, call, live, ta.name)
                                    } else {
                                        // Permissions may have been turned off while the model was thinking.
                                        tools.run(call, settings.current().copy(
                                            tools = settings.current().tools intersect permittedGroups),
                                            conversationId, ta.id)
                                    }
                                }
                                else -> ToolOutcome("本轮未授权此工具，未执行。", "群聊拒绝未提供的工具")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ToolOutcome("调用失败：" + (e.message ?: e.javaClass.simpleName),
                                "群聊工具调用出错")
                        }
                        if (outcome.webSendUrls.isNotEmpty()) {
                            outcome = persistWebImages(conversationId, ta.id, outcome.webSendUrls,
                                background, null)
                        }
                        if (outcome.tablePayload != null) {
                            outcome = persistTable(conversationId, ta.id, outcome.tablePayload, background, null)
                        }
                        withContext(NonCancellable) {
                            val at = stamp()
                            db.messages().insert(MessageEntity(
                                conversationId = conversationId, role = "tool", content = outcome.result,
                                toolCallId = call.id, createdAt = at, note = outcome.note))
                            db.conversations().touch(conversationId, at)
                        }
                        results += ApiMessage("tool", outcome.result, toolCallId = call.id)
                    }
                    if (step.message.toolCalls.isNotEmpty() &&
                        step.message.toolCalls.all { it.name in ToolSpecs.speaking }) {
                        if (spoke && ordinaryInput) consumedLife(ta.id, conversationId, event)
                        return spoke
                    }
                    messages = messages + step.message + results
                    toolRounds++
                }
                is Step.Ended -> return false
            }
        }
    }

    private suspend fun reply(conversationId: Long) {
        val group = db.conversations().get(conversationId)
        if (group?.isGroup == true) {
            // Mode 3 is explicit opt-in. A new USER message may start a bounded
            // natural follow-on; an emoji, withdrawal or wake never starts it.
            val startedByUser = db.messages().newest(conversationId, 1).firstOrNull()?.let {
                it.role == "user" && it.note == null && it.error == null &&
                    !WalletReactiveEvents.isEvent(it)
            } == true
            val firstBefore = db.messages().newest(conversationId, 20).firstOrNull {
                it.role == "assistant" && it.error == null && it.content.isNotBlank()
            }?.id
            groupReply(conversationId, group)
            var previous = db.messages().newest(conversationId, 20).firstOrNull {
                it.role == "assistant" && it.error == null && it.content.isNotBlank()
            }?.id
            var emitted = previous != firstBefore
            for (round in 0 until GroupAutoMode.EXTRA_ROUNDS) {
                currentCoroutineContext().ensureActive()
                val fresh = db.conversations().get(conversationId) ?: break
                val remaining = Int.MAX_VALUE // Bounded auto-follow-up rounds remain unchanged.
                if (!GroupAutoMode.shouldContinue(fresh.groupMode, startedByUser, emitted, remaining, round))
                    break
                delay(GroupAutoMode.GAP_MS)
                currentCoroutineContext().ensureActive()
                val still = db.conversations().get(conversationId) ?: break
                if (still.groupMode != GroupAutoMode.MODE) break
                groupReply(conversationId, still, continued = true)
                val next = db.messages().newest(conversationId, 20).firstOrNull {
                    it.role == "assistant" && it.error == null && it.content.isNotBlank()
                }?.id
                emitted = next != previous
                previous = next
                if (!emitted) break
            }
            return
        }
        val startedAt = System.currentTimeMillis()
        val s = settings.current()
        val ta = taOf(conversationId)
        val lifeEvent = latestLifeInput(conversationId)
        if (deferForLife(conversationId, ta.id, input = lifeEvent)) return
        // What isn't folded into the recap yet, as much of it as the window takes. The recap
        // stands in for everything before.
        val conversation = db.conversations().get(conversationId)
        val history = if (conversation == null) emptyList() else Recap.sent(recaps.live(conversation), s.historySize)
        // A pat is a text-like interaction; only an actual latest voice message uses the heard model.
        val lastInput = history.lastOrNull { m ->
            m.role == "user" || Recalls.isEvent(m) || ReactionEvents.isEvent(m) || (m.role == "pat" && Pats.decode(m.content)?.who == Pats.AI)
        }
        val use = ta.modelFor(heard = lastInput?.role == "user" && lastInput.audio != null)
        val key = secrets.key(use.baseUrl)
        if (key.isNullOrBlank()) {
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "",
                    createdAt = System.currentTimeMillis(),
                    error = if (use.forHeard) "回语音用的模型还没有填 API Key。去设置 ›「模型」里填上。" else "还没有填 API Key。去设置里填上，就能聊了。",
                ),
            )
            return
        }
        // The dots at once. Getting ready can take a while (an outside service slow to list its
        // tools, one that is down until it times out), and the chat should show meanwhile that
        // a reply is coming, with stop.
        show(StreamingReply(conversationId, "", thinking = false))
        try {
            val endpoint = ApiEndpoint(use.baseUrl, key, use.model)
            val endpointKey = endpoint.chatUrl + "|" + endpoint.model
            // What this reply takes in: whatever the person sends from here on is answered after it.
            lastInput?.let { m -> answeredUpTo.merge(conversationId, m.createdAt) { a, b -> maxOf(a, b) } }
            val recap = conversation?.recap
            val now = ZonedDateTime.now()
            var groups = if (endpointKey in refusesTools) emptySet() else groupsFor(s, ta).let { if (lastInput?.role == "user") it else it - ToolGroup.Apps }
            // The tools of the MCP services switched on come along whenever tools do.
            var outside = if (endpointKey in refusesTools) emptyList() else mcp.tools()
            var historyTools = lastInput?.role == "user" && ChatHistorySearch.wanted(lastInput.content) &&
                endpointKey !in refusesTools
            val (stickers, sendStickers) = stickersFor(s)
            var withImages = endpointKey !in refusesImages && MessagePictures.hasImages(history, stickers)
            var thinking = ta.deepThinking && endpointKey !in refusesThinking
            // What the TA remembers, read once for this reply.
            val memories = PersonalityGrowthRules.forPrompt(db.memories().allFor(ta.id), ToolGroup.Memory in s.tools)
            // What it noted that has come due while the two are talking: this reply takes it in,
            // and once it has gone through, it is dealt with (Later wakes nobody for it).
            val due = if (ta.proactive) db.later().dueFor(ta.id, System.currentTimeMillis()) else emptyList()
            // The song playing as this reply begins, told beside the time. Asked for even when the model
            // takes no tools: it is something to know, not something to do.
            val heard = if (ToolGroup.Music in s.tools) runCatching { listening() }.getOrNull() else null
            val calls = callsOutside(history)
            val shared = worldContext(conversationId, lastInput?.content.orEmpty(), s)
            val walletEvidence = WalletChatBridge.context(conversationId, ta.id)
            val lifeEvidence = life?.context(ta.id, lastInput?.content.orEmpty())
            val deferredInputs = deferredLifeContext(ta.id, conversationId)
            (lifeEvent ?: lastInput)?.let { lifeTurnInputs[conversationId] = ta.id to it }
            fun build() = Prompt.messages(
                s, ta, history, now, groups, withImages, memories, recap, outside, due.map { it.what }, heard, stickers, sendStickers, calls = calls,
                extraContext = listOfNotNull(shared, walletEvidence, lifeEvidence, deferredInputs)
                    .joinToString("\n\n").ifBlank { null },
            )
            var messages = prepare(build())
            var rounds = 0
            val galleryBudget = GalleryToolBudget(ordinaryRoundLimit = MAX_TOOL_ROUNDS)
            // What the last refusal made this reply leave out, and when.
            var leftOut: LeftOut? = null
            suspend fun done() {
                val durableReply = db.messages().newest(conversationId, 128).any {
                    it.role == "assistant" && it.call == null && it.createdAt >= startedAt &&
                        it.error == null && (it.content.isNotBlank() || it.images != null || it.audio != null)
                }
                if (durableReply) consumedLife(ta.id, conversationId, lifeEvent ?: lastInput)
                remember(leftOut, conversationId, endpointKey)
                recaps.foldLater(conversationId)
                // Own voice/style matures only after actual completed conversation.
                scope.launch {
                    runCatching { personalityGrowth?.maybeGrow(ta.id, conversationId) }
                }
                if (due.isNotEmpty()) db.later().delete(due.map { it.id })
                db.messages().repliedSince(conversationId, startedAt).takeIf { it.isNotEmpty() }?.let { replied(ta, conversationId, it) }
            }
            while (true) {
                val mayRefuse = rounds == 0 && (groups.isNotEmpty() || outside.isNotEmpty() || historyTools || withImages || thinking)
                val specs = tools.specs(groups) + outside.map { it.spec } +
                    (if (historyTools) listOf(ToolSpecs.searchChatHistory, ToolSpecs.readChatMessage) else emptyList())
                when (val step = step(conversationId, endpoint, messages, specs, mayRefuse, thinking, showThought = ta.deepThinking)) {
                    is Step.Ended -> {
                        if (step.ok) done()
                        return
                    }
                    // Only in a wake.
                    is Step.Said -> return
                    Step.Refused -> {
                        // The thinking switch goes first: it was asked for on top of the rest. Then
                        // pictures: many more models take tools than take pictures.
                        val what = when {
                            thinking -> Left.Thinking
                            withImages -> Left.Images
                            else -> Left.Tools
                        }
                        leftOut = LeftOut(what, at = System.currentTimeMillis())
                        when (what) {
                            Left.Thinking -> thinking = false
                            Left.Images -> withImages = false
                            Left.Tools -> {
                                groups = emptySet()
                                outside = emptyList()
                                historyTools = false
                            }
                        }
                        messages = prepare(build())
                    }
                    is Step.Called -> {
                        if (!galleryBudget.accept(step.message.toolCalls)) {
                            note(conversationId, galleryBudget.lastError)
                            hide(conversationId)
                            return
                        }
                        val results = runTools(conversationId, step, s.copy(tools = groups), ta.id, outside, ta.name)
                        if (results.any { it.images.isNotEmpty() }) withImages = true
                        // Only messages sent: that was the whole reply. Asking again would bring
                        // nothing new, or a "发好了".
                        if (step.message.toolCalls.all { it.name in ToolSpecs.speaking }) {
                            done()
                            hide(conversationId)
                            return
                        }
                        messages = messages + step.message + results
                        rounds++
                    }
                }
            }
        } catch (_: LifeDeferred) {
            hide(conversationId)
            return
        } catch (e: Throwable) {
            // Stopped (or failed) while getting ready or while a tool ran: no stream is open to
            // clear the live row on its way out.
            if (_streaming.value[conversationId]?.finished != true) hide(conversationId)
            throw e
        }
    }

    private enum class Left { Thinking, Images, Tools }

    private class LeftOut(val what: Left, val at: Long)

    /**
     * Once a reply got through without what a refusal made it leave out, that is what the
     * model can't take: it is left out from now on, and the chat says so.
     */
    private suspend fun remember(out: LeftOut?, conversationId: Long, endpointKey: String) {
        if (out == null) return
        val line = when (out.what) {
            Left.Thinking -> THINKING_REFUSED.also { refusesThinking += endpointKey }
            Left.Images -> IMAGES_REFUSED.also { refusesImages += endpointKey }
            Left.Tools -> TOOLS_REFUSED.also { refusesTools += endpointKey }
        }
        note(conversationId, line, at = out.at - 1)
    }

    /** Pictures become data: URLs just before sending; one that can't be read is left out. */
    private suspend fun prepare(messages: List<ApiMessage>): List<ApiMessage> = messages.map { m ->
        if (m.images.isEmpty()) m else m.copy(images = m.images.mapNotNull { images.dataUrl(it) })
    }

    private sealed interface Step {
        class Ended(val ok: Boolean) : Step

        /** A wake's answer without calls, not stored: it may be SKIP, and the caller decides. */
        class Said(val text: String, val error: String?, val thought: MessageThought?) : Step

        /**
         * The first request failed the way requests fail on a model that can't take what
         * was in them: tools, or pictures.
         */
        data object Refused : Step

        /**
         * [savedId]: the row the text said before the calls went into, if there was any to store.
         * [thought]: the thinking, when it has no row yet (the first message sent will take it).
         */
        class Called(val message: ApiMessage, val savedId: Long?, val thought: MessageThought? = null, val requestedBubbles: Int? = null) : Step
    }

    /**
     * One request: streams it to the screen, stores what came back, says what's next. [showThought]:
     * the TA's thinking switch. Off, a model that thinks all the same (or a relay that didn't pass
     * on "don't") only shows the dots while it does, and nothing of it is kept.
     *
     * [wake]: the TA deciding on its own whether to say something (see [wake]). Nothing is shown
     * while it decides, what it sends is marked as its own, and an answer without calls comes
     * back as [Step.Said], unstored, since it may be SKIP.
     */
    private suspend fun step(
        conversationId: Long,
        endpoint: ApiEndpoint,
        messages: List<ApiMessage>,
        specs: List<ToolSpec>,
        mayRefuse: Boolean,
        thinking: Boolean,
        showThought: Boolean,
        wake: Boolean = false,
        speakerCompanionId: Long? = null,
        lifePurpose: String = "reply",
    ): Step {
        val actor = speakerCompanionId ?: taOf(conversationId).id
        val goal = goalExecuting[conversationId]?.let { goalStore?.byId(it.first) }
        val blocked = if (goal != null) deferGoalForLife(goal)
            else deferForLife(conversationId, actor, purpose = lifePurpose)
        if (blocked) return Step.Ended(false)
        val startedAt = System.currentTimeMillis()
        val text = StringBuilder()
        // What goes back to the model within this turn, and what the person gets to read.
        val reasoning = StringBuilder()
        val thinkingText = StringBuilder()
        var thinkingFrom = 0L
        var thinkingMs: Long? = null
        // The thinking as the live reply shows it once the words have begun.
        var thoughtShown = ""
        var calls = emptyList<ToolCall>()
        var error: String? = null
        var status: Int? = null
        fun doneThinking() {
            if (thinkingText.isNotEmpty() && thinkingMs == null) {
                thinkingMs = System.currentTimeMillis() - thinkingFrom
                thoughtShown = thinkingText.toString()
            }
        }
        fun thought() = thinkingText.toString().trim().takeIf { it.isNotEmpty() }?.let {
            doneThinking()
            MessageThought(it, thinkingMs ?: 0)
        }
        fun live(reply: StreamingReply) {
            if (!wake) show(reply)
        }
        live(StreamingReply(conversationId, "", thinking = false))
        try {
            client.stream(endpoint, messages, specs, thinking,
                notice={ state -> note(conversationId,state) }).collect { event ->
                if (!lifeCanContinue(conversationId, actor, lifePurpose)) throw LifeDeferred()
                when (event) {
                    is ChatEvent.Delta -> {
                        doneThinking()
                        text.append(event.text)
                        live(StreamingReply(conversationId, "", thinking = false, thought = thoughtShown, thoughtMs = thinkingMs))
                    }
                    is ChatEvent.Reasoning -> {
                        if (event.sendBack) reasoning.append(event.text)
                        if (showThought) {
                            if (thinkingText.isEmpty()) thinkingFrom = System.currentTimeMillis()
                            thinkingText.append(event.text)
                        }
                        if (text.isEmpty()) live(StreamingReply(conversationId, "", thinking = true, thought = thinkingText.toString()))
                    }
                    is ChatEvent.ToolCalls -> calls = event.calls
                }
            }
        } catch (_: LifeDeferred) {
            hide(conversationId)
            return Step.Ended(false)
        } catch (e: CancellationException) {
            // Half of what a wake was deciding to say is not a message.
            if (!wake) withContext(NonCancellable) { finish(conversationId, startedAt, text.toString(), STOPPED, keepEmpty = false, thought()) }
            throw e
        } catch (e: ChatException) {
            error = e.message
            status = e.status
        } catch (e: Exception) {
            error = "出错了：${e.message ?: e.javaClass.simpleName}"
        }
        // A model that can't take tools, pictures or the thinking switch turns the first
        // request down before writing a word. The caller asks again without them.
        if (error != null && mayRefuse && text.isEmpty() && status in REFUSED_STATUSES) return Step.Refused

        if (!lifeCanContinue(conversationId, actor, lifePurpose)) return Step.Ended(false)

        val shown = thought()
        val requested = if (wake) null else AssistantBubbleSplitter.requestedCount(messages.lastOrNull { it.role == "user" }?.content)
        return if (error == null && calls.isNotEmpty()) {
            val body = text.toString()
            val sentBack = reasoning.toString().ifEmpty { null }
            val speaks = calls.any { it.name in ToolSpecs.speaking }
            val id = if (speaks && body.isBlank()) null else storeAssistantBubbles(
                conversationId,
                if (body.isBlank()) listOf(body) else AssistantBubbleSplitter.split(body, MAX_MESSAGES, requested),
                shown?.let(MessageThoughts::encode),
                proactive = wake,
                quiet = wake,
                toolCalls = calls.takeUnless { speaks }.orEmpty(),
                reasoning = sentBack.takeUnless { speaks },
                speakerCompanionId = speakerCompanionId,
            )
            Step.Called(ApiMessage("assistant", body, calls, reasoning = sentBack), savedId = id, thought = shown.takeIf { id == null }, requestedBubbles = requested)
        } else if (wake) {
            Step.Said(text.toString(), error, shown)
        } else {
            finish(conversationId, startedAt, text.toString(), error, keepEmpty = true, shown, requested)
            Step.Ended(ok = error == null)
        }
    }

    /**
     * Runs the calls, storing each result with its line for the chat. Sent messages come
     * first: each is the TA speaking and becomes a bubble, a moment after the one before, the
     * way messages arrive when someone types them one by one. Then the other calls, in order.
     * The results go back in the order of the calls. [wake]: as in [step]. [inCall]: the phone call
     * this is in (callReply): nothing shows in the chat, and every row is marked as the call's.
     */
    private suspend fun runTools(
        conversationId: Long,
        step: Step.Called,
        s: AppSettings,
        companionId: Long,
        outside: List<McpTool>,
        ai: String,
        wake: Boolean = false,
        inCall: Long? = null,
    ): List<ApiMessage> {
        val quiet = wake || inCall != null
        val said = step.message.content
        val (sends, others) = step.message.toolCalls.partition { it.name in ToolSpecs.speaking }
        val results = HashMap<String, ApiMessage>()
        var previous = said.takeIf { it.isNotBlank() }
        var sent = 0
        // The step's thinking, while nothing it put in the chat has it yet.
        var thought = step.thought?.let(MessageThoughts::encode)
        // What the person said lately, newest first: what a message's quote is looked up in.
        var recent: List<MessageEntity>? = null
        suspend fun quoteIn(args: JsonObject?): MessageQuote? {
            val quoted = args?.let { ToolArgs.text(it, "quote") }?.trim().orEmpty()
            if (quoted.isEmpty() || ToolArgs.isNone(quoted)) return null
            val said = recent ?: db.messages().newest(conversationId, UNANSWERED_LOOKBACK)
                .filter { it.role == "user" && it.note == null }
                .also { recent = it }
            return MessageQuotes.find(said, quoted)?.let(MessageQuotes::of)
        }
        for (call in sends) {
            val goalRun=goalExecuting[conversationId]
            val goal=goalRun?.let { goalStore?.byId(it.first)?.takeIf { row ->
                row.version==it.second && row.status=="RUNNING" } }
            val lifeBlocked = if (goal != null) deferGoalForLife(goal)
                else deferForLife(conversationId, companionId,
                    purpose = if (inCall != null) "passive" else if (wake) "wake" else "reply")
            if (lifeBlocked) {
                results[call.id] = ApiMessage("tool", "生活日程已开始，发送动作尚未执行；可用后继续原请求。", toolCallId = call.id)
                continue
            }
            if (goal!=null) {
                val allowed=goalStore?.beginTool(goal.id,goal.version,call.id,call.name,call.arguments,false)==true
                if(!allowed) {
                    results[call.id]=ApiMessage("tool","发送动作未执行，正在等待用户确认。",toolCallId=call.id)
                    continue
                }
            } else if(goalRun!=null) {
                results[call.id]=ApiMessage("tool","目标已停止或变更；动作未执行。",toolCallId=call.id)
                continue
            }
            val args = ToolArgs.parse(call.arguments)
            val words = args?.let { ToolArgs.text(it, "text") }?.trim().orEmpty()
            val result = when {
                words.isEmpty() -> "没有内容，没发出去。"
                sent >= MAX_MESSAGES -> "一次最多发 $MAX_MESSAGES 条，这条没发出去。"
                else -> {
                    previous?.let {
                        // Typing the next one: the dots, for a moment that grows a little with what was just said.
                        if (!quiet) show(StreamingReply(conversationId, "", thinking = false))
                        delay(AssistantBubbleDelivery.gapAfter(it))
                    }
                    // A voice message is made first (a voice service can take a few seconds). One that
                    // can't be made goes as text instead: what the TA said isn't lost.
                    var voice: MessageAudio? = null
                    var why: String? = null
                    if (call.name == ToolSpecs.sendVoice.name) {
                        if (!quiet) show(StreamingReply(conversationId, "", thinking = false, activity = "在录语音"))
                        try {
                            voice = speaker.speak(s, words)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            why = e.message ?: e.javaClass.simpleName
                        }
                    }
                    // One the model can't place is simply not shown as a quote.
                    val quote = if (call.name == ToolSpecs.sendMessage.name) quoteIn(args) else null
                    val parts = if (call.name == ToolSpecs.sendMessage.name && inCall == null) {
                        AssistantBubbleSplitter.split(words, MAX_MESSAGES - sent,
                            step.requestedBubbles.takeIf { sends.size == 1 && said.isBlank() })
                    } else listOf(words)
                    storeAssistantBubbles(
                        conversationId, parts, thought, quote, voice,
                        proactive = wake, inCall = inCall, quiet = quiet,
                        lifeReceipt = lifeReceipt(conversationId).takeIf {
                            !wake && inCall == null && others.isEmpty() && call == sends.lastOrNull()
                        },
                    )
                    thought = null
                    why?.let { note(conversationId, "语音没做出来（$it），这句改成了文字") }
                    previous = words
                    sent += parts.size
                    if (why != null) "语音没做出来（$why），这句已经改成文字发出去了。" else ToolSpecs.SENT
                }
            }
            if(goal!=null) goalStore?.toolResult(goal.id,goal.version,call.id,call.name,
                success=result==ToolSpecs.SENT,verified=false,
                summary="发送操作有结果；请在聊天记录确认")
            results[call.id] = ApiMessage("tool", result, toolCallId = call.id)
        }
        if (sends.isNotEmpty() && others.isNotEmpty()) {
            // Held back in step(): stored now, after the messages and right before the results.
            withContext(NonCancellable) {
                db.messages().insert(
                    MessageEntity(
                        conversationId = conversationId,
                        role = "assistant",
                        content = "",
                        createdAt = System.currentTimeMillis(),
                        toolCalls = ToolCallCodec.encode(others),
                        reasoning = step.message.reasoning,
                        // Only if no message went out to carry it.
                        thought = thought,
                        proactive = wake,
                        call = inCall,
                    ),
                )
            }
        }
        val galleryVision = ArrayList<ApiMessage>()
        for (call in others) {
            val outer = outside.firstOrNull { it.fnName == call.name }
            // What was said before the calls stays up; the screen switches to the stored
            // copy (savedId) as soon as it is in the list.
            val live = StreamingReply(
                conversationId,
                "",
                thinking = false,
                activity = if (call.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name))
                    "在查真实聊天记录"
                else outer?.let { "在用${it.serverName}" } ?: tools.activity(call.name),
            )
            if (!quiet) show(live)
            val goalRun=goalExecuting[conversationId]
            val goalToken=goalRun?.let { goalStore?.byId(it.first)?.takeIf { row ->
                row.version==it.second && row.status=="RUNNING" } }
            val lifeBlocked = if (goalToken != null) deferGoalForLife(goalToken)
                else deferForLife(conversationId, companionId,
                    purpose = if (inCall != null) "passive" else if (wake) "wake" else "reply")
            val canExecute=if(lifeBlocked || (goalRun!=null && goalToken==null)) false
            else if(goalToken!=null && call.name!=GoalCheckpointTool.spec.name) {
                goalStore?.beginTool(goalToken.id,goalToken.version,call.id,call.name,
                    call.arguments,outer!=null)==true
            } else true
            var success=true
            var outcome = try {
                if (lifeBlocked) {
                    success=false
                    ToolOutcome("角色已进入工作、休息或睡眠，本次工具未执行。恢复可用后请继续原请求。", "生活日程：工具等待")
                } else if(!canExecute) {
                    success=false
                    ToolOutcome("目标已经暂停、取消、变更或等待授权；工具未执行。", "目标未执行")
                } else if(goalToken!=null && call.name==GoalCheckpointTool.spec.name) {
                    val args=ToolArgs.parse(call.arguments)
                    ToolOutcome(if(args==null) "检查点 JSON 无效，尚未更新目标。" else
                        goalStore?.checkpoint(goalToken.id,goalToken.version,args).orEmpty(),
                        "更新目标检查点")
                } else if(!canExecute) {
                    success=false
                    ToolOutcome("该目标操作尚未授权，未执行。请用户核对目标卡片中的待授权工具。", "等待目标授权")
                } else if (call.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name))
                    searchHistoryCall(conversationId, call)
                else if (outer != null) runOutside(conversationId, outer, call, live, ai)
                else tools.run(call, s, conversationId, companionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                success=false
                ToolOutcome("工具出错了：${e.message ?: e.javaClass.simpleName}", "${outer?.spec?.action ?: tools.action(call.name)}出错了")
            }
            // A web link is not a sent image. Download privately, persist a real
            // MessageImages bubble, then report SUCCESS only after the DB write.
            if (outcome.webSendUrls.isNotEmpty()) {
                outcome = persistWebImages(conversationId, companionId, outcome.webSendUrls, wake, inCall)
            }
            if (outcome.tablePayload != null) {
                outcome = persistTable(conversationId, companionId, outcome.tablePayload, wake, inCall)
            }
            // The AI must actually send a photo message, not merely view a thumbnail.
            // Keep the source gallery photo untouched and independently preserve the
            // imported copy in private chat storage (normal image backup/restore path).
            if (outcome.gallerySendIds.isNotEmpty()) {
                val imported = ArrayList<MessageImage>()
                try {
                    for (id in outcome.gallerySendIds.take(3)) {
                        val uri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )
                        val stored = images.import(uri, maxEdge = 2048, prefix = "ai-gallery-")
                        imported += MessageImage(stored.file, stored.width, stored.height)
                    }
                    if (imported.isEmpty()) throw IllegalStateException("没有成功导入图片")
                    withContext(NonCancellable) {
                        val at = System.currentTimeMillis()
                        db.messages().insert(
                            MessageEntity(
                                conversationId = conversationId,
                                role = "assistant",
                                content = "",
                                images = MessageImages.encode(imported),
                                createdAt = at,
                                proactive = wake,
                                call = inCall,
                                senderCompanionId = companionId,
                            ),
                        )
                        db.conversations().touch(conversationId, at)
                    }
                    outcome = outcome.copy(
                        result = "已向当前聊天发送了" + imported.size +
                            "张真实图片消息，原相册照片未修改。",
                        note = "已发送" + imported.size + "张相册照片",
                    )
                } catch (e: CancellationException) {
                    images.delete(imported.map { it.file })
                    throw e
                } catch (e: Exception) {
                    images.delete(imported.map { it.file })
                    outcome = outcome.copy(
                        result = "图片没有发送成功：" + (e.message ?: "相册访问或图片解码出错") +
                            "。请重新检查系统相册授权，不能声称已经发出。",
                        note = "发送相册图片失败",
                    )
                }
            }
            if(goalToken!=null && call.name!=GoalCheckpointTool.spec.name && canExecute) {
                // Read-only responses prove a tool was invoked; side effects stay unverified
                // until independently verified by user or a typed verification tool.
                goalStore?.toolResult(goalToken.id,goalToken.version,call.id,call.name,
                    success,verified=success && !GoalRules.sideEffect(call.name,outer!=null),
                    summary=if(success) "实际工具调用有返回，详见会话工具记录" else "工具执行失败")
            }
            // The tool has acted (a todo exists now), so its result is stored even if
            // stop was pressed meanwhile: the history should match what happened.
            withContext(NonCancellable) {
                val at = System.currentTimeMillis()
                db.messages().insert(
                    MessageEntity(
                        conversationId = conversationId,
                        role = "tool",
                        content = outcome.result,
                        createdAt = at,
                        toolCallId = call.id,
                        note = outcome.note,
                        proactive = wake,
                        call = inCall,
                    ),
                )
                outcome.request?.let {
                    db.messages().insert(
                        MessageEntity(conversationId = conversationId, role = "request", content = SecretRequests.encode(it), createdAt = at, call = inCall),
                    )
                }
            }
            results[call.id] = ApiMessage("tool", outcome.result, toolCallId = call.id)
            if (outcome.galleryImages.isNotEmpty()) {
                galleryVision += ApiMessage(
                    role = "user",
                    content = "以下图片由已获用户授权的本机相册工具提供，并非用户新发言。" +
                        "请只根据真实图像回答；若当前模型不能看图须明确说明。",
                    images = outcome.galleryImages,
                )
            }
        }
        return step.message.toolCalls.mapNotNull { results[it.id] } + galleryVision
    }

    /**
     * A call to an MCP service's tool. Unless the tool only reads, or the person said to always
     * allow it, they are asked first, on a card in the chat: a service can take orders and
     * payments, and a TA must not do that on its own.
     */
    /** Shared by normal chat and group chat. Never claim a URL was sent as a photo. */
    private suspend fun persistTable(
        conversationId: Long, companionId: Long, payload: String,
        wake: Boolean, inCall: Long?,
    ): ToolOutcome {
        if (ChatTables.decode(payload) == null)
            return ToolOutcome("表格结构未通过验证，未发出，必须修正行列数。", "发送表格失败")
        return try {
            withContext(NonCancellable) {
                val now = System.currentTimeMillis()
                db.messages().insert(MessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = payload,
                    createdAt = now,
                    proactive = wake,
                    call = inCall,
                    senderCompanionId = companionId,
                ))
                db.conversations().touch(conversationId, now)
            }
            ToolOutcome("已经真正发送了可展开、横向滚动和复制的表格卡片。不要再重复Markdown版本。", "已发送表格")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolOutcome("表格没有保存成功：" + (e.message ?: "数据库写入失败") +
                "。不要声称已发出去。", "发送表格失败")
        }
    }

    private suspend fun persistWebImages(
        conversationId: Long,
        companionId: Long,
        urls: List<String>,
        wake: Boolean = false,
        inCall: Long? = null,
    ): ToolOutcome {
        val imported = ArrayList<MessageImage>()
        try {
            for (url in urls.take(3)) {
                val stored = tools.importWebImageForChat(url)
                imported += MessageImage(stored.file, stored.width, stored.height)
            }
            if (imported.isEmpty()) throw IllegalStateException("没有成功下载网络图片")
            withContext(NonCancellable) {
                val at = System.currentTimeMillis()
                db.messages().insert(MessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "",
                    images = MessageImages.encode(imported),
                    createdAt = at,
                    proactive = wake,
                    call = inCall,
                    senderCompanionId = companionId,
                ))
                db.conversations().touch(conversationId, at)
            }
            return ToolOutcome("已把${imported.size}张网络图片作为真正的图片消息发送到当前聊天，可以点击打开查看；不是文字链接。",
                "已发送${imported.size}张网络图片")
        } catch (cancel: CancellationException) {
            images.delete(imported.map { it.file })
            throw cancel
        } catch (error: Exception) {
            images.delete(imported.map { it.file })
            return ToolOutcome("网络图片没有发出去，不能声称成功。原因：" +
                (error.message ?: "图片下载、格式或存储失败") +
                "。可换一张可直接下载的 HTTPS 图片链接再试。",
                "发送网络图片失败")
        }
    }

    private suspend fun runOutside(conversationId: Long, tool: McpTool, call: ToolCall, live: StreamingReply, ai: String): ToolOutcome {
        val what = "${tool.serverName}的${tool.title}"
        val server = mcp.servers.get(tool.serverId)?.takeIf { it.enabled }
            ?: return ToolOutcome("这个服务在设置里关掉了，现在用不了。", "用${what}没成：设置里关掉了")
        val args = ToolArgs.parse(call.arguments)
            ?: return ToolOutcome("参数不是合法的 JSON 对象，按参数说明重新调用。", "用${what}没成：参数写错了")
        if (server.askFirst && !tool.readOnly && tool.name !in server.allowed) {
            when (ask(conversationId, live, McpAsk(tool.serverName, tool.title, Mcp.preview(args)))) {
                Answer.No -> return ToolOutcome(
                    "对方没有同意，这次没有调用。需要的话在聊天里问问对方。",
                    "没让${ai.ifBlank { "TA" }}用$what",
                )
                Answer.Always -> mcp.servers.allow(server.id, tool.name)
                Answer.Yes -> Unit
            }
            show(live)
        }
        return try {
            ToolOutcome(mcp.call(server, tool, args), "用了$what")
        } catch (e: McpException) {
            ToolOutcome("没有调用成功：${e.message}。照实告诉对方没成，别编结果。", "用${what}没成：${e.message.orEmpty().lineSequence().first()}")
        }
    }

    /** Shows the card and waits for the person. No answer in [ASK_WAIT] counts as no. */
    private suspend fun ask(conversationId: Long, live: StreamingReply, question: McpAsk): Answer {
        val answer = CompletableDeferred<Answer>()
        asks[conversationId] = answer
        show(live.copy(activity = null, asking = question))
        return try {
            withTimeoutOrNull(ASK_WAIT) { answer.await() } ?: Answer.No
        } finally {
            asks.remove(conversationId, answer)
        }
    }

    /** The person answered the card in [conversationId]. */
    fun answer(conversationId: Long, answer: Answer) {
        asks[conversationId]?.complete(answer)
    }

    /** Each bubble is its own database message; all speaking paths share this pacing. */
    private suspend fun storeAssistantBubbles(
        conversationId: Long,
        parts: List<String>,
        thought: String? = null,
        quote: MessageQuote? = null,
        voice: MessageAudio? = null,
        proactive: Boolean = false,
        inCall: Long? = null,
        quiet: Boolean = false,
        toolCalls: List<ToolCall> = emptyList(),
        reasoning: String? = null,
        speakerCompanionId: Long? = null,
        lifeReceipt: String? = null,
    ): Long? {
        var savedId: Long? = null
        AssistantBubbleDelivery.deliver(
            parts,
            waiting = { if (!quiet) show(StreamingReply(conversationId, "", thinking = false)) },
        ) { index, words ->
            // A stop can prevent the next message, but never half-save the one already sent.
            withContext(NonCancellable) {
                val at = System.currentTimeMillis()
                savedId = db.messages().insert(
                    MessageEntity(
                        conversationId = conversationId,
                        role = "assistant",
                        content = words,
                        createdAt = at,
                        thought = thought.takeIf { index == 0 },
                        quote = quote?.takeIf { index == 0 }?.let(MessageQuotes::encode),
                        audio = voice?.takeIf { index == 0 }?.let(MessageAudios::encode),
                        proactive = proactive,
                        call = inCall,
                        toolCalls = toolCalls.takeIf { index == parts.lastIndex && it.isNotEmpty() }?.let(ToolCallCodec::encode),
                        reasoning = reasoning.takeIf { index == parts.lastIndex },
                        senderCompanionId = speakerCompanionId,
                        toolCallId = lifeReceipt.takeIf { index == parts.lastIndex },
                    ),
                )
                db.conversations().touch(conversationId, at)
            }
            if (!quiet) hide(conversationId)
        }
        return savedId
    }

    private suspend fun finish(
        conversationId: Long,
        startedAt: Long,
        body: String,
        error: String?,
        keepEmpty: Boolean,
        thought: MessageThought? = null,
        requestedBubbles: Int? = null,
    ) {
        if (error == null) {
            storeAssistantBubbles(conversationId, AssistantBubbleSplitter.split(body, MAX_MESSAGES, requestedBubbles),
                thought?.let(MessageThoughts::encode), lifeReceipt = lifeReceipt(conversationId))
        } else if (body.isNotEmpty() || keepEmpty) {
            withContext(NonCancellable) {
                db.messages().insert(
                    MessageEntity(
                        conversationId = conversationId,
                        role = "assistant",
                        content = body,
                        createdAt = startedAt,
                        error = error,
                        thought = thought?.let(MessageThoughts::encode),
                    ),
                )
                db.conversations().touch(conversationId, System.currentTimeMillis())
            }
        }
        hide(conversationId)
    }

    private suspend fun note(conversationId: Long, text: String, at: Long = System.currentTimeMillis()) {
        withContext(NonCancellable) {
            db.messages().insert(MessageEntity(conversationId = conversationId, role = "note", content = "", createdAt = at, note = text))
        }
    }

    companion object {
        private const val LIFE_GOAL_WAIT = "[生活日程等待] "
        const val DEFAULT_TITLE = "新对话"
        const val STOPPED = "已停止"

        /** Round trips with tools in one reply before it is cut off, against a model stuck calling. */
        const val MAX_TOOL_ROUNDS = 5

        /** Messages sent in one go: past this it is a flood, not a conversation. */
        const val MAX_MESSAGES = 8

        /** How long the TA waits after the person's last message before answering: long enough for the next one. */
        const val REPLY_WAIT = 2_000L

        /** How long typing (or a voice message's words) can hold the answer up, counted from the last message. */
        private const val HOLD_MAX = 20_000L
        private const val HOLD_CHECK = 300L

        /** How far back to look for what the person said and wasn't answered, or what the TA quotes. */
        private const val UNANSWERED_LOOKBACK = 30

        /** How long a call to an outside service waits for the person to allow it. */
        private const val ASK_WAIT = 10 * 60_000L

        /**
         * How endpoints turn down tools or pictures a model can't take: 400 (SiliconFlow,
         * vLLM), 404 (OpenRouter finds no endpoint for it), 422 (strict validators).
         */
        private val REFUSED_STATUSES = setOf(400, 404, 422)

        /**
         * What a TA can do on the phone: what needs no picture, card or message of its own in the
         * chat, and leaves the sound alone (music_control would talk over the call).
         */
        private val CALL_TOOLS = setOf(
            ToolGroup.Todos, ToolGroup.Diary, ToolGroup.AiDiary, ToolGroup.Memory, ToolGroup.Letters, ToolGroup.Weather,
            ToolGroup.Location, ToolGroup.Later, ToolGroup.Alarm, ToolGroup.Calendar, ToolGroup.Apps,
        )
        private const val TOOLS_REFUSED = "这个模型不接受工具调用，这次没带工具。想让 TA 记待办、查天气，换一个支持工具的模型。"
        private const val IMAGES_REFUSED = "这个模型看不了图片，这次只发了文字。想让 TA 看图，换一个能看图的模型。"
        private const val THINKING_REFUSED = "这个模型不认深度思考的开关，这次照常回复了，之后也不再带这个开关。"
        private const val TOO_MANY_ROUNDS = "连着用了太多次工具，先停在这里。"
    }
}
