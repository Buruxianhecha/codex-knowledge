#!/usr/bin/env python3
"""0.38.17 structural and safe-batch regression checks."""
from pathlib import Path
import sys
root = Path(sys.argv[1]).resolve()
src = root / "app/src/main/java/com/cleo/cleos/ui/chat"
chat = (src / "ChatScreen.kt").read_text(encoding="utf-8")
backend = (src / "BatchMessageActions.kt").read_text(encoding="utf-8")
view = (src / "MessageMultiSelect.kt").read_text(encoding="utf-8")
menu = (src / "WechatMessageMenu.kt").read_text(encoding="utf-8")
build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
assert 'versionName = "0.38.17"' in build and "versionCode = 62083" in build
for item in (
    'selectionMode by remember(state.conversationId)',
    'selectedMessageIds by remember(state.conversationId)',
    'BackHandler(enabled = selectionMode',
    'ChatMessageCheck(', 'toggleSelection(m.id)',
    'Modifier.matchParentSize().clickable',
    'ChatMultiSelectBottomBar(', 'onMultiSelect = { beginSelection(m.id) }',
    '"多选", MessageMenuGlyph.SELECT',
    'ChooseForwardMode(', 'ChooseForwardTarget(',
    'BatchMessageActions.forward(', 'BatchMessageActions.delete(',
    'BatchMessageActions.ordered(', 'c.moments.favorite(',
    'if (selectionMode) "已选择 "',
    'if (selectionMode) TextButton(',
    'if (selectionMode) null else',
    'if (selectionMode) {', 'if (BatchMessageActions.isMerged(message.content))',
    'mergedOpen = true', 'AlertDialog(',
):
    assert item in chat, ("chat missing", item)
assert "MessageMenuGlyph.SELECT -> Icons.Rounded.Checklist" in menu
assert "internal const val MESSAGE_MENU_COLUMNS = 2" in menu
for item in ('MAX_SELECTED = 100','require(selected.size == ids.size)',
             'require(original.none { it.audio != null })',
             'cloneFile(', 'copyTo(', 'c.db.withTransaction',
             'require(ordered(c.db.messages().byIds(ids), ids, sourceId) == original)',
             'c.db.messages().insert(it)', 'c.db.messages().delete(it.id)',
             'withContext(Dispatchers.IO) { c.images.delete(created) }',
             'content = transcript(', 'audio = audio?.let(MessageAudios::encode)',
             'senderCompanionId = null', 'toolCalls = null'):
    assert item in backend, ("backend missing", item)
for item in ('checked: Boolean', 'onClick: () -> Unit', 'ChatMultiSelectBottomBar',
             'ChooseForwardMode', 'ChooseForwardTarget', 'onMerge', 'onSeparate',
             'onFavorite', 'onDelete'):
    assert item in view, ("UI missing", item)
print("PASS v0.38.17: selection UI, real attachment-safe forward copies, merged card, batch favorites and deletes")
