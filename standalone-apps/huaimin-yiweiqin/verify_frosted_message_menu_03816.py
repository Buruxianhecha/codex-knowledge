#!/usr/bin/env python3
"""Structural checks for translucent 2-column menu without loss of message actions."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
menu = (root / "app/src/main/java/com/cleo/cleos/ui/chat/WechatMessageMenu.kt").read_text(encoding="utf-8")
chat = (root / "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt").read_text(encoding="utf-8")
build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
block = chat[chat.index("private fun MessageBubble("):chat.index("private fun VoiceBubble(")]
assert 'versionName = "0.38.16"' in build
assert "versionCode = 62082" in build
for token in (
    "internal const val MESSAGE_MENU_COLUMNS = 2",
    "actions.chunked(MESSAGE_MENU_COLUMNS)",
    "private fun GlassActionTile(",
    "GlassActionTile(item, Modifier.weight(1f))",
    "Brush.linearGradient(",
    "Color.White.copy(alpha = 0.53f)",
    "Modifier.height(59.dp)" if "Modifier.height(59.dp)" in menu else ".height(59.dp)",
    "private val DeleteInk = Color(0xFFF19A9F)",
    "destructive = item.glyph == MessageMenuGlyph.DELETE",
    "PopupProperties(",
    "heightIn(max = maxHeight)",
    "verticalScroll(rememberScrollState())",
    "ChatMenuPositionProvider(",
    "coerceIn(edgePx, maxX)",
    "coerceIn(edgePx, maxY)",
    "GlassArrow(up = true",
    "GlassArrow(up = false",
    "reactionContent()",
):
    assert token in menu, token
for token in (
    '"收藏", MessageMenuGlyph.FAVORITE',
    '"复制", MessageMenuGlyph.COPY',
    'MessageMenuGlyph.READ',
    '"引用", MessageMenuGlyph.QUOTE',
    '"重新转文字", MessageMenuGlyph.TRANSCRIBE',
    '"重新回答", MessageMenuGlyph.RETRY',
    '"编辑", MessageMenuGlyph.EDIT',
    '"撤回", MessageMenuGlyph.RECALL',
    '"删除", MessageMenuGlyph.DELETE',
    "favoritesContainer.moments.favorite(",
    "clipboard.setClipEntry(",
    "onRead(words)", "onQuote()", "onRetryVoice()", "onRetry()",
    "onEdit()", "onRecall()", "onDelete()", "onReact(emoji)",
    "WechatMessagePopup(",
):
    assert token in block, token
assert block.count("add(MessageMenuAction(") == 9
assert "DropdownMenu(expanded = menu" not in block
print("PASS 0.38.16: frosted glass 2-column UI and every existing action callback retained")
