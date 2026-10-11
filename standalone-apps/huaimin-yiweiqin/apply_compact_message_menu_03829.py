#!/usr/bin/env python3
"""0.38.29: light, compact two-column long-press menu; behavior stays untouched."""
from pathlib import Path
import sys
root = Path(sys.argv[1]).resolve()
path = root / "app/src/main/java/com/cleo/cleos/ui/chat/WechatMessageMenu.kt"
chat_path = root / "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
gradle = root / "app/build.gradle.kts"
s = path.read_text(encoding="utf-8")

def rep(old, new, description):
    global s
    n = s.count(old)
    if n != 1:
        raise RuntimeError(f"{description}: expected one exact match, found {n}; preserving original")
    s = s.replace(old, new, 1)

# This is an incremental style-only pass on the REAL generated 0.38.28 menu
# (including multi-select). No action list/callback/menu glyph is replaced.
for needle in (
    "internal const val MESSAGE_MENU_COLUMNS = 2",
    "MessageMenuGlyph.SELECT",
    "actions.chunked(MESSAGE_MENU_COLUMNS)",
    "GlassActionTile(item, Modifier.weight(1f))",
    ".clickable(onClick = item.onClick)",
    "reactionContent()",
    "ChatMenuPositionProvider(",
    "GlassArrow(up = true",
    "GlassArrow(up = false",
    "private fun GlassActionTile(",
):
    if needle not in s:
        raise RuntimeError(f"Unexpected predecessor, cannot safely style menu: {needle}")

rep("internal const val MESSAGE_MENU_COLUMNS = 2",
    "internal const val MESSAGE_MENU_COLUMNS = 2\n"
    "internal const val MESSAGE_MENU_ITEM_HEIGHT_DP = 49\n"
    "internal const val MESSAGE_MENU_ROW_GAP_DP = 6",
    "layout measurements")
rep('private val GlassBlue = Color(0xFF15314F)',
    'private val GlassBlue = Color(0xFFB6CEED)', "popup pointer color")
rep('private val GlassNavy = Color(0xFF0B2039)',
    'private val GlassNavy = Color(0xFFB2CAEA)', "background accent")
rep('private val IcyLight = Color(0xFF9FD8FF)',
    'private val IcyLight = Color(0xFFDCEBFF)', "diffuse highlight")
rep('private val WhiteInk = Color(0xFFF3F8FF)',
    'private val WhiteInk = Color(0xFF263953)', "legible dark ink")
rep('private val DeleteInk = Color(0xFFF19A9F)',
    'private val DeleteInk = Color(0xFFAE5965)', "soft destructive ink")
rep('private val PanelShape = RoundedCornerShape(27.dp)',
    'private val PanelShape = RoundedCornerShape(22.dp)', "panel corner")
rep('private val TileShape = RoundedCornerShape(19.dp)',
    'private val TileShape = RoundedCornerShape(15.dp)', "tile corner")

rep('val maxWidth = (config.screenWidthDp - 22).coerceAtLeast(210).dp',
    'val maxWidth = (config.screenWidthDp - 24).coerceAtLeast(196).dp',
    "small screen width")
rep('val width = minOf(366.dp, maxWidth)',
    'val width = minOf(316.dp, maxWidth)', "menu width")
rep('val maxHeight = (config.screenHeightDp * 0.65f).dp.coerceAtLeast(180.dp)',
    'val maxHeight = (config.screenHeightDp * 0.60f).dp.coerceAtLeast(168.dp)',
    "bounded scroll on small screens")
rep('val gap = with(density) { 4.dp.roundToPx() }',
    'val gap = with(density) { 3.dp.roundToPx() }', "anchor gap")

rep('.shadow(13.dp, PanelShape)',
    '.shadow(7.dp, PanelShape)', "drop shadow")
rep('''                            colors = listOf(
                                GlassBlue.copy(alpha = 0.95f),
                                Color(0xFF17516B).copy(alpha = 0.88f),
                                GlassNavy.copy(alpha = 0.91f),
                            ),''',
    '''                            colors = listOf(
                                Color(0xFFCBDDF4).copy(alpha = 0.94f),
                                Color(0xFFB6D0F1).copy(alpha = 0.90f),
                                Color(0xFFD8E7F8).copy(alpha = 0.91f),
                            ),''', "light translucent panel")
rep('''                                Color.White.copy(alpha = 0.53f),
                                IcyLight.copy(alpha = 0.23f),
                                Color.White.copy(alpha = 0.09f),''',
    '''                                Color.White.copy(alpha = 0.64f),
                                IcyLight.copy(alpha = 0.34f),
                                Color.White.copy(alpha = 0.22f),''', "soft glass hairline")
rep('.blur(21.dp)', '.blur(12.dp)', "less milky internal bloom")
rep('''                                    IcyLight.copy(alpha = 0.20f),
                                    Color(0xFF39B9D6).copy(alpha = 0.08f),
                                    Color(0xFF06152C).copy(alpha = 0.12f),''',
    '''                                    Color.White.copy(alpha = 0.18f),
                                    IcyLight.copy(alpha = 0.10f),
                                    GlassNavy.copy(alpha = 0.07f),''',
    "remove dark plastic tint")
rep('.padding(horizontal = 11.dp, vertical = 12.dp)',
    '.padding(horizontal = 9.dp, vertical = 9.dp)',
    "tighter panel inset")
rep('verticalArrangement = Arrangement.spacedBy(8.dp)',
    'verticalArrangement = Arrangement.spacedBy(MESSAGE_MENU_ROW_GAP_DP.dp)',
    "row spacing")
rep('horizontalArrangement = Arrangement.spacedBy(8.dp)',
    'horizontalArrangement = Arrangement.spacedBy(7.dp)',
    "column spacing")
rep('.height(59.dp)',
    '.height(MESSAGE_MENU_ITEM_HEIGHT_DP.dp)',
    "compact tile height")
rep('''                        Color(0xFF8EB7E0).copy(alpha = 0.20f),
                        Color(0xFF2F6085).copy(alpha = 0.30f),
                        Color(0xFF0B2945).copy(alpha = 0.15f),''',
    '''                        Color.White.copy(alpha = 0.35f),
                        Color(0xFFE7F0FF).copy(alpha = 0.28f),
                        Color(0xFF9BB7DD).copy(alpha = 0.24f),''',
    "pale distinct tiles")
rep('''                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.07f),''',
    '''                        Color.White.copy(alpha = 0.43f),
                        Color.White.copy(alpha = 0.17f),''',
    "soft tile edge")
rep('.padding(horizontal = 12.dp)',
    '.padding(horizontal = 10.dp)',
    "tile inset")
rep('modifier = Modifier.size(23.dp)',
    'modifier = Modifier.size(20.dp)',
    "icon size")
rep('Spacer(Modifier.width(10.dp))',
    'Spacer(Modifier.width(8.dp))',
    "text-icon gap")
rep('fontSize = 15.sp',
    'fontSize = 14.sp',
    "menu label size")
rep('Box(Modifier.fillMaxWidth().height(9.dp))',
    'Box(Modifier.fillMaxWidth().height(7.dp))',
    "small arrow height")
rep('.padding(horizontal = 27.dp)',
    '.padding(horizontal = 23.dp)',
    "arrow inset")
rep('.size(width = 17.dp, height = 9.dp)',
    '.size(width = 14.dp, height = 7.dp)',
    "smaller arrow")

path.write_text(s, encoding="utf-8")

chat = chat_path.read_text(encoding="utf-8")
old = '''                val estimatedMenuHeight = with(LocalDensity.current) {
                    (if (canReactInMenu) 235.dp else 167.dp).roundToPx()
                }'''
new = '''                val compactMenuRows = (actions.size + MESSAGE_MENU_COLUMNS - 1) / MESSAGE_MENU_COLUMNS
                val estimatedMenuHeight = with(LocalDensity.current) {
                    (18.dp + (compactMenuRows * MESSAGE_MENU_ITEM_HEIGHT_DP).dp +
                        ((compactMenuRows - 1).coerceAtLeast(0) * MESSAGE_MENU_ROW_GAP_DP).dp +
                        (if (canReactInMenu) 65.dp else 0.dp) + 7.dp).roundToPx()
                }'''
n=chat.count(old)
if n!=1: raise RuntimeError(f"ChatScreen menu height anchor unexpected ({n}); refusing partial change")
chat_path.write_text(chat.replace(old,new,1),encoding="utf-8")

v=gradle.read_text(encoding="utf-8")
for old,new in [
    ('versionName = "0.38.28"','versionName = "0.38.29"'),
    ('versionCode = 62094','versionCode = 62095'),
]:
    if v.count(old)!=1: raise RuntimeError(f"Version anchor not unique: {old}")
    v=v.replace(old,new,1)
gradle.write_text(v,encoding="utf-8")
print("0.38.29/62095: compact icy-glass message menu, same action handlers and multi-select")
