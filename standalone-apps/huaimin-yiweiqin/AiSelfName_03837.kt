package com.cleo.cleos.ai

import com.cleo.cleos.data.Companions
import com.cleo.cleos.data.db.AppDatabase

/** Only the AI currently replying may edit its own persisted companion name. */
interface SelfName {
    suspend fun rename(conversationId: Long, companionId: Long, newName: String): String
}

/** Pure, testable policy for a self-chosen display name. */
internal object SelfNameRules {
    fun cleanName(raw: String): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty() || name.codePointCount(0, name.length) > 14) return null
        if (name.any {
                Character.isISOControl(it) || it == '\u200B' || it == '\u200C' ||
                    it == '\u200D' || it == '\u202E' || it == '\u202D'
            }) return null
        return name
    }

    /** An ability question is NOT permission to perform a profile write. */
    fun explicitlyAsked(userText: String): Boolean {
        val s = userText.replace(Regex("\\s+"), "")
        if (s.endsWith("吗") || s.endsWith("么") || s.endsWith("?") ||
            s.endsWith("？")) return false
        val referringToSelf = listOf("你自己", "给自己", "为自己", "自己选", "自己挑",
            "你来", "你选", "你挑", "你喜欢", "你决定", "名字由你").any(s::contains)
        val mentionsNameChange = listOf("改名", "换名", "起名", "取名", "名字",
            "昵称").any(s::contains)
        val requestedAction = listOf("改", "换", "取", "起", "选", "挑", "定",
            "决定").any(s::contains)
        return referringToSelf && mentionsNameChange && requestedAction
    }
}

/** Device-side implementation. No API argument can select or rename another character. */
class AiSelfName(private val db: AppDatabase, private val companions: Companions) : SelfName {
    override suspend fun rename(conversationId: Long, companionId: Long, newName: String): String {
        val name = SelfNameRules.cleanName(newName)
            ?: throw ToolFailure("新名字不能为空、包含控制字符或超过14个字符，请重新选一个简短名字。", "名字不合适")
        val conversation = db.conversations().get(conversationId)
            ?: throw ToolFailure("当前聊天已不存在，不能修改名字。", "找不到当前会话")
        val owned = if (conversation.isGroup)
            db.groupMembers().idsFor(conversationId).contains(companionId)
        else conversation.companionId == companionId
        if (!owned || companionId <= 0L)
            throw ToolFailure("只能修改当前发言角色自己的名字。", "不能修改其他角色")

        val recent = db.messages().newest(conversationId, 40)
        val latestUser = recent.firstOrNull {
            it.role == "user" && it.note == null && it.error == null
        } ?: throw ToolFailure("需要对方在当前聊天里明确同意你自己改名。", "本轮没有改名授权")
        if (System.currentTimeMillis() - latestUser.createdAt !in 0L..600_000L ||
            !SelfNameRules.explicitlyAsked(latestUser.content))
            throw ToolFailure("对方还没有在这一轮明确要求你自行选择名字并改名。不要仅口头声称已修改。", "尚未明确授权")

        // One successful rename per user request, even if the model repeats the tool call.
        if (recent.any {
                it.role == "note" && it.createdAt >= latestUser.createdAt &&
                    it.note?.startsWith("AI自主改名成功：") == true
            }) throw ToolFailure("本次改名已经成功，不必再反复修改。", "本轮已经改过")

        val mine = companions.get(companionId)
            ?: throw ToolFailure("你的角色资料不存在。", "找不到你的角色")
        if (mine.name == name) return "你现在已经叫「$name」，名字没有变化。"
        if (companions.ensure().any {
                it.id != companionId && it.name.equals(name, ignoreCase = true)
            }) throw ToolFailure("这个名字已经被另一位 AI 角色使用，请自己另选一个。", "与其他角色重名")

        // Existing Companions.update serializes edits and Room emits new values to
        // every observing screen; ID, avatar, persona and all history remain unchanged.
        companions.update(companionId) { it.copy(name = name) }
        val saved = companions.get(companionId)
        if (saved?.name != name)
            throw ToolFailure("名字没有保存成功，请稍后重试。", "保存失败")
        return "改名成功。你已经在应用中叫「$name」，通讯录与聊天会同步更新。可以告诉对方你为什么选这个名字。"
    }
}
