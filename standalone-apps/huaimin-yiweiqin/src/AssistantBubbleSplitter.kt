package com.cleo.cleos.ai

/**
 * Fallback for models that visually fake multiple chat messages by returning one response with
 * blank lines between paragraphs instead of calling send_message several times.
 *
 * Only real paragraph breaks are split. Blank lines inside fenced Markdown code stay inside the
 * same bubble. The caller supplies the normal per-turn bubble limit so a long Markdown answer
 * never floods the chat or loses text.
 */
object AssistantBubbleSplitter {
    fun split(text: String, limit: Int): List<String> {
        require(limit >= 1)
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) return emptyList()

        val paragraphs = ArrayList<String>()
        val current = StringBuilder()
        var fence: String? = null

        fun flush() {
            val part = current.toString().trim()
            if (part.isNotEmpty()) paragraphs += part
            current.setLength(0)
        }

        normalized.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            val marker = when {
                trimmed.startsWith("```") -> "```"
                trimmed.startsWith("~~~") -> "~~~"
                else -> null
            }

            if (line.isBlank() && fence == null) {
                flush()
                return@forEach
            }

            if (current.isNotEmpty()) current.append('\n')
            current.append(line)

            if (marker != null) {
                fence = if (fence == marker) null else if (fence == null) marker else fence
            }
        }
        flush()

        if (paragraphs.size <= 1) return listOf(normalized)
        if (paragraphs.size <= limit) return paragraphs

        return buildList {
            addAll(paragraphs.take(limit - 1))
            add(paragraphs.drop(limit - 1).joinToString("\n\n"))
        }
    }
}
