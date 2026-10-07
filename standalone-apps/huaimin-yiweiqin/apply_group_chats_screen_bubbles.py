#!/usr/bin/env python3
from pathlib import Path
import shutil, sys

ROOT = Path(sys.argv[1]).resolve()
HERE = Path(__file__).resolve().parent

def rep(rel, old, new):
    p = ROOT / rel
    s = p.read_text(encoding='utf-8')
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{rel}: expected one match, got {n}: {old[:120]!r}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

def cp(src_name, rel):
    src = HERE / src_name
    dst = ROOT / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)

screen = 'app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt'
rep(screen,
'''    onRead: (String) -> Unit = {},
) {
    val palette = LocalGlassPalette.current''',
'''    onRead: (String) -> Unit = {},
    aiFace: Face? = null,
    aiLabel: String? = null,
    patEnabled: Boolean = true,
    editEnabled: Boolean = true,
    retryEnabled: Boolean = true,
    readEnabled: Boolean = true,
    reactEnabled: Boolean = true,
) {
    val palette = LocalGlassPalette.current''')
# Use per-speaker avatar.
rep(screen,
'''                Avatar(faces.ai.file, faces.ai.letter, AvatarSize, Modifier.pattable(ai = true))
                Spacer(Modifier.width(AvatarGap))''',
'''                val face = aiFace ?: faces.ai
                Avatar(face.file, face.letter, AvatarSize, if (patEnabled) Modifier.pattable(ai = true) else Modifier)
                Spacer(Modifier.width(AvatarGap))''')
# Speaker label above assistant body in group.
rep(screen,
'''                Column(
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (thought != null && !mine) ThoughtBlock(thought.text, thought.ms, key = message.id)''',
'''                Column(
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!mine && !aiLabel.isNullOrBlank()) Text(aiLabel, color = palette.contentSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                    if (thought != null && !mine) ThoughtBlock(thought.text, thought.ms, key = message.id)''')
# Add GroupCreateDialog before VoiceBubble section.
rep(screen,
'''/**
 * A voice message: its length, which it plays on a tap, and underneath, what it said once it
 * has been turned into text. A longer recording draws a longer bubble, the way chat apps do.
 */''',
'''@Composable
private fun GroupCreateDialog(
    companions: List<com.cleo.cleos.data.db.CompanionEntity>,
    currentId: Long,
    onCreate: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(companions, currentId) { mutableStateOf(setOf(currentId)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建群聊") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("选择至少两个角色。进入群聊后，他们能看到彼此在群里的发言，也能参考其他真实聊天记录。", fontSize = 13.sp)
                companions.take(GroupChats.MAX_MEMBERS).forEach { ta ->
                    val checked = ta.id in selected
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (checked) selected - ta.id else selected + ta.id
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { on -> selected = if (on) selected + ta.id else selected - ta.id })
                        Spacer(Modifier.width(8.dp))
                        Avatar(ta.avatar, ta.avatarEmoji ?: avatarLetter(ta.name, "TA"), 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(ta.name.ifBlank { "TA" })
                    }
                }
                if (companions.size > GroupChats.MAX_MEMBERS) Text("一次群聊最多 ${GroupChats.MAX_MEMBERS} 个角色。", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(selected) }, enabled = selected.size >= 2) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * A voice message: its length, which it plays on a tap, and underneath, what it said once it
 * has been turned into text. A longer recording draws a longer bubble, the way chat apps do.
 */''')
rep(screen,
'                                        patEnabled = !state.isGroup,\n                                        canRetry = row.isLast && !state.replying,',
'                                        patEnabled = !state.isGroup,\n                                        editEnabled = !state.isGroup,\n                                        retryEnabled = !state.isGroup,\n                                        readEnabled = !state.isGroup,\n                                        reactEnabled = !state.isGroup,\n                                        canRetry = row.isLast && !state.replying,')
rep(screen, 'if (!mine && message.error == null) {\n                        ReactionPicker', 'if (reactEnabled && !mine && message.error == null) {\n                        ReactionPicker')
rep(screen, 'if (!mine && message.error == null && audio == null && words.isNotBlank()) {', 'if (readEnabled && !mine && message.error == null && audio == null && words.isNotBlank()) {')
rep(screen, 'if (!mine && canRetry) {', 'if (retryEnabled && !mine && canRetry) {')
rep(screen, 'if (MessageEdits.canEdit(message)) {', 'if (editEnabled && MessageEdits.canEdit(message)) {')

print('apply_group_chats_screen_bubbles.py applied')
