package com.cleo.cleos.ai

import com.cleo.cleos.data.Companions
import com.cleo.cleos.data.db.AppDatabase

/** The AI may choose and save its own display name during natural conversation. */
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

        // Intention is understood from the whole natural conversation by the AI.
        // Do not re-parse only the final user bubble with rigid keywords or
        // expire a chat topic after 10 minutes: “那你换你喜欢的吧” is valid in context.
        // The tool still validates speaker identity and persists only their name.

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
