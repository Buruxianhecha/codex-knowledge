#!/usr/bin/env python3
"""0.38.17: add WeChat-like chat selection, batch operations and forwarded record card."""
from pathlib import Path
from shutil import copyfile
import sys
root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
src = root / "app/src/main/java/com/cleo/cleos/ui/chat"
chatpath = src / "ChatScreen.kt"
chat = chatpath.read_text(encoding="utf-8")

def rep(old, new, what):
    global chat
    found = chat.count(old)
    if found != 1: raise RuntimeError(what + ": " + str(found) + " anchors")
    chat = chat.replace(old, new, 1)

anchor = '    val focusManager = LocalFocusManager.current\n    BackHandler(enabled = (drawerOpen || plusOpen) && pageShown)'
replacement = '''    val focusManager = LocalFocusManager.current
    var selectionMode by remember(state.conversationId) { mutableStateOf(false) }
    var selectedMessageIds by remember(state.conversationId) { mutableStateOf(setOf<Long>()) }
    var batchBusy by remember { mutableStateOf(false) }
    var askingDeleteBatch by remember { mutableStateOf(false) }
    var choosingForwardMode by remember { mutableStateOf(false) }
    var choosingForwardTarget by remember { mutableStateOf(false) }
    var mergeForward by remember { mutableStateOf(false) }
    var forwardConversations by remember { mutableStateOf(emptyList<com.cleo.cleos.data.db.ConversationEntity>()) }
    fun leaveSelection() {
        selectedMessageIds = emptySet()
        selectionMode = false
        askingDeleteBatch = false
        choosingForwardMode = false
        choosingForwardTarget = false
    }
    fun toggleSelection(id: Long) {
        selectedMessageIds = if (id in selectedMessageIds) selectedMessageIds - id
        else if (selectedMessageIds.size < BatchMessageActions.MAX_SELECTED) selectedMessageIds + id
        else {
            voiceHint = "每次最多选择100条消息"
            selectedMessageIds
        }
    }
    fun beginSelection(id: Long) {
        selectionMode = true
        selectedMessageIds = setOf(id)
        drawerOpen = false
        plusOpen = false
        keyboard?.hide()
        focusManager.clearFocus()
    }
    LaunchedEffect(state.conversationId, state.messages) {
        if (selectionMode) {
            val valid = state.messages.filter(BatchMessageActions::selectable).mapTo(HashSet()) { it.id }
            selectedMessageIds = selectedMessageIds.intersect(valid)
        }
    }
    LaunchedEffect(choosingForwardTarget) {
        if (choosingForwardTarget) forwardConversations =
            c.db.conversations().all().sortedByDescending { it.updatedAt }
    }
    BackHandler(enabled = selectionMode && pageShown) { leaveSelection() }
    BackHandler(enabled = (drawerOpen || plusOpen) && pageShown)'''
rep(anchor, replacement, "selection lifecycle and back")
rep('                title = if (state.isGroup) ', '                title = if (selectionMode) "已选择 " + selectedMessageIds.size + " 条消息" else if (state.isGroup) ', "selection title")
rep('                subtitle = state.model.takeIf { it.isNotBlank() }?.let { "$it ▾" },',
    '                subtitle = if (selectionMode) null else state.model.takeIf { it.isNotBlank() }?.let { "$it ▾" },', "subtitle")
rep('                leading = { GlassIconButton(Icons.Rounded.Forum, "对话记录", onOpenConversations, page) },',
    '                leading = { if (selectionMode) TextButton(onClick = { leaveSelection() }) { Text("取消") } else GlassIconButton(Icons.Rounded.Forum, "对话记录", onOpenConversations, page) },', "cancel top")
rep('                onTitleClick = { switching = true },',
    '                onTitleClick = { if (!selectionMode) switching = true },', "disable switch")
rep('                visible = barShown,', '                visible = barShown && !selectionMode,', "music overlay")

rep('            ChatInputBar(\n                backdrop = page,\n',
'''            if (selectionMode) {
                ChatMultiSelectBottomBar(
                    backdrop = page, bottomPadding = inputBottom,
                    count = selectedMessageIds.size, busy = batchBusy,
                    onForward = { if (selectedMessageIds.isNotEmpty()) choosingForwardMode = true },
                    onFavorite = {
                        if (selectedMessageIds.isNotEmpty() && !batchBusy) {
                            batchBusy = true
                            val sourceId = state.conversationId
                            val chosen = selectedMessageIds.toSet()
                            val known = state.groupSpeakers.associate { it.id to it.name }
                            val aiName = state.aiName
                            scope.launch {
                                var done = 0
                                try {
                                    require(sourceId != null) { "当前会话无效" }
                                    val rows = BatchMessageActions.ordered(
                                        c.db.messages().byIds(chosen), chosen, sourceId)
                                    for (m in rows) {
                                        val name = if (m.role == "user") "我"
                                            else known[m.senderCompanionId] ?: aiName.ifBlank { "TA" }
                                        c.moments.favorite(m, name)
                                        done++
                                    }
                                    voiceHint = "已收藏 " + done + " 条消息"
                                    leaveSelection()
                                } catch (e: Exception) {
                                    voiceHint = "已收藏" + done + "条；" + (e.message ?: "部分收藏失败")
                                } finally { batchBusy = false }
                            }
                        }
                    },
                    onDelete = { if (selectedMessageIds.isNotEmpty()) askingDeleteBatch = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = inputBottom)
                        .onSizeChanged { inputHeight = it.height },
                )
            } else {
            ChatInputBar(
                backdrop = page,
''', "toolbar replaces input")
rep('''                    .onSizeChanged { inputHeight = it.height },
            )
            if (recording || voiceHint != null)''',
'''                    .onSizeChanged { inputHeight = it.height },
            )
            }
            if (recording || voiceHint != null)''', "close toolbar else")

start = chat.index('                                    MessageBubble(\n                                        message = m,')
chat = chat[:start] + '''                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        if (selectionMode) ChatMessageCheck(
                                            checked = m.id in selectedMessageIds,
                                            onClick = { toggleSelection(m.id) },
                                        )
                                        Box(Modifier.weight(1f)) {
''' + chat[start:]
start = chat.index('                                    MessageBubble(\n                                        message = m,')
end = chat.index('\n                                    )', start) + len('\n                                    )')
chat = chat[:end] + '''
                                            if (selectionMode) Box(
                                                Modifier.matchParentSize().clickable {
                                                    toggleSelection(m.id)
                                                },
                                            )
                                        }
                                    }''' + chat[end:]
rep('''                                    MessageBubble(
                                        message = m,''',
'''                                    MessageBubble(
                                        message = m,
                                        onMultiSelect = { beginSelection(m.id) },''', "wire menu callback")
rep('    onDelete: () -> Unit,\n    onRecall: () -> Unit = {},',
    '    onDelete: () -> Unit,\n    onMultiSelect: () -> Unit = {},\n    onRecall: () -> Unit = {},', "bubble callback")
rep('''                val actions = buildList {
                    if (message.role in listOf("user", "assistant")''',
'''                val actions = buildList {
                    if (BatchMessageActions.selectable(message)) {
                        add(MessageMenuAction("多选", MessageMenuGlyph.SELECT) {
                            menu = false
                            onMultiSelect()
                        })
                    }
                    if (message.role in listOf("user", "assistant")''', "menu entry")
rep('    var menu by remember { mutableStateOf(false) }\n',
    '    var menu by remember { mutableStateOf(false) }\n    var mergedOpen by remember { mutableStateOf(false) }\n', "merged dialog state")
rep('''                    } else {
                        pieces.forEach { piece ->
                            when (piece) {''',
'''                    } else if (BatchMessageActions.isMerged(message.content)) {
                        BubbleSurface(
                            mine = mine, companionId = aiLabelId ?: LocalBubbleCompanion.current,
                            modifier = Modifier.widthIn(max = bubbleMaxWidth()).combinedClickable(
                                onClick = { mergedOpen = true }, onLongClick = { menu = true },
                            ),
                        ) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text("聊天记录", fontWeight = FontWeight.SemiBold,
                                    color = bubbleTextColor(mine, aiLabelId ?: LocalBubbleCompanion.current))
                                Text(message.content.lineSequence().drop(1).firstOrNull().orEmpty(),
                                    color = bubbleTextColor(mine, aiLabelId ?: LocalBubbleCompanion.current))
                                Text("点击查看完整记录", fontSize = 12.sp, color = palette.contentSecondary)
                            }
                        }
                    } else {
                        pieces.forEach { piece ->
                            when (piece) {''', "merged record clickable")
rep('''                val canReactInMenu = reactEnabled && !mine && message.error == null''',
'''                if (mergedOpen) {
                    AlertDialog(
                        onDismissRequest = { mergedOpen = false },
                        title = { Text("合并转发聊天记录") },
                        text = {
                            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                Text(message.content.removePrefix(BatchMessageActions.MERGED_PREFIX))
                            }
                        },
                        confirmButton = { TextButton(onClick = { mergedOpen = false }) { Text("关闭") } },
                    )
                }
                val canReactInMenu = reactEnabled && !mine && message.error == null''', "merged detail")

rep('    walletAction?.let { kind ->\n', '''    if (choosingForwardMode) {
        ChooseForwardMode(
            onSeparate = { mergeForward = false; choosingForwardMode = false; choosingForwardTarget = true },
            onMerge = { mergeForward = true; choosingForwardMode = false; choosingForwardTarget = true },
            onDismiss = { choosingForwardMode = false },
        )
    }
    if (choosingForwardTarget) {
        ChooseForwardTarget(
            candidates = forwardConversations,
            sourceId = state.conversationId ?: -1L,
            count = selectedMessageIds.size,
            onTarget = { target ->
                if (!batchBusy) {
                    choosingForwardTarget = false
                    batchBusy = true
                    val sourceId = state.conversationId
                    val chosen = selectedMessageIds.toSet()
                    val merged = mergeForward
                    val names = state.groupSpeakers.associate { it.id to it.name } +
                        (state.companionId to state.aiName)
                    val title = state.aiName.ifBlank { "聊天" }
                    scope.launch {
                        try {
                            require(sourceId != null) { "会话不存在" }
                            val n = BatchMessageActions.forward(c, sourceId, target, chosen, merged, names, title)
                            voiceHint = "已转发 " + n + " 条消息"
                            leaveSelection()
                        } catch (e: Exception) {
                            voiceHint = e.message ?: "转发失败"
                            choosingForwardTarget = true
                        } finally { batchBusy = false }
                    }
                }
            },
            onDismiss = { choosingForwardTarget = false },
        )
    }
    if (askingDeleteBatch) {
        AlertDialog(
            onDismissRequest = { if (!batchBusy) askingDeleteBatch = false },
            title = { Text("删除 " + selectedMessageIds.size + " 条消息？") },
            text = { Text("仅删除当前会话的选中消息，删除后无法恢复。") },
            confirmButton = {
                TextButton(enabled = !batchBusy, onClick = {
                    batchBusy = true
                    val sourceId = state.conversationId
                    val chosen = selectedMessageIds.toSet()
                    askingDeleteBatch = false
                    scope.launch {
                        try {
                            require(sourceId != null) { "会话不存在" }
                            val n = BatchMessageActions.delete(c, sourceId, chosen)
                            voiceHint = "已删除 " + n + " 条消息"
                            leaveSelection()
                        } catch (e: Exception) {
                            voiceHint = e.message ?: "删除失败"
                        } finally { batchBusy = false }
                    }
                }) { Text("删除", color = palette.error) }
            },
            dismissButton = { TextButton(onClick = { askingDeleteBatch = false }) { Text("取消") } },
        )
    }

    walletAction?.let { kind ->
''', "batch dialogs")

chatpath.write_text(chat, encoding="utf-8")
menupath = src / "WechatMessageMenu.kt"
menu = menupath.read_text(encoding="utf-8")
for old, new in [
    ("import androidx.compose.material.icons.rounded.Delete\n",
     "import androidx.compose.material.icons.rounded.Delete\nimport androidx.compose.material.icons.rounded.Checklist\n"),
    ("RECALL, DELETE }", "RECALL, DELETE, SELECT }"),
    ("    MessageMenuGlyph.DELETE -> Icons.Rounded.Delete\n",
     "    MessageMenuGlyph.DELETE -> Icons.Rounded.Delete\n    MessageMenuGlyph.SELECT -> Icons.Rounded.Checklist\n"),
]:
    if menu.count(old) != 1: raise RuntimeError("unexpected menu anchor " + old)
    menu = menu.replace(old, new, 1)
menupath.write_text(menu, encoding="utf-8")
for name in ("BatchMessageActions", "MessageMultiSelect"):
    copyfile(here / (name + "_03817.kt"), src / (name + ".kt"))
buildpath = root / "app/build.gradle.kts"
build = buildpath.read_text(encoding="utf-8")
for old, new in [('versionName = "0.38.16"', 'versionName = "0.38.17"'),
                 ('versionCode = 62082', 'versionCode = 62083')]:
    if build.count(old) != 1: raise RuntimeError("wrong build " + old)
    build = build.replace(old, new, 1)
buildpath.write_text(build, encoding="utf-8")
print("0.38.17/62083: multi-select, separate/merged forwarding, favorite and confirmed deletion integrated")
