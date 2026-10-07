package com.cleo.cleos.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One run of pats (拍一拍): who was patted, how many times in a row, and the words it was said
 * with. Kept whole in the row, so a line already in the chat doesn't change when the verb or the
 * ending is changed later.
 */
@Serializable
data class PatRecord(
    val who: String,
    val count: Int = 1,
    val verb: String = Pats.VERB,
    val suffix: String = "",
    /** In a group: the exact TA the person patted. Null keeps old single-chat rows compatible. */
    val targetCompanionId: Long? = null,
    /** In a group: which TA patted the person back. Null keeps old rows compatible. */
    val sourceCompanionId: Long? = null,
)

/**
 * 拍一拍: a double tap on an avatar leaves a line in the chat. Patting the TA is an interaction
 * of its own and gets a reply after a short quiet moment; quick repeated pats are merged into one
 * counted line and one reply. Patting oneself stays quiet until the next real message.
 */
object Pats {
    /** Who was patted: the TA, or the person themself. */
    const val AI = "ai"
    const val ME = "me"

    /** The TA patting the person back: a line of its own, which it chose to leave (the pat_user tool). */
    const val FROM_AI = "from_ai"

    /** Patted this many in a row, the TA answers with a word or two, once the person has stopped. */
    const val HEAVY_AT = 10

    const val VERB = "拍"

    /** What the verb can be, one character each: 拍了拍, 戳了戳, 摸了摸… */
    val VERBS = listOf("拍", "戳", "摸", "抱", "揉")

    /** A pat this soon after the last one is one more on the same line. */
    const val STREAK_MS = 4_000L

    /** The most that can follow the TA's name ("的小脑袋"). */
    const val SUFFIX_MAX = 12

    /** From this many in a row, the line says the TA has had enough. */
    private const val DIZZY_AT = 8

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(r: PatRecord): String = json.encodeToString(r)

    fun decode(raw: String?): PatRecord? =
        if (raw.isNullOrBlank()) null else runCatching { json.decodeFromString<PatRecord>(raw) }.getOrNull()

    fun cleanSuffix(s: String): String = s.trim().replace('\n', ' ').take(SUFFIX_MAX)

    /** The verb's first character as a person reads it (a whole code point); 拍 while there is none. */
    fun cleanVerb(s: String): String {
        val t = s.trim()
        return if (t.isEmpty()) VERB else String(Character.toChars(t.codePointAt(0)))
    }

    /**
     * The pat to record now: one more on [prev] when it is the same side's and still warm ([prevAt]
     * is when it was last patted), otherwise a first one.
     */
    fun again(
        prev: PatRecord?,
        prevAt: Long,
        who: String,
        verb: String,
        suffix: String,
        now: Long,
        targetCompanionId: Long? = null,
        sourceCompanionId: Long? = null,
    ): PatRecord =
        if (
            prev != null &&
            prev.who == who &&
            prev.targetCompanionId == targetCompanionId &&
            prev.sourceCompanionId == sourceCompanionId &&
            now - prevAt <= STREAK_MS
        ) {
            prev.copy(count = prev.count + 1, verb = verb, suffix = suffix)
        } else {
            PatRecord(who, 1, verb, suffix, targetCompanionId, sourceCompanionId)
        }

    private fun once(v: String) = "${v}了$v"

    /** Whether the person patted the TA so many times in a row that it answers (see HEAVY_AT). */
    fun heavy(r: PatRecord): Boolean = r.who == AI && r.count >= HEAVY_AT

    /** The line the chat shows. */
    fun line(r: PatRecord, aiName: String): String {
        if (r.who == FROM_AI) return "“${aiName.ifBlank { "TA" }}”${once(r.verb)}我${r.suffix}"
        val to = if (r.who == ME) "自己" else "“${aiName.ifBlank { "TA" }}”${r.suffix}"
        val body = if (r.count <= 1) "我${once(r.verb)}$to" else "我连${r.verb}了$to ${r.count} 下"
        return if (r.who == AI && r.count >= DIZZY_AT) "$body（别拍啦，要晕了）" else body
    }

    /** What the TA is told about the interaction. */
    fun forModel(r: PatRecord): String {
        val to = if (r.who == ME) "自己" else "你${r.suffix}"
        if (heavy(r)) return "（对方连${r.verb}了$to ${r.count} 下，拍个不停。自然回一两句就好）"
        if (r.who == AI && r.count <= 1) return "（对方${once(r.verb)}$to。请自然回应这次互动，简短一点也可以）"
        if (r.who == AI) return "（对方连${r.verb}了$to ${r.count} 下。请自然回应这次互动）"
        return if (r.count <= 1) "（对方${once(r.verb)}$to）" else "（对方连${r.verb}了$to ${r.count} 下）"
    }
}
