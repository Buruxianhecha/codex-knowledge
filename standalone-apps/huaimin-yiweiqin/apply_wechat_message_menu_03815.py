#!/usr/bin/env python3
"""0.38.15: transform the existing per-message DropdownMenu without losing its actions."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
chat = root / "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
source = chat.read_text(encoding="utf-8")
start_scope = source.index("private fun MessageBubble(")
start = source.index("                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {", start_scope)
end = source.index("\n            }\n            val error = message.error", start)
old = source[start:end]

required = (
    'ReactionPicker(', 'favoritesContainer.moments.favorite(', 'clipboard.setClipEntry(',
    'onRead(words)', 'onQuote()', 'onRetryVoice()', 'onRetry()', 'onEdit()',
    'onRecall()', 'onDelete()', 'MessageEdits.canEdit(message)',
    'Recalls.canRecall(message)', 'onReact(emoji)', 'Text("重新转文字")',
)
for marker in required:
    if marker not in old:
        raise RuntimeError(f"0.38.15 refuses to replace menu: action missing {marker}")
if old.count("DropdownMenuItem(") != 9:
    raise RuntimeError(f"Unexpected number of menu choices: {old.count('DropdownMenuItem(')}")

new = '''                // Same behavior as 0.38.14; compact icon grid replaces only the vertical menu.
                val canReactInMenu = reactEnabled && !mine && message.error == null
                val actions = buildList {
                    if (message.role in listOf("user", "assistant") &&
                        (words.isNotBlank() || audio != null)) {
                        add(MessageMenuAction("收藏", MessageMenuGlyph.FAVORITE) {
                            menu = false
                            scope.launch {
                                try {
                                    favoritesContainer.moments.favorite(
                                        message, if (mine) "我" else aiLabel ?: "TA")
                                    android.widget.Toast.makeText(favoritesUiContext,
                                        "已添加到收藏", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(favoritesUiContext,
                                        e.message ?: "收藏失败", android.widget.Toast.LENGTH_SHORT).show()
                                    android.util.Log.w("Favorites", "保存消息失败", e)
                                }
                            }
                        })
                    }
                    if (words.isNotBlank()) {
                        add(MessageMenuAction("复制", MessageMenuGlyph.COPY) {
                            menu = false
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", words))) }
                        })
                    }
                    if (readEnabled && !mine && message.error == null && audio == null && words.isNotBlank()) {
                        add(MessageMenuAction(
                            if (playingFile == readingMark(message.id)) "停止朗读" else "朗读",
                            MessageMenuGlyph.READ,
                        ) {
                            menu = false
                            onRead(words)
                        })
                    }
                    if (message.error == null && MessageQuotes.of(message) != null) {
                        add(MessageMenuAction("引用", MessageMenuGlyph.QUOTE) {
                            menu = false
                            onQuote()
                        })
                    }
                    if (audio != null && message.content.isBlank() && !transcribing) {
                        add(MessageMenuAction("重新转文字", MessageMenuGlyph.TRANSCRIBE) {
                            menu = false
                            onRetryVoice()
                        })
                    }
                    if (retryEnabled && !mine && canRetry) {
                        add(MessageMenuAction("重新回答", MessageMenuGlyph.RETRY) {
                            menu = false
                            onRetry()
                        })
                    }
                    if (editEnabled && MessageEdits.canEdit(message)) {
                        add(MessageMenuAction("编辑", MessageMenuGlyph.EDIT) {
                            menu = false
                            onEdit()
                        })
                    }
                    if (Recalls.canRecall(message)) {
                        add(MessageMenuAction("撤回", MessageMenuGlyph.RECALL) {
                            menu = false
                            onRecall()
                        })
                    }
                    add(MessageMenuAction("删除", MessageMenuGlyph.DELETE) {
                        menu = false
                        onDelete()
                    })
                }
                val reactionContent: (@Composable () -> Unit)? = if (canReactInMenu) {
                    {
                        ReactionPicker(on = remember(reactions) { reactions.map { it.emoji }.toSet() }) { emoji ->
                            menu = false
                            onReact(emoji)
                        }
                    }
                } else null
                val rootHeight = bubbleRootHeightPx.coerceAtLeast(1)
                val belowPx = (rootHeight - bubbleBottomPx).coerceAtLeast(0)
                val estimatedMenuHeight = with(LocalDensity.current) {
                    (if (canReactInMenu) 235.dp else 167.dp).roundToPx()
                }
                val preferMenuAbove = bubbleTopPx >= estimatedMenuHeight || bubbleTopPx > belowPx
                WechatMessagePopup(
                    expanded = menu,
                    mine = mine,
                    preferAbove = preferMenuAbove,
                    actions = actions,
                    onDismiss = { menu = false },
                    reactionContent = reactionContent,
                )'''
source = source[:start] + new + source[end:]

anchor = '    var menu by remember { mutableStateOf(false) }\n'
position = source.index(anchor, start_scope)
source = source[:position] + source[position:].replace(anchor, anchor + '''    var bubbleTopPx by remember { mutableIntStateOf(0) }
    var bubbleBottomPx by remember { mutableIntStateOf(0) }
    var bubbleRootHeightPx by remember { mutableIntStateOf(0) }
''', 1)

start_scope = source.index("private fun MessageBubble(")
box_anchor = '''            Box {
                Column(
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
'''
index = source.index(box_anchor, start_scope)
source = source[:index] + source[index:].replace(box_anchor, '''            Box(
                Modifier.onGloballyPositioned { coordinates ->
                    bubbleTopPx = coordinates.positionInRoot().y.toInt()
                    bubbleBottomPx = bubbleTopPx + coordinates.size.height
                    bubbleRootHeightPx = coordinates.findRootCoordinates().size.height
                },
            ) {
                Column(
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
''', 1)

for imp in (
    "import androidx.compose.ui.layout.onGloballyPositioned\n",
    "import androidx.compose.ui.layout.positionInRoot\n",
    "import androidx.compose.ui.layout.findRootCoordinates\n",
):
    if imp not in source:
        source = source.replace("import androidx.compose.ui.layout.onSizeChanged\n",
                                "import androidx.compose.ui.layout.onSizeChanged\n" + imp, 1)
chat.write_text(source, encoding="utf-8")

menu_file = root / "app/src/main/java/com/cleo/cleos/ui/chat/WechatMessageMenu.kt"
copyfile(here / "WechatMessageMenu_03815.kt", menu_file)

gradle = root / "app/build.gradle.kts"
build = gradle.read_text(encoding="utf-8")
for old_version, new_version in (
    ('versionName = "0.38.14"', 'versionName = "0.38.15"'),
    ('versionCode = 62080', 'versionCode = 62081'),
):
    if build.count(old_version) != 1:
        raise RuntimeError("Unexpected app version: " + old_version)
    build = build.replace(old_version, new_version, 1)
gradle.write_text(build, encoding="utf-8")
print("0.38.15/62081: anchored 3-column x 2-row action grid, preserved all 9 conditionally available actions and AI reactions")
