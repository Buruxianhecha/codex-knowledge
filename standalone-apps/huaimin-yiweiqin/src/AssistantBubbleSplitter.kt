package com.cleo.cleos.ai

/** Conversational sentences become messages; structured Markdown stays intact. */
object AssistantBubbleSplitter {
    private val structured = Regex("^(?:#{1,6}\\s|[-*+]\\s|\\d+[.)]\\s|>|\\|)")
    private val requested = Regex("(?:分(?:成)?|拆(?:成)?|发)\\s*([2-8二两三四五六七八])\\s*(?:条(?:消息)?|次(?:连续)?(?:发|发送|说)|句|个(?:气泡|消息))")
    private val url = Regex("(?:https?://|www\\.)[^\\s。！？；，<>]+")

    fun requestedCount(text: String?): Int? {
        val value = text?.let { requested.find(it.takeLast(1000)) }?.groupValues?.get(1) ?: return null
        return value.toIntOrNull() ?: mapOf("二" to 2, "两" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "七" to 7, "八" to 8)[value]
    }

    fun split(text: String, limit: Int, desired: Int? = null): List<String> {
        require(limit >= 1)
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()
        val parts = ArrayList<String>()
        val block = StringBuilder()
        var fence: Char? = null
        var fenceSize = 0
        fun flush(protected: Boolean = false) {
            val words = block.toString().trim()
            if (words.isNotEmpty()) {
                if (protected || words.lineSequence().any { structured.containsMatchIn(it.trimStart()) }) parts += words
                else words.lineSequence().filter { it.isNotBlank() }.forEach { parts += sentences(it.trim()) }
            }
            block.setLength(0)
        }
        normalized.lineSequence().forEach { line ->
            val start = line.trimStart()
            val marker = start.firstOrNull()?.takeIf { it == '`' || it == '~' }
            val size = if (marker == null) 0 else start.takeWhile { it == marker }.length
            if (fence != null) {
                if (block.isNotEmpty()) block.append('\n')
                block.append(line)
                if (marker == fence && size >= fenceSize && start.drop(size).isBlank()) {
                    fence = null
                    flush(protected = true)
                }
            } else if (size >= 3) {
                flush()
                fence = marker
                fenceSize = size
                block.append(line)
            } else if (line.isBlank()) flush()
            else {
                if (block.isNotEmpty()) block.append('\n')
                block.append(line)
            }
        }
        flush(protected = fence != null)
        val target = desired?.coerceIn(1, limit)
        if (target != null) {
            // Respect "分三次发" using existing clause boundaries only, never arbitrary character cuts.
            while (parts.size < target) {
                val candidate = parts.indices.mapNotNull { i -> clauseCut(parts[i])?.let { Triple(i, it, parts[i].length) } }
                    .maxByOrNull { it.third } ?: break
                val words = parts.removeAt(candidate.first)
                parts.add(candidate.first, words.substring(candidate.second).trim())
                parts.add(candidate.first, words.substring(0, candidate.second).trim())
            }
            if (parts.size > target) {
                val merged = ArrayList<String>()
                var at = 0
                repeat(target) { group ->
                    val take = (parts.size - at + target - group - 1) / (target - group)
                    merged += parts.subList(at, at + take).joinToString("\n")
                    at += take
                }
                parts.clear()
                parts.addAll(merged)
            }
        }
        if (parts.size <= limit) return parts
        return parts.take(limit - 1) + parts.drop(limit - 1).joinToString("\n\n")
    }

    private fun sentences(text: String): List<String> {
        val out = ArrayList<String>()
        var from = 0
        val protected = protectedPositions(text)
        var i = 0
        while (i < text.length) {
            val char = text[i]
            val stop = !protected[i] && (char in "。！？!?；;" ||
                (char == '.' && (i + 1 == text.length || text[i + 1].isWhitespace()) && !abbreviation(text, i)))
            if (stop) {
                var end = i + 1
                while (end < text.length && text[end] in "。！？!?；;”’\"）)]】」』") end++
                while (end < text.length) {
                    val cp = Character.codePointAt(text, end)
                    if (Character.getType(cp) != Character.OTHER_SYMBOL.toInt() && cp !in 0xFE00..0xFE0F && cp != 0x200D && cp !in 0x1F3FB..0x1F3FF) break
                    end += Character.charCount(cp)
                }
                text.substring(from, end).trim().takeIf { it.isNotEmpty() }?.let(out::add)
                from = end
                i = end
            } else i++
        }
        text.substring(from).trim().takeIf { it.isNotEmpty() }?.let(out::add)
        return out
    }

    private fun abbreviation(text: String, dot: Int): Boolean {
        val original = text.substring(0, dot + 1).takeLastWhile { it.isLetter() || it == '.' }
        return original.lowercase() in setOf("mr.", "mrs.", "ms.", "dr.", "prof.", "e.g.", "i.e.", "vs.") ||
            (original.length == 2 && original[0].isUpperCase())
    }

    private fun clauseCut(text: String): Int? {
        if (text.contains('\n') || text.startsWith("```") || text.startsWith("~~~") || structured.containsMatchIn(text)) return null
        val protected = protectedPositions(text)
        return text.indices.filter { i -> text[i] in "，," && !protected[i] && i >= 5 && text.length - i >= 6 }
            .minByOrNull { kotlin.math.abs(it * 2 - text.length) }?.plus(1)
    }

    private fun protectedPositions(text: String): BooleanArray {
        val result = BooleanArray(text.length)
        url.findAll(text).forEach { m -> m.range.forEach { result[it] = true } }
        val closes = ArrayList<Char>()
        var code = false
        text.forEachIndexed { i, c ->
            val wasInside = code || closes.isNotEmpty()
            when {
                c == '`' -> code = !code
                code -> Unit
                closes.lastOrNull() == c -> closes.removeAt(closes.lastIndex)
                c == '“' -> closes += '”'
                c == '「' -> closes += '」'
                c == '『' -> closes += '』'
                c == '(' -> closes += ')'
                c == '（' -> closes += '）'
                c == '[' -> closes += ']'
                c == '【' -> closes += '】'
                c == '"' -> closes += '"'
            }
            if (wasInside || code || closes.isNotEmpty()) result[i] = true
        }
        return result
    }
}
