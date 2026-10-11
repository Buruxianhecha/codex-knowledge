package com.cleo.cleos.data

/**
 * The complete thirteen built-in choices from the reference recall picker.
 * Only the visible text is customized; the real withdrawn content and AI event are untouched.
 */
object RecallDisplayText {
    const val DEFAULT = "你撤回了一条消息"
    const val MAX_LENGTH = 80

    // Keep the exact reference order, including the plain/default "无" choice in second place.
    val presets = listOf(
        "$DEFAULT，因为有错别字。",
        DEFAULT,
        "$DEFAULT，并坏笑了一下。",
        "$DEFAULT，你猜猜撤回了什么。",
        "$DEFAULT，并祝你新春快乐 🐮",
        "$DEFAULT，你品，你细品~",
        "$DEFAULT，并抱了你一下 ❤️",
        "$DEFAULT，保持高贵的沉默。",
        "$DEFAULT，试图萌混过关 😉",
        "$DEFAULT，并假装无事发生。",
        "$DEFAULT，并对你施了遗忘咒。",
        "$DEFAULT，就是辣么神秘 😎",
        "$DEFAULT，并祝你节日快乐",
    )

    /** A single safe line, never more than eighty characters. */
    fun clean(value: String): String {
        val flat = value.replace(Regex("[\\p{Cntrl}\\r\\n\\t]+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return flat.take(MAX_LENGTH).ifBlank { DEFAULT }
    }
}
