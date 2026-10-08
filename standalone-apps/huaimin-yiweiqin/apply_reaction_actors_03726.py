#!/usr/bin/env python3
"""Group emoji reactions: real companion IDs, no fabricated participants, no Room migration.
Backward-compatible with pre-0.37.26 MessageReaction(emoji, at) backups.
"""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
base="app/src/main/java/com/cleo/cleos/"

def update(rel,before,after,expected=1):
    p=root/rel
    s=p.read_text(encoding="utf-8")
    count=s.count(before)
    if count != expected:
        raise SystemExit(f"{rel}: expected {expected} anchors but got {count}: {before[:120]!r}")
    p.write_text(s.replace(before,after,expected),encoding="utf-8")

for name,target in [
    ("GroupReactionRules.kt",base+"data/GroupReactionRules.kt"),
    ("GroupReactionRulesTest.kt","app/src/test/java/com/cleo/cleos/data/GroupReactionRulesTest.kt"),
]:
    dest=root/target
    dest.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(here/"src"/name,dest)

data=base+"data/MessageReactions.kt"
update(data, "data class MessageReaction(val emoji: String, val at: Long)",
       "data class MessageReaction(val emoji: String, val at: Long, val actorCompanionId: Long? = null)")
update(data,
'''if (list.any { it.emoji == emoji }) list.filterNot { it.emoji == emoji } else list + MessageReaction(emoji, at)''',
'''if (list.any { it.emoji == emoji && it.actorCompanionId == null })
            list.filterNot { it.emoji == emoji && it.actorCompanionId == null }
        else list + MessageReaction(emoji, at)''')

events=base+"data/ReactionEvents.kt"
update(events, "val removed = before.any { it.emoji == emoji }",
       "val removed = before.any { it.emoji == emoji && it.actorCompanionId == null }")
update(base+"ai/Prompt.kt",
'''MessageReactions.decode(m.reactions).filterNot { Triple(m.id, it.emoji, it.at) in covered }''',
'''MessageReactions.decode(m.reactions).filter { it.actorCompanionId == null }
                .filterNot { Triple(m.id, it.emoji, it.at) in covered }''')

group=base+"ai/GroupChats.kt"
update(group,
'''不要解释系统、调度、模型、发言机会或 SKIP 规则。''',
'''不要解释系统、调度、模型、发言机会或 SKIP 规则。
如果你只是想用一个表情轻轻回应群里刚刚那位成员的最新一条消息，而不想发送文字，
可以严格只输出 <react:❤️> 这样的格式（尖括号中的表情可换成普通 Emoji）。
不要在这个标记前后加任何解释、其它文字或 Markdown；也不要频繁使用这一选项。
这是真正的表情回应，App 会把它附在原消息下面，而不是发成文字气泡。''')

chat=base+"ai/ChatRepository.kt"
update(chat,
'''                    if (body.isEmpty() || GroupChats.isSkip(body)) return false
                    storeAssistantBubbles(''',
'''                    if (body.isEmpty() || GroupChats.isSkip(body)) return false
                    val groupEmoji = GroupReactionRules.parse(body)
                    if (groupEmoji != null) {
                        val target = GroupReactionRules.target(history, ta.id) ?: return false
                        val saved = db.withTransaction {
                            val current = db.messages().get(target.id) ?: return@withTransaction false
                            if (current.conversationId != conversationId || current.role !in setOf("user", "assistant") ||
                                current.error != null || current.content.isBlank()) return@withTransaction false
                            val old = MessageReactions.decode(current.reactions)
                            val updated = GroupReactionRules.add(old, groupEmoji, ta.id, stamp())
                            if (old == updated) return@withTransaction false
                            db.messages().setReactions(current.id, MessageReactions.encode(updated))
                            true
                        }
                        return saved
                    }
                    storeAssistantBubbles(''')
update(chat,
'''import com.cleo.cleos.data.ReactionEvents''',
'''import com.cleo.cleos.data.ReactionEvents
import com.cleo.cleos.data.GroupReactionRules''')
# Human presses do not impersonate companion actions; companion reactions require real AI output.
# A human can react to an AI message even when a companion reacted with the same Emoji.
# The UI resolves profiles from the actual group role list.
ui=base+"ui/chat/Stickers.kt"
p=root/ui
s=p.read_text(encoding="utf-8")
start=s.index("fun ReactionChips(")
annotation=s.rfind("@Composable",0,start)
end=s.index("/** Compact quick reactions",start)
replacement='''data class ReactionPerson(val name: String, val avatar: String?, val letter: String)

/** Per-emoji chips, up to three real reactor avatars and full detail on tap. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReactionChips(
    reactions: List<MessageReaction>,
    avatar: String?,
    letter: String,
    userName: String,
    speakers: Map<Long, ReactionPerson>,
    onRemove: (String) -> Unit,
) {
    val palette = LocalGlassPalette.current
    var selected by remember { mutableStateOf<String?>(null) }
    fun person(actor: Long?): ReactionPerson = if (actor == null)
        ReactionPerson(userName.ifBlank { "我" }, avatar, letter)
    else speakers[actor] ?: ReactionPerson("已离开的角色", null, "TA")
    val groups = reactions.groupBy { it.emoji }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { (emoji, actors) ->
            GlassSurface(
                modifier = Modifier.combinedClickable(
                    onClick = { selected = emoji },
                    onLongClick = {
                        if (actors.any { it.actorCompanionId == null }) onRemove(emoji)
                        else selected = emoji
                    },
                ),
                style = palette.notice,
                shape = GlassShape.Capsule,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(emoji, fontSize = 17.sp)
                    actors.distinctBy { it.actorCompanionId }.take(3).forEach { r ->
                        val who = person(r.actorCompanionId)
                        Avatar(who.avatar, who.letter, 19.dp)
                    }
                    val remaining = actors.distinctBy { it.actorCompanionId }.size - 3
                    if (remaining > 0) Text("+" + remaining, fontSize = 11.sp,
                        color = palette.contentSecondary)
                }
            }
        }
    }
    selected?.let { emoji ->
        val actors = groups[emoji].orEmpty().distinctBy { it.actorCompanionId }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("回应详情 " + emoji) },
            text = {
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(
                    androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actors.forEach { r ->
                        val who = person(r.actorCompanionId)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Avatar(who.avatar, who.letter, 28.dp)
                            Text(who.name)
                        }
                    }
                }
            },
            confirmButton = {
                if (actors.any { it.actorCompanionId == null }) {
                    TextButton(onClick = { selected = null; onRemove(emoji) }) { Text("取消我的回应") }
                } else TextButton(onClick = { selected = null }) { Text("关闭") }
            },
            dismissButton = {
                if (actors.any { it.actorCompanionId == null })
                    TextButton(onClick = { selected = null }) { Text("关闭") }
            },
        )
    }
}

'''
if not s[annotation:start].strip()=="@Composable":raise SystemExit("ReactionChips annotation mismatch")
p.write_text(s[:annotation]+replacement+s[end:],encoding="utf-8")

screen=base+"ui/chat/ChatScreen.kt"
update(screen,
'''    reactionUserName: String = "我",
    aiFace: Face? = null,''',
'''    reactionUserName: String = "我",
    reactionSpeakers: Map<Long, ReactionPerson> = emptyMap(),
    aiFace: Face? = null,''')
update(screen,
'''ReactionChips(reactions, reactionAvatar, reactionLetter, reactionUserName, onReact)''',
'''ReactionChips(reactions, reactionAvatar, reactionLetter, reactionUserName, reactionSpeakers, onReact)''')
update(screen,
'''                                        reactionUserName = state.userName,''',
'''                                        reactionUserName = state.userName,
                                        reactionSpeakers = state.groupSpeakers.associate {
                                            it.id to ReactionPerson(it.name, it.avatar,
                                                it.avatarEmoji ?: avatarLetter(it.name, "TA"))
                                        },''')
print("Group AI emoji actions, real actor identities, grouped chips and details integrated; Room 22 unchanged")
