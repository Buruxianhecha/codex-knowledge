#!/usr/bin/env python3
"""Structural regression: compact menu appearance, no missing actions or callbacks."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
menu=(r/"app/src/main/java/com/cleo/cleos/ui/chat/WechatMessageMenu.kt").read_text(encoding="utf-8")
chat=(r/"app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt").read_text(encoding="utf-8")
version=(r/"app/build.gradle.kts").read_text(encoding="utf-8")
assert 'versionName = "0.38.29"' in version and 'versionCode = 62095' in version
checks=[
    "internal const val MESSAGE_MENU_COLUMNS = 2",
    "internal const val MESSAGE_MENU_ITEM_HEIGHT_DP = 49",
    "internal const val MESSAGE_MENU_ROW_GAP_DP = 6",
    "val width = minOf(316.dp, maxWidth)",
    "private val PanelShape = RoundedCornerShape(22.dp)",
    "private val TileShape = RoundedCornerShape(15.dp)",
    "private val WhiteInk = Color(0xFF263953)",
    "private val DeleteInk = Color(0xFFAE5965)",
    "fontSize = 14.sp",
    "Modifier.size(20.dp)",
    ".shadow(7.dp, PanelShape)",
    "MessageMenuGlyph.SELECT",
    "actions.chunked(MESSAGE_MENU_COLUMNS)",
    "GlassActionTile(item, Modifier.weight(1f))",
    ".clickable(onClick = item.onClick)",
    "reactionContent()",
    "ChatMenuPositionProvider(",
    "heightIn(max = maxHeight)",
    "verticalScroll(rememberScrollState())",
    "GlassArrow(up = true",
    "GlassArrow(up = false",
    "private fun GlassActionTile(",
]
for token in checks:
    assert token in menu,token
for glyph in ["FAVORITE","COPY","READ","QUOTE","TRANSCRIBE","RETRY","EDIT","RECALL","DELETE","SELECT"]:
    assert f"MessageMenuGlyph.{glyph}" in menu,glyph
bubble=chat[chat.index("private fun MessageBubble("):chat.index("private fun VoiceBubble(")]
for action in [
    'MessageMenuGlyph.FAVORITE','MessageMenuGlyph.COPY','MessageMenuGlyph.READ',
    'MessageMenuGlyph.QUOTE','MessageMenuGlyph.TRANSCRIBE',
    'MessageMenuGlyph.RETRY','MessageMenuGlyph.EDIT',
    'MessageMenuGlyph.RECALL','MessageMenuGlyph.DELETE','MessageMenuGlyph.SELECT',
    "favoritesContainer.moments.favorite(","clipboard.setClipEntry(",
    "onRead(words)","onQuote()","onRetryVoice()","onRetry()",
    "onEdit()","onRecall()","onDelete()","onReact(emoji)",
    "onMultiSelect()","WechatMessagePopup(",
]:
    assert action in bubble,action
assert "(actions.size + MESSAGE_MENU_COLUMNS - 1) / MESSAGE_MENU_COLUMNS" in bubble
assert "(compactMenuRows * MESSAGE_MENU_ITEM_HEIGHT_DP).dp" in bubble
assert "PopupProperties(" in menu
assert "Button(" not in menu
print("PASS 0.38.29: light compact grid, reaction picker, 10 action types, anchor/scroll and all callback handlers")
