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
rep(screen, 'import com.cleo.cleos.ai.ChatRepository\n', 'import com.cleo.cleos.ai.ChatRepository\nimport com.cleo.cleos.ai.GroupChats\n')
# Checkbox import.
rep(screen, 'import androidx.compose.material3.AlertDialog\n', 'import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.Checkbox\n')
# Group creator state beside switching.
rep(screen,
'''    var switching by remember { mutableStateOf(false) }
    var readingRecap by remember { mutableStateOf(false) }''',
'''    var switching by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf(false) }
    var managingGroup by remember { mutableStateOf(false) }
    var readingRecap by remember { mutableStateOf(false) }''')
# Top bar title/subtitle/call behavior.
rep(screen,
'''                title = state.aiName.ifBlank { "聊天" },
                subtitle = state.model.takeIf { it.isNotBlank() }?.let { "$it ▾" },
                backdrop = page,
                leading = { GlassIconButton(Icons.Rounded.Forum, "对话记录", onOpenConversations, page) },
                trailing = {
                    GlassIconButton(Icons.Rounded.Call, "打电话", { startCall() }, page)
                    GlassIconButton(Icons.Rounded.AddComment, "新对话", vm::newConversation, page)
                },''',
'''                title = state.aiName.ifBlank { "聊天" },
                subtitle = state.model.takeIf { it.isNotBlank() }?.let { "$it ▾" },
                backdrop = page,
                leading = { GlassIconButton(Icons.Rounded.Forum, "对话记录", onOpenConversations, page) },
                trailing = {
                    if (!state.isGroup) GlassIconButton(Icons.Rounded.Call, "打电话", { startCall() }, page)
                    GlassIconButton(Icons.Rounded.AddComment, if (state.isGroup) "新单聊" else "新对话", vm::newConversation, page)
                },''')
# Add create group menu item before add TA.
rep(screen,
'''                        DropdownMenuItem(
                            text = { Text("添加一个 TA") },''',
'''                        if (state.isGroup) {
                            DropdownMenuItem(
                                text = { Text("管理群成员") },
                                leadingIcon = { Icon(Icons.Rounded.Forum, contentDescription = null) },
                                onClick = {
                                    switching = false
                                    managingGroup = true
                                },
                            )
                        }
                        if (companions.size >= 2) {
                            DropdownMenuItem(
                                text = { Text(if (state.isGroup) "创建另一个群聊" else "创建群聊") },
                                leadingIcon = { Icon(Icons.Rounded.Forum, contentDescription = null) },
                                onClick = {
                                    switching = false
                                    creatingGroup = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("添加一个 TA") },''')
# Pass per-speaker face/name to bubble.
rep(screen,
'''                                    MessageBubble(
                                        message = m,
                                        showFace = row.showFace,''',
'''                                    val speaker = if (state.isGroup && m.role == "assistant") {
                                        state.groupSpeakers.firstOrNull { it.id == (m.senderCompanionId ?: state.companionId) }
                                    } else null
                                    MessageBubble(
                                        message = m,
                                        showFace = row.showFace,
                                        aiFace = speaker?.let { Face(it.avatar, it.avatarEmoji ?: avatarLetter(it.name, "TA")) },
                                        aiLabel = speaker?.name?.trim()?.ifEmpty { "TA" },
                                        patEnabled = !state.isGroup,''')
# Empty group hint.
rep(screen,
'''                    if (state.aiName.isBlank()) "说点什么吧" else "和${state.aiName}说点什么吧",''',
'''                    if (state.isGroup) "和大家说点什么吧" else if (state.aiName.isBlank()) "说点什么吧" else "和${state.aiName}说点什么吧",''')
# Add dialog before edit dialog.
rep(screen,
'''    vm.editingMessage?.let { original ->''',
'''    if (creatingGroup) {
        GroupCreateDialog(
            companions = companions,
            currentId = state.companionId,
            onCreate = {
                vm.createGroup(it)
                creatingGroup = false
            },
            onDismiss = { creatingGroup = false },
        )
    }
    if (managingGroup) {
        GroupMembersDialog(
            companions = companions,
            initial = state.groupMembers.map { it.id }.toSet(),
            onSave = {
                vm.updateGroupMembers(it)
                managingGroup = false
            },
            onDismiss = { managingGroup = false },
        )
    }

    vm.editingMessage?.let { original ->''')

print('apply_group_chats_screen_shell.py applied')
