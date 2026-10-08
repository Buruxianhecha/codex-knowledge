package com.cleo.cleos.data

import com.cleo.cleos.data.db.MessageEntity
import org.junit.Assert.*
import org.junit.Test

class GroupReactionRulesTest {
    private fun row(id: Long, role: String, actor: Long? = null) =
        MessageEntity(id = id, conversationId = 5, role = role,
            content = "这条是真实消息", createdAt = id, senderCompanionId = actor)

    @Test fun onlyExactWhitelistedReactionTokensAreAccepted() {
        assertEquals("🌹", GroupReactionRules.parse("<react:🌹>"))
        assertEquals("😂", GroupReactionRules.parse("  <react:😂> "))
        assertNull(GroupReactionRules.parse("对你 <react:🌹>"))
        assertNull(GroupReactionRules.parse("<react:假的>"))
        assertNull(GroupReactionRules.parse("<react:🌹><react:😂>"))
        assertNull(GroupReactionRules.parse("<react:🌹> 再说一句"))
    }

    @Test fun targetsLatestOtherActorWithoutReplyingToOwnMessage() {
        val rows = listOf(row(1,"user"),row(2,"assistant",7),row(3,"assistant",8),row(4,"assistant",7))
        assertEquals(3L, GroupReactionRules.target(rows, 7)!!.id)
        assertEquals(4L, GroupReactionRules.target(rows, 8)!!.id)
        assertNull(GroupReactionRules.target(listOf(row(4,"assistant",7)),7))
    }

    @Test fun sameEmojiMergesSeparateRealActorsAndDoesNotClobberUser() {
        val user = MessageReaction("🌹", 10)
        val a = GroupReactionRules.add(listOf(user), "🌹", 7, 20)
        val b = GroupReactionRules.add(a, "🌹", 8, 30)
        assertEquals(3,b.size)
        assertEquals(listOf(null,7L,8L),b.map { it.actorCompanionId })
        assertEquals(b,GroupReactionRules.add(b,"🌹",7,40))
        val untoggled = MessageReactions.toggle(b,"🌹",50)
        assertEquals(listOf(7L,8L),untoggled.map { it.actorCompanionId })
    }

    @Test fun oldSerializedReactionsRemainUserReactionsWithoutSchemaMigration() {
        val previous = """[{"emoji":"❤️","at":123}]"""
        val decoded = MessageReactions.decode(previous)
        assertEquals(1,decoded.size)
        assertNull(decoded.single().actorCompanionId)
        val joined = GroupReactionRules.add(decoded,"❤️",7,200)
        assertEquals(2, MessageReactions.decode(MessageReactions.encode(joined)).size)
        assertEquals(listOf(null,7L),
            MessageReactions.decode(MessageReactions.encode(joined)).map { it.actorCompanionId })
    }
}
