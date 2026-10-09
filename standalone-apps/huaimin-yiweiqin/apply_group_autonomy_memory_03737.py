#!/usr/bin/env python3
"""v0.37.37 opt-in persisted group autonomy plus ranked evidence over own saved messages.

No new Android permission, no new DB schema, no always-running background service.
The app's existing WorkManager periodically considers a group, notifies only for
real AI messages, and conservatively stops talking when no human replies.
"""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
def edit(rel,old,new,label):
    p=root/rel
    s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1: raise RuntimeError(f"{label}: expected exactly one anchor, got {n}")
    p.write_text(s.replace(old,new,1),encoding="utf-8")

repo="app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
app="app/src/main/java/com/cleo/cleos/CleosApp.kt"
opts="app/src/main/java/com/cleo/cleos/ui/chat/GroupOptionsDialog.kt"

edit(repo,
'''        val dao = db.messages()
        val picked = mutableListOf<com.cleo.cleos.data.db.SharedMessageRow>()
        for (keyword in GroupHistoryRecall.keywords(userText)) {
            picked += dao.groupHistoryRows(conversationId, keyword, 8, 0)
            if (picked.size >= 12) break
        }
        // No matching words: give a bounded old window, not a made-up memory.
        if (picked.isEmpty()) picked += dao.groupHistoryRows(conversationId, null, 8, 20)
        val names = db.companions().all().associate { it.id to it.name.trim().ifEmpty { "TA" } }
        return GroupHistoryRecall.format(picked.distinctBy { it.id }.take(12), names, userName)
''',
'''        val dao = db.messages()
        val candidates = mutableListOf<com.cleo.cleos.data.db.SharedMessageRow>()
        // Exact-match retrieval is fast; offline character-bigram ranking finds more
        // distant phrasings without inventing a record or uploading an embedding index.
        for (keyword in GroupHistoryRecall.keywords(userText)) {
            candidates += dao.groupHistoryRows(conversationId, keyword, 12, 0)
        }
        candidates += dao.groupHistoryRows(conversationId, null, 700, 0)
        val ranked = GroupMemoryRanker.rank(userText, candidates, 12)
        val names = db.companions().all().associate { it.id to it.name.trim().ifEmpty { "TA" } }
        return GroupHistoryRecall.format(ranked, names, userName)
''',"rank real group-history evidence")

edit(repo,
'''    private suspend fun groupReply(conversationId: Long, conversation: ConversationEntity, continued: Boolean = false) {
''',
'''    private suspend fun groupReply(conversationId: Long, conversation: ConversationEntity,
                                  continued: Boolean = false, background: Boolean = false) {
''',"group background state")

edit(repo,
'''            if (said >= conversation.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)) break
''',
'''            if (said >= if (background) 1 else conversation.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)) break
''',"one AI group voice per background turn")

edit(repo,
'''                    allowToolCalls = canOperateTools,
                    currentMusic = groupListening)) said++
''',
'''                    allowToolCalls = canOperateTools,
                    currentMusic = groupListening,
                    background = background)) said++
''',"propagate background marker")

edit(repo,
'''        currentMusic: String? = null,
    ): Boolean {
''',
'''        currentMusic: String? = null,
        background: Boolean = false,
    ): Boolean {
''',"mark safe autonomous turn")

edit(repo,
'''            val groupSpecs = toolPool.distinctBy { it.name }
''',
'''            // WorkManager must not operate the user's device, send voice or launch
            // external services without explicit interaction on that same turn.
            val groupSpecs = if (background) emptyList() else toolPool.distinctBy { it.name }
''',"disable side-effecting background tools")

edit(repo,
'''                        quiet = true,
                        speakerCompanionId = ta.id,
''',
'''                        quiet = true,
                        proactive = background,
                        speakerCompanionId = ta.id,
''',"persist background message as proactively generated")

edit(repo,
'''    /** A user-invoked, cancellable continuation; never an endless background chat. */
''',
'''    /** The WorkManager cycle is awaited by its worker (not fire-and-forget).
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
        val remaining = if (room.groupUsedDay == LocalDate.now().toEpochDay())
            room.groupDailyLimit - room.groupCallsToday else room.groupDailyLimit
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
''',"WorkManager-driven opt-in real group conversation")

edit(app,
'''        container.notifier.channels()
''',
'''        container.notifier.channels()
        // Only mode=3 groups can spend model calls; disabled groups are never queried.
        com.cleo.cleos.ai.GroupAutonomy.install(this)
''',"boot persistent scheduler")

edit(opts,
'''3 to "自主接话（收到消息后最多再聊 4 轮）",''',
'''3 to "自主群聊（自然接话 + 适度后台主动发言）",''',"background mode text")

edit(opts,
'''"只在本群收到用户新消息后续聊，没人接话会停；最多额外 4 轮，受每日请求额度限制。切换模式或按停止会中断，不会在后台无限聊天。",''',
'''"用户发言后最多自然续聊 4 轮；后台由 Android 按系统条件约每 30 分钟提供一次考虑机会（不保证准点）。仅限白天、最近 24 小时有互动、距离上次回复至少 50 分钟；连续 2 次后台主动消息没人回应则停止。关闭此模式即禁止后台模型请求，仍受每天群调用额度限制。",''',
"explain opt-in inexact background")

for name,dest in (
 ("GroupMemoryRanker.kt","app/src/main/java/com/cleo/cleos/ai/GroupMemoryRanker.kt"),
 ("GroupMemoryRankerTest.kt","app/src/test/java/com/cleo/cleos/ai/GroupMemoryRankerTest.kt"),
 ("GroupAutonomy.kt","app/src/main/java/com/cleo/cleos/ai/GroupAutonomy.kt"),
 ("GroupAutonomyTest.kt","app/src/test/java/com/cleo/cleos/ai/GroupAutonomyTest.kt"),
):
    out=root/dest
    out.parent.mkdir(parents=True,exist_ok=True)
    copyfile(here/name,out)
print("0.37.37 opt-in persisted group autonomy and actual-message ranking integrated")
