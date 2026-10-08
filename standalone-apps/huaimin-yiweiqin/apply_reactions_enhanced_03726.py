#!/usr/bin/env python3
"""Enhance v0.37.26 reactions after the basic picker: validate all emoji,
show real actor identity (only actual user reactions), save recent choices, preserve AI events.
No pretend companion reactions or destructive DB changes.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
prefix="app/src/main/java/com/cleo/cleos/"

def update(rel, before, after, expected=1):
    path=root/rel
    source=path.read_text(encoding="utf-8")
    count=source.count(before)
    if count!=expected: raise SystemExit(f"{rel}: needed {expected} anchors but got {count}: {before[:120]!r}")
    path.write_text(source.replace(before,after,expected),encoding="utf-8")

# Critical: the original ReactionEvents whitelist only knew seven emojis.
# Without this, the expanded UI silently discards a tap on every newly added emoji.
update(prefix+"data/ReactionEvents.kt",
       "emoji !in MessageReactions.OFFERED", "emoji !in MessageReactions.ALL")
update(prefix+"data/ReactionEvents.kt",
       "it.emoji in MessageReactions.OFFERED", "it.emoji in MessageReactions.ALL")
update("app/src/test/java/com/cleo/cleos/data/ReactionEventsTest.kt",
       "for (emoji in MessageReactions.OFFERED)", "for (emoji in MessageReactions.ALL)")
update("app/src/test/java/com/cleo/cleos/data/ReactionEventsTest.kt",
       '''    @Test fun additionCreatesItsOwnCurrentTurnWithTargetContext() {''',
       '''    @Test fun newlyExpandedEmojiIsPersistedAndDeliveredAsModelEvent() {
        val addition = ReactionEvents.change(said(), "🌹", 251)
        assertNotNull(addition)
        assertEquals("🌹", MessageReactions.decode(addition!!.reactions).single().emoji)
        val encoded = requireNotNull(addition.event).content
        assertEquals("🌹", ReactionEvents.decode(encoded)?.emoji)
        val removal = ReactionEvents.change(said().copy(reactions = addition.reactions), "🌹", 252)
        assertNull(removal?.event)
        assertNull(removal?.reactions)
    }

    @Test fun additionCreatesItsOwnCurrentTurnWithTargetContext() {''')

# Recent emoji are locally remembered; all operations stay on UI click callbacks.
# Note: no claim that preference history is in the full chat backup.
ui=prefix+"ui/chat/Stickers.kt"
update(ui, '''    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.width(272.dp).padding(horizontal = 4.dp, vertical = 2.dp)) {''',
'''    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("huaimin_recent_reactions", android.content.Context.MODE_PRIVATE) }
    var recent by remember(prefs) {
        mutableStateOf(prefs.getString("recent", "").orEmpty().split(",")
            .filter { it in MessageReactions.ALL }.distinct().take(8))
    }
    fun select(emoji: String) {
        recent = (listOf(emoji) + recent.filterNot { it == emoji }).take(8)
        prefs.edit().putString("recent", recent.joinToString(",")).apply()
        onPick(emoji)
    }
    Column(Modifier.width(272.dp).padding(horizontal = 4.dp, vertical = 2.dp)) {''')
update(ui, ".clickable { onPick(emoji) }", ".clickable { select(emoji) }", expected=2)
update(ui, '''        if (expanded) {
            Text("点选回应 · 再点取消",''',
'''        if (expanded) {
            if (recent.isNotEmpty()) {
                Text("最近使用", fontSize = 11.sp, color = palette.contentSecondary,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    recent.take(6).forEach { emoji ->
                        Box(Modifier.size(40.dp).clip(CircleShape)
                            .background(if (emoji in on) palette.accent.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { select(emoji) }, contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 21.sp)
                        }
                    }
                }
            }
            Text("点选回应 · 再点取消",''')

# The existing encoded MessageReaction has no actor ID: all currently stored reactions
# are performed by the app user. Display that real identity, not fabricated AI reactors.
update(ui, 'import com.cleo.cleos.ui.common.appContainer',
       'import com.cleo.cleos.ui.common.appContainer\nimport com.cleo.cleos.ui.common.Avatar\nimport androidx.compose.foundation.layout.FlowRow')
update(ui, '''fun ReactionChips(reactions: List<MessageReaction>, onClick: () -> Unit) {
    val palette = LocalGlassPalette.current
    GlassSurface(
        modifier = Modifier.clickable(interactionSource = null, indication = null, onClick = onClick),
        style = palette.notice,
        shape = GlassShape.Capsule,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(reactions.joinToString(" ") { it.emoji }, fontSize = 15.sp)
    }
}''',
'''fun ReactionChips(
    reactions: List<MessageReaction>,
    avatar: String?,
    letter: String,
    userName: String,
    onRemove: (String) -> Unit,
) {
    val palette = LocalGlassPalette.current
    var selected by remember { mutableStateOf<String?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        reactions.distinctBy { it.emoji }.forEach { reaction ->
            GlassSurface(
                modifier = Modifier.clickable { selected = reaction.emoji },
                style = palette.notice,
                shape = GlassShape.Capsule,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(reaction.emoji, fontSize = 17.sp)
                    Avatar(avatar, letter, 20.dp)
                }
            }
        }
    }
    selected?.let { emoji ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("回应详情") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(avatar, letter, 28.dp)
                    Text("${userName.ifBlank { "我" }}  ${emoji}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null; onRemove(emoji) }) { Text("取消这个回应") }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text("关闭") }
            },
        )
    }
}''')
screen=prefix+"ui/chat/ChatScreen.kt"
update(screen, '''    onRead: (String) -> Unit = {},
    aiFace: Face? = null,''',
'''    onRead: (String) -> Unit = {},
    reactionAvatar: String? = null,
    reactionLetter: String = "我",
    reactionUserName: String = "我",
    aiFace: Face? = null,''')
update(screen, 'if (reactions.isNotEmpty()) ReactionChips(reactions) { menu = true }',
       'if (reactions.isNotEmpty()) ReactionChips(reactions, reactionAvatar, reactionLetter, reactionUserName, onReact)')
update(screen, '''                                        onReact = { vm.react(m.id, it) },''',
'''                                        onReact = { vm.react(m.id, it) },
                                        reactionAvatar = state.userAvatar,
                                        reactionLetter = avatarLetter(state.userName, "我"),
                                        reactionUserName = state.userName,''')
# Upgrade package version only after all existing patches have run.
update("app/build.gradle.kts", 'versionName = "0.37.25"', 'versionName = "0.37.26"')
update("app/build.gradle.kts", "versionCode = 62047", "versionCode = 62048")
print("v0.37.26: all emojis validated end-to-end; recent reactions + truthful avatar chips; version 62048")
