package com.cleo.cleos.ui.chat

/** Dictation drafts never send automatically; preserve anything already typed. */
internal object VoiceDictationDraft {
    fun append(existing: String, recognized: String): String {
        val speech = recognized.trim()
        if (speech.isEmpty()) return existing
        if (existing.isEmpty()) return speech
        val space = if (existing.last().isWhitespace() ||
            existing.last().code > 127 || speech.first().code > 127) "" else " "
        return existing + space + speech
    }
}
