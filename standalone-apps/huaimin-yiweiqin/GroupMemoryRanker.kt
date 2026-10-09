package com.cleo.cleos.ai

import com.cleo.cleos.data.db.SharedMessageRow

/** Offline evidence retrieval from actual saved group messages.
 * Character bigrams make paraphrased Chinese questions more tolerant than
 * exact SQL instr(); the LLM then reasons over quoted actual evidence.
 * This is NOT a pretend embedding/semantic vector index.
 */
object GroupMemoryRanker {
    private val ignored = Regex("(上次|之前|以前|还记得|记得|说过|聊过|关于|我们|你们|群里|谁|怎么|为什么|是哪|什么|吗|呢|呀|啊|？|\\?|的|了)")
    private fun pieces(raw: String): Set<String> {
        val clean = raw.lowercase().replace(ignored, "")
            .replace(Regex("[\\s，。！？、：:,.;]+"), "")
        if (clean.length < 2) return emptySet()
        return clean.windowed(2, 1).toSet()
    }
    fun rank(question: String, rows: List<SharedMessageRow>, limit: Int = 12): List<SharedMessageRow> {
        val features=pieces(question)
        if (features.isEmpty()) return emptyList()
        val candidates=rows.distinctBy { it.id }.mapNotNull { row ->
            val message = row.content.trim()
            if (message.isEmpty()) return@mapNotNull null
            val seen=pieces(message)
            val matches=(features intersect seen).size
            val overlap=matches.toDouble() / features.size
            val bonus = if (message.contains(question.trim(), ignoreCase=true)) 1.0 else 0.0
            val score=overlap + bonus
            if (score <= 0.0) null else row to score
        }
        return candidates.sortedWith(compareByDescending<Pair<SharedMessageRow,Double>> { it.second }
            .thenByDescending { it.first.createdAt }).take(limit.coerceIn(1,24)).map { it.first }
    }
}
