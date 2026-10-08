#!/usr/bin/env python3
"""v0.37.31: searchable original messages, paging, exact-message reads, with sharing controls."""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
app=root/"app/src/main/java/com/cleo/cleos"
repo=app/"ai/ChatRepository.kt"
dao=app/"data/db/Daos.kt"
tool=app/"ai/Tools.kt"

def once(path: Path, old: str, new: str):
    s=path.read_text(encoding="utf-8")
    count=s.count(old)
    if count != 1:
        raise SystemExit(f"history search anchor {path.name}: {count} matches, expected 1: {old[:130]!r}")
    path.write_text(s.replace(old,new,1),encoding="utf-8")

# Room read-only lookups; no migration, no change to any existing message or history flags.
sql_base = ("FROM messages m JOIN conversations c ON c.id = m.conversationId "
    "WHERE m.conversationId != :excludeConversationId AND c.historyShareAllowed = 1 "
    "AND m.role IN ('user','assistant') AND m.note IS NULL AND m.error IS NULL "
    "AND m.content != '' "
    "AND (:companionId IS NULL OR "
    "(c.isGroup = 0 AND c.companionId = :companionId) OR "
    "(c.isGroup = 1 AND EXISTS (SELECT 1 FROM conversation_members gm "
    "WHERE gm.conversationId = c.id AND gm.companionId = :companionId))) "
    "AND (:conversationFilter IS NULL OR c.id = :conversationFilter) "
    "AND (:scope = 'all' OR (:scope = 'private' AND c.isGroup = 0) OR (:scope = 'group' AND c.isGroup = 1)) "
    "AND (:keyword IS NULL OR instr(m.content, :keyword) > 0) "
    "AND (:since IS NULL OR m.createdAt >= :since) "
    "AND (:before IS NULL OR m.createdAt < :before)")
project_cols=("m.id AS id, m.conversationId AS conversationId, c.title AS conversationTitle, "
    "c.companionId AS ownerCompanionId, m.senderCompanionId AS senderCompanionId, "
    "m.role AS role, m.content AS content, m.createdAt AS createdAt")
def lit(sql): return '"' + sql.replace("\\","\\\\").replace('"','\\"') + '"'

needle='''    suspend fun sharedDirectForCompanion(excludeConversationId: Long, companionId: Long, limit: Int): List<SharedMessageRow>
'''
addition='''
    /** Search the ACTUAL saved message bodies in allowed conversations, page by page. */
    @Query(%s)
    suspend fun queryAuthorizedHistory(
        excludeConversationId: Long, companionId: Long?, conversationFilter: Long?,
        scope: String, keyword: String?, since: Long?, before: Long?, limit: Int, offset: Int,
    ): List<SharedMessageRow>

    @Query(%s)
    suspend fun countAuthorizedHistory(
        excludeConversationId: Long, companionId: Long?, conversationFilter: Long?,
        scope: String, keyword: String?, since: Long?, before: Long?,
    ): Int

    @Query(%s)
    suspend fun authorizedOriginalMessage(excludeConversationId: Long, messageId: Long): SharedMessageRow?
'''%(
    lit("SELECT "+project_cols+" "+sql_base+" ORDER BY m.createdAt DESC, m.id DESC LIMIT :limit OFFSET :offset"),
    lit("SELECT COUNT(*) "+sql_base),
    lit("SELECT "+project_cols+" FROM messages m JOIN conversations c ON c.id=m.conversationId "
        "WHERE m.id=:messageId AND m.conversationId!=:excludeConversationId "
        "AND c.historyShareAllowed=1 AND m.role IN ('user','assistant') "
        "AND m.note IS NULL AND m.error IS NULL AND m.content != '' LIMIT 1"),
)
once(dao,needle,needle+addition)

specs='''object ToolSpecs {
    /** Never offered in normal chat automatically; only on a user's history request. */
    val searchChatHistory = ToolSpec(
        name = "search_chat_history",
        groups = emptySet(),
        action = "查询真实聊天记录",
        description = "按角色、关键词、日期、页码从设备已保存且允许共享的其他聊天会话逐条读取原始消息。问到其他角色私聊、之前说过什么、原话是什么时优先调用；不要把摘要当原始记录。可多次调用：offset=0 开始，下一页使用返回的 offset。不确定角色名就不要乱填。",
        parameters = schema(
            "companion" to prop("string", "其他角色的准确名字，比如「小艺」；省略表示所有允许共享的会话"),
            "keyword" to prop("string", "可选正文关键词，按字面匹配；若想知道概况请先不传"),
            "scope" to prop("string", "private 仅私聊；group 仅群聊；all 全部。指定角色默认 private，否则 all"),
            "date" to prop("string", "可选日期 YYYY-MM-DD，只看那一天"),
            "conversation_id" to prop("integer", "可选：上次查到的具体会话编号"),
            "offset" to prop("integer", "结果页起点，默认 0；下一页采用上一页提示"),
            "limit" to prop("integer", "本页多少条，默认 20，上限 30"),
        ),
    )
    val readChatMessage = ToolSpec(
        name = "read_chat_message",
        groups = emptySet(),
        action = "读取完整原始消息",
        description = "按 search_chat_history 返回的消息编号读取一条原始消息全文。内容太长可以分段：start 为字符起点。只能读取允许共享且仍存在的记录，不可恢复已删除消息。",
        parameters = schema(
            required = listOf("message_id"),
            "message_id" to prop("integer", "原始消息编号"),
            "start" to prop("integer", "从正文的第几个字符开始，默认 0"),
        ),
    )
'''
once(tool,'object ToolSpecs {\n',specs)

# Concrete repository lookup for both normal single chats and group chats.
handler='''    private suspend fun searchHistoryCall(conversationId: Long, call: ToolCall): ToolOutcome {
        val owner = db.conversations().get(conversationId)
            ?: return ToolOutcome("当前会话不存在，无法查阅聊天记录。", "查记录失败")
        if (owner.isGroup && !owner.groupShareOutside)
            return ToolOutcome("这个群已关闭「共享其他会话记录」，不能跨会话查询。需要用户在群设置亲自开启。", "群聊没有授权跨会话查询")
        val args = ToolArgs.parse(call.arguments)
            ?: return ToolOutcome("查询参数不是合法 JSON，请重新调用。", "查记录参数无效")
        if (call.name == ToolSpecs.readChatMessage.name) {
            val id = ToolArgs.id(args["message_id"])
                ?: return ToolOutcome("需要一个有效的 message_id。", "缺少消息编号")
            val row = db.messages().authorizedOriginalMessage(conversationId, id)
                ?: return ToolOutcome("找不到这条可共享的原始消息：可能不存在、已删除或原会话不允许共享，无法还原已删除内容。", "这条原文不可读取")
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
        val total = dao.countAuthorizedHistory(conversationId, target, sourceId, scope, keyword, since, before)
        val rows = dao.queryAuthorizedHistory(conversationId, target, sourceId, scope, keyword, since, before, limit, offset)
        val names = db.companions().all().associate { it.id to it.name.trim().ifEmpty { "TA" } }
        return ToolOutcome(
            ChatHistorySearch.page(rows, total, offset, names, settings.current().userName),
            "检索了允许共享的真实聊天记录：符合条件 $total 条",
        )
    }

'''
once(repo,'''    private suspend fun worldContext(conversationId: Long, latestText: String, s: AppSettings): String? {''',
    handler+'''    private suspend fun worldContext(conversationId: Long, latestText: String, s: AppSettings): String? {''')

# Existing preloaded excerpts prime the model to think it has only a summary. On explicit
# questions, pull first pages of original stored content and indicate page counts.
first='''        val directSections = if (GroupMemoryBridge.isHistoryQuestion(latestText) && named.isNotEmpty()) {
            GroupMemoryBridge.orderedTargets(latestText, named).mapNotNull { ta ->
                GroupMemoryBridge.directExcerpt(
                    ta, db.messages().sharedDirectForCompanion(conversationId, ta.id, 20), all, s.userName,
                )
            }
        } else emptyList()
        val ordinary = GroupChats.sharedContext(rows, all, s.userName)
        val missing = if (GroupMemoryBridge.isHistoryQuestion(latestText) && named.isNotEmpty() && directSections.isEmpty())
            "当前没有检索到这些角色允许共享的私聊记录（可能未有私聊、原会话禁止共享或内容不在记录中），不要编造，也不要说永远不能跨会话读取。"
            else null
        return (directSections + listOfNotNull(missing, ordinary)).joinToString("\\n\\n").ifBlank { null }'''
second='''        val isHistory = ChatHistorySearch.wanted(latestText)
        val directSections = if (isHistory && named.isNotEmpty()) {
            GroupMemoryBridge.orderedTargets(latestText, named).take(2).map { ta ->
                val total = db.messages().countAuthorizedHistory(
                    conversationId, ta.id, null, "private", null, null, null,
                )
                val found = db.messages().queryAuthorizedHistory(
                    conversationId, ta.id, null, "private", null, null, null, 30, 0,
                )
                "【针对「¤{ta.name}」私聊的实际数据库查询】\\n" +
                    ChatHistorySearch.page(found, total, 0, all.associate { it.id to it.name }, s.userName)
            }
        } else emptyList()
        // General context is secondary, and should not be portrayed as a complete record.
        val ordinary = if (isHistory) null else GroupChats.sharedContext(rows, all, s.userName)
        val hint = if (isHistory)
            "用户明确要求查看聊天历史：本机已提供原始消息页。如需更多页、指定日期、关键词或查看某条长消息，请调用 search_chat_history / read_chat_message；没有查询的地方不能推断，更不能说只能看摘要。只有原会话允许共享才能读取，已删除内容无法恢复。"
            else null
        return (directSections + listOfNotNull(hint, ordinary)).joinToString("\\n\\n").ifBlank { null }'''.replace("¤","$")
once(repo,first,second)

# Pass history intent from the full user-message burst, not just the last '?' message.
once(repo,'''                    targeted = patTarget != null || mentions.isNotEmpty() || conversation.groupMode == 1 || groupLocation != null || (isQuestion && said == 0))) said++''',
'''                    targeted = patTarget != null || mentions.isNotEmpty() || conversation.groupMode == 1 || groupLocation != null || (isQuestion && said == 0),
                    historyRequested = !continued && ChatHistorySearch.wanted(latestText))) said++''')
once(repo,'''        shared: String?,
        targeted: Boolean,
    ): Boolean {
        val s = settings.current()''',
'''        shared: String?,
        targeted: Boolean,
        historyRequested: Boolean = false,
    ): Boolean {
        val s = settings.current()''')

once(repo,'''        var retryTargeted = false
        val groupRule = GroupChats.turnInstruction(ta, members, targeted)''',
'''        var retryTargeted = false
        var historyTools = historyRequested && endpointKey !in refusesTools &&
            db.conversations().get(conversationId)?.groupShareOutside == true
        var historyRounds = 0
        val groupRule = GroupChats.turnInstruction(ta, members, targeted)''')

once(repo,'''        val extra = listOfNotNull(shared, announcement, groupRule).joinToString("\\n\\n")
        fun build() = Prompt.messages(''',
'''        val extra = listOfNotNull(shared, announcement, groupRule).joinToString("\\n\\n")
        fun build() = Prompt.messages(''')
# Add an explicit instruction without leaking history into prompt when sharing is off.
once(repo,'''            extraContext = if (retryTargeted) extra +
                "\\n\\n刚刚是用户在群里的明确提问或 @，上次没有发出有效文本。请像真人直接回应对方的问题；不能确定就说明不知道，不要再写 SKIP。" else extra,
        )
        var messages = prepare(build())''',
'''            extraContext = extra +
                if (historyTools) "\\n\\n本轮明确请求查询其他聊天原始记录：优先使用 search_chat_history 而不是猜测或只引用简要摘要；需要更早记录使用 offset 翻页；不要谎称不能访问允许共享的原文。" else "" +
                if (retryTargeted) "\\n\\n用户已经明确提问或 @，上次没有发出有效文本。请直接回应，不要再写 SKIP。" else "",
        )
        var messages = prepare(build())''')
# Fix Kotlin expression ordering below with additional parentheses in follow-up replacement.
once(repo,'''            extraContext = extra +
                if (historyTools) "\\n\\n本轮明确请求查询其他聊天原始记录：优先使用 search_chat_history 而不是猜测或只引用简要摘要；需要更早记录使用 offset 翻页；不要谎称不能访问允许共享的原文。" else "" +
                if (retryTargeted) "\\n\\n用户已经明确提问或 @，上次没有发出有效文本。请直接回应，不要再写 SKIP。" else "",''',
'''            extraContext = extra +
                (if (historyTools) "\\n\\n本轮明确请求查询其他聊天原始记录：优先使用 search_chat_history 而不是猜测或只引用简要摘要；需要更早记录使用 offset 翻页；不要谎称不能访问允许共享的原文。" else "") +
                (if (retryTargeted) "\\n\\n用户已经明确提问或 @，上次没有发出有效文本。请直接回应，不要再写 SKIP。" else ""),''')

once(repo,'''            when (val step = step(conversationId, endpoint, messages, emptyList(), mayRefuse = thinking || withImages,
                thinking = thinking, showThought = ta.deepThinking, wake = true)) {''',
'''            val groupSpecs = if (historyTools) listOf(ToolSpecs.searchChatHistory, ToolSpecs.readChatMessage) else emptyList()
            when (val step = step(conversationId, endpoint, messages, groupSpecs,
                mayRefuse = thinking || withImages || groupSpecs.isNotEmpty(),
                thinking = thinking, showThought = ta.deepThinking, wake = true)) {''')

once(repo,'''                    } else if (withImages) {
                        withImages = false
                        refusesImages += endpointKey
                    } else return false
                    messages = prepare(build())''',
'''                    } else if (withImages) {
                        withImages = false
                        refusesImages += endpointKey
                    } else if (historyTools) {
                        historyTools = false
                        refusesTools += endpointKey
                    } else return false
                    messages = prepare(build())''')

once(repo,'''                is Step.Called -> {
                    // Some models invoke send_message even without a tool spec (habit from''',
'''                is Step.Called -> {
                    val lookups = step.message.toolCalls.filter {
                        it.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name)
                    }
                    if (lookups.isNotEmpty() && historyRounds < 3) {
                        // These are read-only queries. Tool-call scratch rows are not part of
                        // the spoken group history, and do not overwrite the original records.
                        step.savedId?.let { db.messages().delete(it) }
                        val responses = step.message.toolCalls.map { call ->
                            val outcome = if (call in lookups)
                                searchHistoryCall(conversationId, call)
                            else ToolOutcome("这个群聊工具没有执行，请直接用文字回答。", "")
                            ApiMessage("tool", outcome.result, toolCallId = call.id)
                        }
                        messages = messages + step.message + responses
                        historyRounds++
                        continue
                    }
                    // Some models invoke send_message even without a tool spec (habit from''')

# Single-chat answers can also query other TAs' original private or group records.
once(repo,'''            var outside = if (endpointKey in refusesTools) emptyList() else mcp.tools()
            val (stickers, sendStickers) = stickersFor(s)''',
'''            var outside = if (endpointKey in refusesTools) emptyList() else mcp.tools()
            var historyTools = lastInput?.role == "user" && ChatHistorySearch.wanted(lastInput.content) &&
                endpointKey !in refusesTools
            val (stickers, sendStickers) = stickersFor(s)''')
once(repo,'''                val mayRefuse = rounds == 0 && (groups.isNotEmpty() || outside.isNotEmpty() || withImages || thinking)
                val specs = tools.specs(groups) + outside.map { it.spec }''',
'''                val mayRefuse = rounds == 0 && (groups.isNotEmpty() || outside.isNotEmpty() || historyTools || withImages || thinking)
                val specs = tools.specs(groups) + outside.map { it.spec } +
                    (if (historyTools) listOf(ToolSpecs.searchChatHistory, ToolSpecs.readChatMessage) else emptyList())''')
once(repo,'''                                groups = emptySet()
                                outside = emptyList()
                            }''',
'''                                groups = emptySet()
                                outside = emptyList()
                                historyTools = false
                            }''')
once(repo,'''                if (outer != null) runOutside(conversationId, outer, call, live, ai) else tools.run(call, s, conversationId, companionId)''',
'''                if (call.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name))
                    searchHistoryCall(conversationId, call)
                else if (outer != null) runOutside(conversationId, outer, call, live, ai)
                else tools.run(call, s, conversationId, companionId)''')
once(repo,'''                activity = outer?.let { "在用¤{it.serverName}" } ?: tools.activity(call.name),'''.replace("¤","$"),
'''                activity = if (call.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name))
                    "在查真实聊天记录"
                else outer?.let { "在用¤{it.serverName}" } ?: tools.activity(call.name),'''.replace("¤","$"))

for name, destination in (
    ("ChatHistorySearch.kt",app/"ai/ChatHistorySearch.kt"),
    ("ChatHistorySearchTest.kt",root/"app/src/test/java/com/cleo/cleos/ai/ChatHistorySearchTest.kt"),
):
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(here/name,destination)

gradle=root/"app/build.gradle.kts"
for old,new in (('versionName = "0.37.30"','versionName = "0.37.31"'),('versionCode = 62052','versionCode = 62053')):
    once(gradle,old,new)
print("0.37.31 / 62053: true on-device authorized chat history paging and original message reads, no DB schema changes")
