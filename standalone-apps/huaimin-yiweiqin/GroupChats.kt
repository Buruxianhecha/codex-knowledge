package com.cleo.cleos.ai

import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.MessageEntity
import com.cleo.cleos.data.db.SharedMessageRow

/** Rules and prompt shaping for conversations that contain several TAs. */
object GroupChats {
    const val MAX_MEMBERS = 6
    const val MAX_REPLIES = 3
    const val MAX_OPPORTUNITIES = 4
    const val MAX_BUBBLES = 2
    private const val SHARED_MAX_CHARS = 3200

    /** Matches a complete @ token, not a prefix of another person's name. */
    private fun containsMention(text: String, name: String): Boolean {
        if (name.isBlank()) return false
        for (prefix in listOf("@", "＠")) {
            val token = prefix + name
            var from = 0
            while (true) {
                val at = text.indexOf(token, from)
                if (at < 0) break
                val after = at + token.length
                if (after == text.length || text[after].isWhitespace() ||
                    text[after] in "，。！？、,.;:；：@＠)") return true
                from = at + 1
            }
        }
        return false
    }

    /** Legacy text-based mentions remain valid for messages typed without the picker. */
    fun mentioned(text: String, members: List<CompanionEntity>): List<CompanionEntity> {
        if (text.isBlank()) return emptyList()
        if (containsMention(text, "所有人")) return members
        return members.filter { ta -> containsMention(text, ta.name.trim()) }
    }

    /**
     * Picker selections are stored by immutable companion ID on each user message, so renaming
     * a character does not redirect an older @ to someone else with the same old display name.
     */
    fun targeted(text: String, storedIds: String?, members: List<CompanionEntity>): List<CompanionEntity> {
        if (containsMention(text, "所有人")) return members
        val ids = storedIds.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
        return if (ids.isNotEmpty()) members.filter { it.id in ids } else mentioned(text, members)
    }

    fun isSkip(text: String): Boolean = text.trim().let { it == "SKIP" || it.startsWith("SKIP:") || it.startsWith("SKIP：") }

    /**
     * The model must see another TA's words as somebody else's, not as its own assistant history.
     * Its own prior messages stay assistant turns so providers preserve the natural dialogue shape.
     */
    fun historyFor(
        history: List<MessageEntity>,
        selfId: Long,
        names: Map<Long, String>,
        fallbackSpeakerId: Long,
    ): List<MessageEntity> = history.map { m ->
        if (m.role != "assistant") return@map m
        val speakerId = m.senderCompanionId ?: fallbackSpeakerId
        if (speakerId == selfId) return@map m
        val who = names[speakerId].orEmpty().ifBlank { "另一位成员" }
        m.copy(
            role = "user",
            content = "（群聊里，$who 说：${m.content}）",
            error = null,
            toolCalls = null,
            reasoning = null,
            toolCallId = null,
            note = null,
            images = null,
            audio = null,
            thought = null,
            quote = null,
            proactive = false,
            reactions = null,
            call = null,
        )
    }

    fun turnInstruction(self: CompanionEntity, members: List<CompanionEntity>, targeted: Boolean): String {
        val selfName = self.name.trim().ifEmpty { "TA" }
        val names = members.joinToString("、") { it.name.trim().ifEmpty { "TA" } }
        val choice = if (targeted) {
            "对方刚刚明确点到了你（例如 @ 你，或在群里拍了拍你），所以正常回应；除非内容确实不需要回答，否则不要 SKIP。"
        } else {
            "这是一次自然发言机会。先判断此刻像真实群聊一样你有没有必要开口：没必要就严格只回复 SKIP，不要为了轮到你而硬说。"
        }
        return """
你正在一个真实的多人群聊里。群成员有：$names；你是$selfName。
你能看到群里其他角色刚刚说的话，那些话属于他们，不是你自己说过的。保持你自己的完整人格、记忆、态度和关系，不要替别的角色发言，也不要把别人的经历说成自己的。
$choice
如果开口，像真人群聊一样简短自然，可以赞同、补充、反驳、接梗或直接回应别人；不要每轮都总结全场。一次最多说两小句，需要分开发时最多两条真实消息。不要解释系统、调度、模型、发言机会或 SKIP 规则。
""".trim()
    }

    /** Compact, real excerpts from other chats. The current TA may use them when relevant. */
    fun sharedContext(rows: List<SharedMessageRow>, companions: List<CompanionEntity>, userName: String): String? {
        if (rows.isEmpty()) return null
        val names = companions.associate { it.id to it.name.trim().ifEmpty { "TA" } }
        val me = userName.trim().ifEmpty { "我" }
        val ordered = rows.distinctBy { it.id }.sortedWith(compareBy<SharedMessageRow> { it.createdAt }.thenBy { it.id })
        val out = StringBuilder()
        for (r in ordered) {
            val who = if (r.role == "user") me else names[r.senderCompanionId ?: r.ownerCompanionId] ?: "TA"
            val content = r.content.trim().replace(Regex("\\s+"), " ").take(260)
            if (content.isEmpty()) continue
            val title = r.conversationTitle.trim().ifEmpty { "聊天" }
            val line = "[$title] $who：$content\n"
            if (out.length + line.length > SHARED_MAX_CHARS) break
            out.append(line)
        }
        if (out.isEmpty()) return null
        return """
下面是这个 App 里其他真实聊天的摘录。不同 TA 不再彼此封闭；这些内容可以在当前话题确实相关时作为共同经历来参考。不要无缘无故复述隐私，不要编造这里没有的记录，也不要把另一位 TA 说过的话说成你自己说过的：
${out.toString().trimEnd()}
""".trim()
    }
}
