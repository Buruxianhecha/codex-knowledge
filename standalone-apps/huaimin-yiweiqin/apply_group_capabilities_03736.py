#!/usr/bin/env python3
"""0.37.36: honest on-device group-history retrieval and opt-in bounded group autonomy.

Apply only after all 0.37.35 patches. Anchors must be unique.
"""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

def patch(path, old, new, label):
    p = root / path
    raw = p.read_text(encoding="utf-8")
    n = raw.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: expected one occurrence, found {n}: {old[:160]!r}")
    p.write_text(raw.replace(old, new, 1), encoding="utf-8")

ai = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
dao = "app/src/main/java/com/cleo/cleos/data/db/Daos.kt"
vm = "app/src/main/java/com/cleo/cleos/ui/chat/ChatViewModel.kt"
ui = "app/src/main/java/com/cleo/cleos/ui/chat/GroupOptionsDialog.kt"

patch(dao, '''interface MessageDao {
''',
'''interface MessageDao {
    /** An AI in the current group may read its own group history, even when external
     * conversation sharing is disabled; only live user/assistant rows, never revoked ones. */
    @Query("SELECT m.id AS id, m.conversationId AS conversationId, c.title AS conversationTitle, c.companionId AS ownerCompanionId, m.senderCompanionId AS senderCompanionId, m.role AS role, m.content AS content, m.createdAt AS createdAt FROM messages m JOIN conversations c ON c.id=m.conversationId WHERE c.id=:conversationId AND c.isGroup=1 AND m.role IN ('user','assistant') AND m.note IS NULL AND m.error IS NULL AND m.content != '' AND (:keyword IS NULL OR instr(m.content,:keyword)>0) ORDER BY m.createdAt DESC,m.id DESC LIMIT :limit OFFSET :offset")
    suspend fun groupHistoryRows(conversationId: Long, keyword: String?, limit: Int, offset: Int): List<SharedMessageRow>

    @Query("SELECT COUNT(*) FROM messages m JOIN conversations c ON c.id=m.conversationId WHERE c.id=:conversationId AND c.isGroup=1 AND m.role IN ('user','assistant') AND m.note IS NULL AND m.error IS NULL AND m.content != '' AND (:keyword IS NULL OR instr(m.content,:keyword)>0)")
    suspend fun groupHistoryCount(conversationId: Long, keyword: String?): Int

    @Query("SELECT m.id AS id, m.conversationId AS conversationId, c.title AS conversationTitle, c.companionId AS ownerCompanionId, m.senderCompanionId AS senderCompanionId, m.role AS role, m.content AS content, m.createdAt AS createdAt FROM messages m JOIN conversations c ON c.id=m.conversationId WHERE c.id=:conversationId AND c.isGroup=1 AND m.id=:messageId AND m.role IN ('user','assistant') AND m.note IS NULL AND m.error IS NULL AND m.content != '' LIMIT 1")
    suspend fun groupOriginalMessage(conversationId: Long, messageId: Long): SharedMessageRow?

''', "current-group read-only history DAO")

patch(ai,
'''        if (owner.isGroup && !owner.groupShareOutside)
            return ToolOutcome("这个群已关闭「共享其他会话记录」，不能跨会话查询。需要用户在群设置亲自开启。", "群聊没有授权跨会话查询")
        val args = ToolArgs.parse(call.arguments)
''',
'''        val args = ToolArgs.parse(call.arguments)
''', "external sharing permission after own-group tools")

patch(ai,
'''            val row = db.messages().authorizedOriginalMessage(conversationId, id)
                ?: return ToolOutcome("找不到这条可共享的原始消息：可能不存在、已删除或原会话不允许共享，无法还原已删除内容。", "这条原文不可读取")
''',
'''            val local = if (owner.isGroup) db.messages().groupOriginalMessage(conversationId, id) else null
            val row = local ?: run {
                if (owner.isGroup && !owner.groupShareOutside)
                    return ToolOutcome("只能读本群记录；外部会话共享已关闭。", "群聊未授权跨会话阅读")
                db.messages().authorizedOriginalMessage(conversationId, id)
            } ?: return ToolOutcome("找不到可读取的原始消息：可能不存在、已删除或源会话未共享。", "这条原文不可读取")
''', "read own-group original message")

patch(ai,
'''        val limit = (ToolArgs.int(args["limit"]) ?: ChatHistorySearch.PAGE_DEFAULT)
            .coerceIn(1, ChatHistorySearch.PAGE_MAX)
        val dao = db.messages()
''',
'''        val limit = (ToolArgs.int(args["limit"]) ?: ChatHistorySearch.PAGE_DEFAULT)
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
''', "current group search independent of external sharing")

patch(ai,
'''    /** A user-invoked, cancellable continuation; never an endless background chat. */
''',
'''    /** When asked about its past, seek real rows in this group's own Room history,
     * separate from recap and from other conversations' sharing permissions. */
    private suspend fun groupHistoryContext(conversationId: Long, userText: String, userName: String): String? {
        if (!GroupHistoryRecall.requested(userText)) return null
        val dao = db.messages()
        val picked = mutableListOf<com.cleo.cleos.data.db.SharedMessageRow>()
        for (keyword in GroupHistoryRecall.keywords(userText)) {
            picked += dao.groupHistoryRows(conversationId, keyword, 8, 0)
            if (picked.size >= 12) break
        }
        // No matching words: give a bounded old window, not a made-up memory.
        if (picked.isEmpty()) picked += dao.groupHistoryRows(conversationId, null, 8, 20)
        val names = db.companions().all().associate { it.id to it.name.trim().ifEmpty { "TA" } }
        return GroupHistoryRecall.format(picked.distinctBy { it.id }.take(12), names, userName)
    }

    /** A user-invoked, cancellable continuation; never an endless background chat. */
''', "bounded on-demand original group context")

patch(ai,
'''            val context = listOfNotNull(worldContext(conversationId, latestText, s), groupLocation, groupWeather)
''',
'''            val context = listOfNotNull(
                groupHistoryContext(conversationId, latestText, s.userName),
                worldContext(conversationId, latestText, s), groupLocation, groupWeather)
''', "include own-group original history in each relevant character prompt")

patch(ai,
'''        var historyTools = historyRequested && endpointKey !in refusesTools &&
            db.conversations().get(conversationId)?.groupShareOutside == true
''',
'''        var historyTools = historyRequested && endpointKey !in refusesTools
''', "group own-history tool can run with outside sharing off")

patch(ai,
'''                (if (historyTools) "\\n\\n本轮明确请求查询其他聊天原始记录：优先使用 search_chat_history 而不是猜测或只引用简要摘要；需要更早记录使用 offset 翻页；不要谎称不能访问允许共享的原文。" else "") +
''',
'''                (if (historyTools) "\\n\\n本群会话编号是 $conversationId。查询本群原文请用 search_chat_history 的 conversation_id=$conversationId，不需要开放其他会话的共享；查询外部会话则必须遵守各自分享开关。需要更早记录用 offset 翻页，不要猜测未读原文。" else "") +
''', "accurate current-conversation history tool instruction")

patch(ai,
'''            val groupSpecs = (if (toolSupport) tools.specs(permittedGroups) +
                externalTools.map { it.spec } else emptyList()) +
                (if (historyTools) listOf(ToolSpecs.searchChatHistory, ToolSpecs.readChatMessage) else emptyList())
''',
'''            // All speakers may actually send chat/voice messages; only one per user
            // request can operate on external devices, tasks, calendars or MCP.
            val speechOnly = listOf(ToolSpecs.sendMessage) +
                (if (ToolGroup.Speak in s.tools && Speech.ready(s))
                    listOf(ToolSpecs.sendVoice) else emptyList())
            val toolPool = (if (toolSupport) tools.specs(permittedGroups) +
                externalTools.map { it.spec } else speechOnly) +
                (if (historyTools) listOf(ToolSpecs.searchChatHistory, ToolSpecs.readChatMessage) else emptyList())
            val groupSpecs = toolPool.distinctBy { it.name }
''', "nonexecutor characters can still send message and voice")

patch(ui, '''                    2 to "仅 @ 或拍一拍时回复",
''',
'''                    2 to "仅 @ 或拍一拍时回复",
                    3 to "自主接话（收到消息后最多再聊 4 轮）",
''', "opt-in auto group mode")

patch(ui, '''                OutlinedTextField(
                    value = maxReplies,
''',
'''                if (mode == 3) Text(
                    "只在本群收到用户新消息后续聊，没人接话会停；最多额外 4 轮，受每日请求额度限制。切换模式或按停止会中断，不会在后台无限聊天。",
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = maxReplies,
''', "auto mode explanation")

patch(vm, '''id, mode.coerceIn(0, 2), maxReplies.coerceIn(1, 6), dailyLimit.coerceIn(1, 120),''',
'''id, mode.coerceIn(0, 3), maxReplies.coerceIn(1, 6), dailyLimit.coerceIn(1, 120),''',
"persist new group mode via existing groupMode column")

patch(ai,
'''        if (group?.isGroup == true) {
            groupReply(conversationId, group)
            return
        }
''',
'''        if (group?.isGroup == true) {
            // Mode 3 is explicit opt-in. A new USER message may start a bounded
            // natural follow-on; an emoji, withdrawal or wake never starts it.
            val startedByUser = db.messages().newest(conversationId, 1).firstOrNull()?.let {
                it.role == "user" && it.note == null && it.error == null
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
                val remaining = if (fresh.groupUsedDay == LocalDate.now().toEpochDay())
                    fresh.groupDailyLimit - fresh.groupCallsToday else fresh.groupDailyLimit
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
''', "bounded opt-in social continuation")

# Existing safe per-call budget, explicit permission switches, and no Room schema
# migration. 0.37.36 is a new candidate, not a released signed APK.
build = root / "app/build.gradle.kts"
raw = build.read_text(encoding="utf-8")
for before, after in [('versionName = "0.37.35"', 'versionName = "0.37.36"'),
                      ('versionCode = 62057', 'versionCode = 62058')]:
    if raw.count(before) != 1:
        raise RuntimeError("build version anchor mismatch: " + before)
    raw = raw.replace(before, after)
build.write_text(raw, encoding="utf-8")
for name, dest in (
    ("GroupHistoryRecall.kt", "app/src/main/java/com/cleo/cleos/ai/GroupHistoryRecall.kt"),
    ("GroupHistoryRecallTest.kt", "app/src/test/java/com/cleo/cleos/ai/GroupHistoryRecallTest.kt"),
):
    output = root / dest
    output.parent.mkdir(parents=True, exist_ok=True)
    copyfile(here / name, output)

# Event-only reactions/withdrawals must never inherit an old @ mention.
patch(ai,
'''        val userBurst = if (continued) emptyList() else GroupTurnRecovery.activeUserMessages(firstHistory)
''',
'''        val userBurst = if (continued || trigger?.role != "user") emptyList()
            else GroupTurnRecovery.activeUserMessages(firstHistory)
''', "event routing cannot steal previous @")

patch(ai,
'''            conversation.groupMode == 2 -> emptyList()
            conversation.groupMode == 1 -> eligible
            eventTurn -> naturalOrder.filter { it.id !in muted }.take(1)
''',
'''            eventTurn -> naturalOrder.filter { it.id !in muted }.take(1)
            conversation.groupMode == 2 -> emptyList()
            conversation.groupMode == 1 -> eligible
''', "recall event may reply even in only-at mode")

patch(ai,
'''            sendStickers = toolSupport && sendStickers,
''',
'''            sendStickers = sendStickers,
''', "all members can send stickers in group")

patch(ai,
'''            val toolPool = (if (toolSupport) tools.specs(permittedGroups) +
                externalTools.map { it.spec } else speechOnly) +
''',
'''            val toolPool = (if (toolSupport) tools.specs(permittedGroups) +
                externalTools.map { it.spec } else speechOnly +
                    (if (ToolGroup.Memory in s.tools) tools.specs(setOf(ToolGroup.Memory)) else emptyList())) +
''', "each AI may operate its own permitted memory tool")

print("0.37.36 group own-history, opt-in autonomous chat and speaking parity applied")
