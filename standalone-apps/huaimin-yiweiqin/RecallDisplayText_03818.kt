package com.cleo.cleos.data

/**
 * Recall wording only affects the visible status line. It never replaces the original
 * message content or the AI-facing recall event and must not influence recall eligibility.
 */
object RecallDisplayText {
    const val DEFAULT = "你撤回了一条消息"
    const val MAX_LENGTH = 80

    val presets = listOf(
        DEFAULT,
        "$DEFAULT，因为有错别字。",
        "$DEFAULT，并坏笑了一下。",
        "$DEFAULT，你猜猜撤回了什么。",
        "$DEFAULT，并祝你新春快乐 🐮",
        "$DEFAULT，保持高贵的沉默。",
        "$DEFAULT，试图萌混过关 😉",
        "$DEFAULT，并抱了你一下 ❤️",
    )

    /** One line only. Empty or invalid values safely fall back to the original marker. */
    fun clean(value: String): String {
        val flat = value.replace(Regex("[\\p{Cntrl}\\r\\n\\t]+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return flat.take(MAX_LENGTH).ifBlank { DEFAULT }
    }
}
