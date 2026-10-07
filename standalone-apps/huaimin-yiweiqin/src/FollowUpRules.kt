package com.cleo.cleos.ai

/** A single opportunity after a normal reply, never after another proactive message. */
object FollowUpRules {
    val OPTIONS = listOf(30, 60, 180)
    const val GRACE_MS = 5 * 60_000L
    fun seconds(value: Int) = value.takeIf { it in OPTIONS } ?: 60
    fun eligible(enabled: Boolean, anchor: Long?, latest: Long?, due: Long?, now: Long, occupied: Boolean): Boolean =
        enabled && anchor != null && anchor == latest && due != null && now >= due && now - due <= GRACE_MS && !occupied

    val instruction = """
        【一次自然的补充机会，不是用户的新消息】
        你刚才已经回复过，对方暂时没有再发消息。结合你的性格和刚才真实的上下文，决定是否还有一句自然想补充的话，也可以让对话停在这里。
        没有自然想说的就只输出 SKIP，不要为了这个机会硬凑话题，不要重复刚才的回答。
        不要催回复、追问为什么不说话，不要把沉默当成拒绝，也不要编造对方的反应。
        对方说过忙、睡觉、再见或想安静时保持安静。
        有话时像平常聊天一样简短自然；如果真要分开发，最多 3 个气泡，用 send_message 真正分开发，不要靠空行伪装。
        不要提系统、定时器、补充机会或本提示。这只有一次机会，不要安排下一次补充。
    """.trimIndent()
}
