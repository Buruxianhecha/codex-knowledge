#!/usr/bin/env python3
"""v0.37.24: continuation is about the latest TA, no stale @; meter actual attempts, memory newest-first."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base="app/src/main/java/com/cleo/cleos/"
def rep(path,old,new,count=1):
    p=root/path
    s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=count: raise SystemExit(f"{path}: wanted {count} found {n}: {old[:120]!r}")
    p.write_text(s.replace(old,new),encoding="utf-8")
chat=base+"ai/ChatRepository.kt"
rep(chat,
'''start(conversationId) { groupReply(conversationId, convo) }''',
'''start(conversationId) { groupReply(conversationId, convo, continued = true) }''')
rep(chat,
'''    private suspend fun groupReply(conversationId: Long, conversation: ConversationEntity) {''',
'''    private suspend fun groupReply(conversationId: Long, conversation: ConversationEntity, continued: Boolean = false) {''')
rep(chat,
'''        val latestText = trigger?.takeIf { it.role == "user" }?.content.orEmpty()
        val mentions = GroupChats.targeted(latestText, trigger?.mentionedCompanionIds, members)''',
'''        // "Continue" begins after the last TA, not after an old @user request.
        val latestAssistant = firstHistory.lastOrNull { it.role == "assistant" && it.error == null && it.content.isNotBlank() }
        if (continued && latestAssistant == null) return
        val latestText = if (continued) latestAssistant?.content.orEmpty()
            else trigger?.takeIf { it.role == "user" }?.content.orEmpty()
        val mentions = if (continued) emptyList() else GroupChats.targeted(latestText, trigger?.mentionedCompanionIds, members)''')
rep(chat,
'''        val patTarget = pat?.targetCompanionId''',
'''        val patTarget = if (continued) null else pat?.targetCompanionId''')
rep(chat,
'''            // Claim before sending network traffic. This is per-group, per-local-day, across restarts.
            if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) == 0) break
            val latestConversation''',
'''            val latestConversation''')
rep(chat,
'''                if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) == 0) return@start
                val history = Recap.sent(recaps.live(convo), settings.current().historySize)''',
'''                val history = Recap.sent(recaps.live(convo), settings.current().historySize)''')
rep(chat,
'''        var messages = prepare(build())
        while (true) {
            when (val step = step(conversationId, endpoint, messages, emptyList(), mayRefuse = thinking || withImages,''',
'''        var messages = prepare(build())
        while (true) {
            // Count actual outgoing attempts, not merely an "opportunity". Refusals and SKIP cost calls too.
            if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) == 0) return false
            val inputProxy = history.sumOf { it.content.length }.toLong() + extra.length
            db.conversations().recordGroupText(conversationId, inputProxy.coerceAtMost(200000), 0)
            when (val step = step(conversationId, endpoint, messages, emptyList(), mayRefuse = thinking || withImages,''')
rep(chat,
'''                    val body = step.text.trim()
                    if (body.isEmpty() || GroupChats.isSkip(body)) return false''',
'''                    val body = step.text.trim()
                    // Count visible response characters including SKIP, not hidden model reasoning.
                    db.conversations().recordGroupText(conversationId, 0, body.length.toLong())
                    if (body.isEmpty() || GroupChats.isSkip(body)) return false''')
# Make retrieval pick latest relevant memories before older records exhaust the context.
g=base+"ai/GroupChats.kt"
p = root / g
source = p.read_text(encoding="utf-8")
first = source.find("        val ordered = rows.distinctBy { it.id }")
last = source.find("        if (out.isEmpty()) return null", first)
if first < 0 or last < 0:
    raise SystemExit("Cannot locate original shared-context selection")
replacement = '''        val recent = rows.distinctBy { it.id }
            .sortedWith(compareByDescending<SharedMessageRow> { it.createdAt }.thenByDescending { it.id })
        val selected = ArrayList<String>()
        var size = 0
        for (r in recent) {
            val who = if (r.role == "user") me else names[r.senderCompanionId ?: r.ownerCompanionId] ?: "TA"
            val content = r.content.trim().replace(Regex("\\\\s+"), " ").take(260)
            if (content.isEmpty()) continue
            val title = r.conversationTitle.trim().ifEmpty { "聊天" }
            val line = "[$title] $who：$content\\n"
            if (size + line.length > SHARED_MAX_CHARS) continue
            selected += line
            size += line.length
        }
        val out = selected.asReversed().joinToString("")
'''
p.write_text(source[:first] + replacement + source[last:], encoding="utf-8")

print("v0.37.24 continuation, attempt-count ledger and recent-first shared memory applied")
