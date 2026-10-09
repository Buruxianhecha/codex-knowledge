#!/usr/bin/env python3
"""v0.37.37 automatically attach strong, source-grounded memories to relevant group turns."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
def change(path,old,new,why):
 p=root/path
 s=p.read_text(encoding="utf-8")
 n=s.count(old)
 if n!=1: raise RuntimeError(f"{why} expected 1 anchor got {n}")
 p.write_text(s.replace(old,new,1),encoding="utf-8")
chat="app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
rank="app/src/main/java/com/cleo/cleos/ai/GroupMemoryRanker.kt"

change(rank,
'''    fun rank(question: String, rows: List<SharedMessageRow>, limit: Int = 12): List<SharedMessageRow> {
''',
'''    /** Conservative automatic recall: long-enough question and >=60% 2-gram
     * overlap before surfacing older text that the user did not explicitly ask for.
     * A weak match is silently omitted, never fabricated.
     */
    fun related(question: String, rows: List<SharedMessageRow>): List<SharedMessageRow> {
        val features = pieces(question)
        if (features.size < 3) return emptyList()
        return rows.distinctBy { it.id }.mapNotNull { row ->
            val seen = pieces(row.content)
            val hits = (features intersect seen).size
            if (hits >= 3 && hits.toDouble() / features.size >= 0.60)
                row to hits else null
        }.sortedWith(compareByDescending<Pair<SharedMessageRow,Int>> { it.second }
            .thenByDescending { it.first.createdAt }).take(4).map { it.first }
    }

    fun rank(question: String, rows: List<SharedMessageRow>, limit: Int = 12): List<SharedMessageRow> {
''',"automatic memory contextual relevance")

change(chat,
'''        if (!GroupHistoryRecall.requested(userText)) return null
        val dao = db.messages()
        val candidates = mutableListOf<com.cleo.cleos.data.db.SharedMessageRow>()
''',
'''        val explicit = GroupHistoryRecall.requested(userText)
        if (!explicit && userText.trim().length < 6) return null
        val dao = db.messages()
        val candidates = mutableListOf<com.cleo.cleos.data.db.SharedMessageRow>()
''',"memory retrieval need not require explicit phrase")
change(chat,
'''        candidates += dao.groupHistoryRows(conversationId, null, 700, 0)
        val ranked = GroupMemoryRanker.rank(userText, candidates, 12)
''',
'''        candidates += dao.groupHistoryRows(conversationId, null, if (explicit) 700 else 250, 0)
        val ranked = if (explicit) GroupMemoryRanker.rank(userText, candidates, 12)
            else GroupMemoryRanker.related(userText, candidates)
''',"limit automatic recall to high-confidence related messages")
change(chat,
'''        var said = 0
        var toolExecutorReserved = false
''',
'''        // Compute shared evidence once per group turn, not repeatedly for all AI members.
        val groupRecall = if (!continued && trigger?.role == "user")
            groupHistoryContext(conversationId, latestText, s.userName) else null
        var said = 0
        var toolExecutorReserved = false
''',"cache one factual memory retrieval per turn")
change(chat,
'''                groupHistoryContext(conversationId, latestText, s.userName),
                worldContext(conversationId, latestText, s), groupLocation, groupWeather)
''',
'''                groupRecall,
                worldContext(conversationId, latestText, s), groupLocation, groupWeather)
''',"add memories only to relevant user-initiated turns")
print("0.37.37 automatic conservative original-memory recall added to group replies")
