#!/usr/bin/env python3
"""0.37.26: expand existing reaction picker without replacing the AI reaction event pipeline."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
model = root / "app/src/main/java/com/cleo/cleos/data/MessageReactions.kt"
ui = root / "app/src/main/java/com/cleo/cleos/ui/chat/Stickers.kt"

def once(text, before, after, name):
    count = text.count(before)
    if count != 1:
        raise RuntimeError(f"{name}: expected one matching anchor, found {count}")
    return text.replace(before, after, 1)

m = model.read_text(encoding="utf-8")
m = once(m, '    val OFFERED = listOf("❤️", "😘", "😂", "🥺", "😭", "👍", "🤗")',
'''    val OFFERED = listOf("❤️", "😘", "😂", "🥺", "😭", "👍", "🤗")
    val ALL = (OFFERED + listOf(
        "😼", "😻", "😹", "🐱", "🐰", "🐶", "👀", "🙈", "🙉", "🙊",
        "😊", "🥰", "😍", "😎", "🤔", "😮", "😱", "🤯", "😴", "🤤",
        "🥲", "😅", "🤣", "🙃", "😏", "😤", "😡", "🤡", "👻", "💀",
        "👎", "👏", "🙌", "🙏", "👌", "✌️", "🤝", "💪", "💋", "💔",
        "💕", "💖", "💯", "🔥", "✨", "🎉", "🎂", "🌹", "🌸", "🍀",
        "🌙", "☀️", "🌈", "⭐", "☕", "🍵", "🍓", "🍰", "🫶", "🫂", "🫡", "🫠"
    )).distinct()''', "emoji catalog")
u = ui.read_text(encoding="utf-8")
needle = '/** The row at the top of a TA message\'s menu: tap one to put it on, tap one that is on to take it off. */'
start = u.index(needle)
end = u.index('/**', start + len(needle))
replacement = '''/** Compact quick reactions and a bounded, scrollable six-column emoji panel.
 * Selection keeps the existing onPick callback and ReactionEvents AI awareness unchanged.
 */
@Composable
fun ReactionPicker(on: Set<String>, onPick: (String) -> Unit) {
    val palette = LocalGlassPalette.current
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.width(272.dp).padding(horizontal = 4.dp, vertical = 2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MessageReactions.OFFERED.take(6).forEach { emoji ->
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(if (emoji in on) palette.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onPick(emoji) },
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 21.sp) }
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).clickable { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) { Text(if (expanded) "−" else "+", fontSize = 22.sp, color = palette.content) }
        }
        if (expanded) {
            Text("点选回应 · 再点取消", fontSize = 11.sp,
                color = palette.contentSecondary, modifier = Modifier.padding(8.dp))
            Column(Modifier.heightIn(max = 210.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                MessageReactions.ALL.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { emoji ->
                            Box(Modifier.size(42.dp).clip(CircleShape)
                                .background(if (emoji in on) palette.accent.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { onPick(emoji) },
                                contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

'''
u = u[:start] + replacement + u[end:]
u = once(u, 'import androidx.compose.runtime.Composable', 'import androidx.compose.runtime.Composable\nimport androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.layout.heightIn', "compose imports")
# No database/schema changes: existing serialized reaction list and backup remain compatible.
model.write_text(m, encoding="utf-8")
ui.write_text(u, encoding="utf-8")
print("0.37.26 expandable emoji picker applied")
