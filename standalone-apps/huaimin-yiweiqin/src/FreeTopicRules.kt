package com.cleo.cleos.ai

import java.time.ZonedDateTime

/** Frequency controls opportunities to think, not a quota of messages to send. */
object FreeTopicRules {
    const val IDLE_MS = 15 * 60_000L
    const val PERSONALITY = -1
    data class Level(val id: Int, val label: String, val minMinutes: Int, val maxMinutes: Int, val dailyMax: Int)

    /** Follow-personality is deliberately broad: the model/persona decides whether an opportunity becomes a message. */
    val PERSONALITY_LEVEL = Level(PERSONALITY, "跟随性格", 20, 240, 24)
    val LEVELS = listOf(
        PERSONALITY_LEVEL,
        Level(0, "偶尔", 240, 360, 3),
        Level(1, "自然", 30, 60, 24),
        Level(2, "比较主动", 10, 20, 60),
    )
    fun level(id: Int) = LEVELS.firstOrNull { it.id == id } ?: PERSONALITY_LEVEL
    fun intervalText(level: Level) = if (level.id == PERSONALITY) "20 分钟–4 小时" else if (level.minMinutes % 60 == 0 && level.maxMinutes % 60 == 0)
        "${level.minMinutes / 60}–${level.maxMinutes / 60} 小时" else "${level.minMinutes}–${level.maxMinutes} 分钟"
    fun minute(value: Int, fallback: Int) = value.takeIf { it in 0..1439 } ?: fallback
    fun time(value: Int) = "%02d:%02d".format(value / 60, value % 60)

    fun quiet(now: ZonedDateTime, on: Boolean, start: Int, end: Int): Boolean {
        if (!on) return false
        val m = now.hour * 60 + now.minute
        return if (start == end) true else if (start < end) m >= start && m < end else m >= start || m < end
    }

    fun outsideQuiet(at: ZonedDateTime, on: Boolean, start: Int, end: Int): ZonedDateTime {
        if (!quiet(at, on, start, end)) return at
        if (start == end) return at.plusDays(1)
        var resume = at.withHour(end / 60).withMinute(end % 60).withSecond(0).withNano(0)
        if (!resume.isAfter(at)) resume = resume.plusDays(1)
        return resume
    }

    fun next(now: ZonedDateTime, level: Level, on: Boolean, start: Int, end: Int, fraction: Double): Long {
        val minutes = (level.minMinutes + (level.maxMinutes - level.minMinutes) * fraction.coerceIn(0.0, 1.0)).toLong()
        return outsideQuiet(now.plusMinutes(minutes), on, start, end).toInstant().toEpochMilli()
    }

    fun held(enabled: Boolean, quiet: Boolean, occupied: Boolean, lastActivity: Long?, now: Long,
             unanswered: Int, attemptsToday: Int, maximum: Int): String? = when {
        !enabled -> "自由找话题已关闭"
        quiet -> "免打扰时段，先保持安静"
        occupied -> "正在聊天或输入，先不打断"
        lastActivity == null -> "还没有聊过，等第一次对话后再来"
        now - lastActivity < IDLE_MS -> "刚刚聊过，先留一点空闲"
        unanswered >= LaterRules.UNANSWERED_MAX -> "前面主动说的还没回，先等你回来"
        attemptsToday >= maximum -> "今天的考虑次数已经用完"
        else -> null
    }

    val instruction = """
        （这是一次你自己决定要不要主动开口的机会，不是对方发来的消息，对方看不到这段提示。）
        结合你的完整性格、已经知道的记忆、你们共同经历、最近聊天和最近你主动说过的内容，判断此刻有没有自然想说的话。
        “要不要主动”本身也是你性格的一部分：高冷、慢热、克制的人可以更常保持安静；活泼、外向或亲近的人可以更容易开口，但不要为了占满机会硬找话题。
        可以分享一个真正在意的想法、问一个有内容的问题、自然接起旧话题，或想起对方之前提过但还没有后续的事。没有自然内容就只回复 SKIP，保持安静完全正常。
        不要因为对方没回而催促、埋怨、委屈、撒娇施压或索要关注；不要说“很久没理我了”“怎么不回我”“在干嘛怎么不说话”。
        对方说过要忙、睡觉、考试、开会、开车或想安静时，尊重那个状态；已经聊过、已经解决或最近主动提过的内容不要重复。
        不要编造自己刚刚在现实中做了什么、看到了什么新闻或对方正在做什么；未知的事不要当事实。没有真实工具结果就不要假装刚看过现实世界的信息。
        想说就像平常聊天一样说，简短自然。需要分开说时最多 3 个短气泡，优先真正调用 send_message 一条一条发；绝对不要用换行、空行或大段空白假装成多个气泡。
        不要提系统、定时器、考虑机会、主动机制、SKIP 或这段提示，也不要给自己预约下一次例行找话题。
    """.trimIndent()
}
