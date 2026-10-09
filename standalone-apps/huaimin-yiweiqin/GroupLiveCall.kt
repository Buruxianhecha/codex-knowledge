package com.cleo.cleos.ai

import com.cleo.cleos.data.db.CompanionEntity

/** One human microphone and multiple AI voices in a live, interruptible call. */
object GroupLiveCall {
    const val MAX_SPOKEN_CHARS = 320
    fun select(rotated: List<CompanionEntity>, mentioned: List<CompanionEntity>,
               maxReplies: Int, mode: Int, first: Boolean): List<CompanionEntity> {
        val candidates = when {
            mentioned.isNotEmpty() -> mentioned.filter { m -> rotated.any { it.id == m.id } }
            mode == 2 && !first -> emptyList()
            else -> rotated
        }
        return candidates.take(maxReplies.coerceIn(1, 6).coerceAtMost(3))
    }

    fun instruction(self: String, names: List<String>, extra: String?, targeted: Boolean): String =
        "这是正在进行的真人多成员语音群电话，电话里有" + names.joinToString("、") +
            "。你是$self，只能代表自己发言，不得代替别人回答。其他角色会用自己的语音实际出声。" +
            "轮到你时先判断是否有必要接话；没有必要仅输出 SKIP。要说则只说一两句口语化的短话（最多320字），" +
            "不要说“作为AI”、不要解释群聊调度，不要用Markdown。" +
            (if (targeted) "对方明确叫了你的名字，请优先回应。" else "") +
            (extra?.let { "当前通话提醒：$it" } ?: "")
}
