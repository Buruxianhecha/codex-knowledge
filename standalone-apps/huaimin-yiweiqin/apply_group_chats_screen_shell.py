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
# In a group, tapping a TA in the title menu opens that TA's real one-to-one chat instead of silently staying in the group.
rep(screen,
'''                        companions.forEach { ta ->
                            val here = ta.id == state.companionId
                            DropdownMenuItem(
                                text = { Text(ta.name.ifBlank { "TA" }, fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal) },
                                leadingIcon = { Avatar(ta.avatar, ta.avatarEmoji ?: avatarLetter(ta.name, "TA"), 28.dp) },
                                trailingIcon = if (here) ({ Icon(Icons.Rounded.Check, contentDescription = "正在聊") }) else null,
                                onClick = {
                                    switching = false
                                    if (!here) vm.switchTo(ta.id)
                                },
                            )
                        }''',
'''                        companions.forEach { ta ->
                            val here = ta.id == state.companionId
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.isGroup) "和${ta.name.ifBlank { "TA" }}单聊" else ta.name.ifBlank { "TA" },
                                        fontWeight = if (!state.isGroup && here) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = { Avatar(ta.avatar, ta.avatarEmoji ?: avatarLetter(ta.name, "TA"), 28.dp) },
                                trailingIcon = if (!state.isGroup && here) ({ Icon(Icons.Rounded.Check, contentDescription = "正在聊") }) else null,
                                onClick = {
                                    switching = false
                                    if (state.isGroup) vm.openSingle(ta.id) else if (!here) vm.switchTo(ta.id)
                                },
                            )
                        }''')

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


# Conversation list management: rename, pin/unpin, persistent up/down ordering, delete.
convs = 'app/src/main/java/com/cleo/cleos/ui/chat/ConversationsScreen.kt'
rep(convs,
'''import androidx.compose.material3.TextButton
''',
'''import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.room.withTransaction
''')
rep(convs,
'''    var confirm by remember { mutableStateOf<ConversationEntity?>(null) }
''',
'''    var actions by remember { mutableStateOf<ConversationEntity?>(null) }
    var confirm by remember { mutableStateOf<ConversationEntity?>(null) }
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun moveConversation(conv: ConversationEntity, offset: Int) {
        val section = conversations.filter { it.pinned == conv.pinned }
        val from = section.indexOfFirst { it.id == conv.id }
        val to = from + offset
        if (from < 0 || to !in section.indices) return
        val ordered = section.toMutableList().also { list ->
            val item = list.removeAt(from)
            list.add(to, item)
        }
        c.appScope.launch {
            val base = System.currentTimeMillis()
            c.db.withTransaction {
                ordered.forEachIndexed { index, item -> c.db.conversations().setManualRank(item.id, base - index) }
            }
        }
    }
''')
rep(convs,
'''                subtitle = "长按可以删除",''',
'''                subtitle = "长按可重命名、置顶和排序",''')
rep(convs,
'''                            onLongClick = { confirm = conv },''',
'''                            onLongClick = { actions = conv },''')
rep(convs,
'''                            Text(Dates.chatStamp(conv.updatedAt), color = palette.contentSecondary, fontSize = 12.sp)''',
'''                            Text(
                                (if (conv.pinned) "置顶 · " else "") + Dates.chatStamp(conv.updatedAt),
                                color = if (conv.pinned) palette.accentContent else palette.contentSecondary,
                                fontSize = 12.sp,
                            )''')
rep(convs,
'''    confirm?.let { conv ->
        AlertDialog(''',
'''    actions?.let { conv ->
        val section = conversations.filter { it.pinned == conv.pinned }
        val at = section.indexOfFirst { it.id == conv.id }
        AlertDialog(
            onDismissRequest = { actions = null },
            title = { Text("管理会话") },
            text = {
                Column {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            actions = null
                            renaming = conv
                            renameText = conv.title
                        },
                    ) { Text("重命名") }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            actions = null
                            c.appScope.launch {
                                c.db.conversations().setPinned(conv.id, !conv.pinned, System.currentTimeMillis())
                            }
                        },
                    ) { Text(if (conv.pinned) "取消置顶" else "置顶") }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = at > 0,
                        onClick = { actions = null; moveConversation(conv, -1) },
                    ) { Text("上移") }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = at >= 0 && at < section.lastIndex,
                        onClick = { actions = null; moveConversation(conv, 1) },
                    ) { Text("下移") }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { actions = null; confirm = conv },
                    ) { Text("删除", color = LocalGlassPalette.current.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { actions = null }) { Text("关闭") } },
        )
    }

    renaming?.let { conv ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(60) },
                    singleLine = true,
                    label = { Text("会话名称") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.trim().isNotEmpty(),
                    onClick = {
                        val title = renameText.trim()
                        renaming = null
                        c.appScope.launch { c.db.conversations().rename(conv.id, title) }
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
        )
    }

    confirm?.let { conv ->
        AlertDialog(''')

print('apply_group_chats_screen_shell.py applied')
