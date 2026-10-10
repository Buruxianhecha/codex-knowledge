#!/usr/bin/env python3
"""0.38.15 structural regression: all existing actions and callbacks remain reachable."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
chat = (root / "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt").read_text(encoding="utf-8")
popup = (root / "app/src/main/java/com/cleo/cleos/ui/chat/WechatMessageMenu.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
block = chat[chat.index("private fun MessageBubble("):chat.index("private fun VoiceBubble(")]
assert 'versionName = "0.38.15"' in gradle and "versionCode = 62081" in gradle
assert "DropdownMenu(expanded = menu" not in block
assert "WechatMessagePopup(" in block
assert 'Modifier.onGloballyPositioned' in block
for code in (
    '"收藏", MessageMenuGlyph.FAVORITE', '"复制", MessageMenuGlyph.COPY',
    'MessageMenuGlyph.READ', '"引用", MessageMenuGlyph.QUOTE',
    '"重新转文字", MessageMenuGlyph.TRANSCRIBE',
    '"重新回答", MessageMenuGlyph.RETRY', '"编辑", MessageMenuGlyph.EDIT',
    '"撤回", MessageMenuGlyph.RECALL', '"删除", MessageMenuGlyph.DELETE',
    'favoritesContainer.moments.favorite(', 'clipboard.setClipEntry(',
    'onRead(words)', 'onQuote()', 'onRetryVoice()', 'onRetry()',
    'onEdit()', 'onRecall()', 'onDelete()', 'onReact(emoji)',
    'editEnabled && MessageEdits.canEdit(message)',
    'Recalls.canRecall(message)',
    'readEnabled && !mine && message.error == null && audio == null',
    'retryEnabled && !mine && canRetry',
    'reactEnabled && !mine && message.error == null',
    'onLongClick = { menu = true }',
):
    assert code in block, code
assert block.count("add(MessageMenuAction(") == 9
assert "internal const val MESSAGE_MENU_COLUMNS = 3" in popup
assert "actions.chunked(MESSAGE_MENU_COLUMNS)" in popup
assert "PopupPositionProvider" in popup and "PopupProperties(focusable = true" in popup
assert "anchorBounds" in popup and "coerceIn(edgePx, maxX)" in popup and "coerceIn(edgePx, maxY)" in popup
assert "PopupArrow(tipUp = true" in popup and "PopupArrow(tipUp = false" in popup
assert "reactionContent()" in popup
assert 'HorizontalDivider(' in popup
assert ".height(66.dp)" in popup
assert "if (!expanded || actions.isEmpty()) return" in popup
print("PASS: v0.38.15 long press grid, both alignment directions, edge clamp, arrow and all actions preserved")
