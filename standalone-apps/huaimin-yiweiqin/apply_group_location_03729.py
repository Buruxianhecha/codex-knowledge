#!/usr/bin/env python3
"""v0.37.29: opt-in Android location result for multi-character group chat."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
chat = root / "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"

def once(old: str, new: str) -> None:
    source = chat.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"group-location: expected one anchor, found {count}: {old[:110]!r}")
    chat.write_text(source.replace(old, new, 1), encoding="utf-8")

# A fresh explicit user request alone can query GPS. Never trigger on group continuation,
# another AI's message, or the historical chat excerpts.
# Read once and make the same verified result available to all members in this turn.
once(
'''        val mentions = if (continued) emptyList() else GroupChats.targeted(latestText, trigger?.mentionedCompanionIds, members)
        val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }''',
'''        val mentions = if (continued) emptyList() else GroupChats.targeted(latestText, trigger?.mentionedCompanionIds, members)
        val groupLocation = if (!continued && trigger?.role == "user" && GroupLocationRules.explicitlyRequested(latestText)) {
            if (ToolGroup.Location !in s.tools) {
                note(conversationId, "查位置没成：尚未开启查位置")
                GroupLocationRules.context("用户没有在设置里开启「查位置」功能。", verified = false)
            } else {
                val outcome = try {
                    tools.run(
                        ToolCall("group-location-" + conversationId + "-" + trigger.id, ToolSpecs.getLocation.name, "{}"),
                        s, conversationId, members.first().id,
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ToolOutcome("定位工具暂时出错，请稍后重试。", "查位置没成：工具出错")
                }
                note(conversationId, outcome.note.ifBlank { "查位置没成：未返回结果" })
                GroupLocationRules.context(outcome.result, verified = outcome.note.startsWith("查了你的位置"))
            }
        } else null
        val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }'''
)
once(
'''            val context = worldContext(conversationId, latestText, s)
            show(StreamingReply(conversationId, "", thinking = false, activity = "${ta.name.trim().ifEmpty { "TA" }}正在输入"))''',
'''            val context = listOfNotNull(worldContext(conversationId, latestText, s), groupLocation)
                .joinToString("\\n\\n").ifBlank { null }
            show(StreamingReply(conversationId, "", thinking = false, activity = "${ta.name.trim().ifEmpty { "TA" }}正在输入"))'''
)
once(
'''if (groupTurn(conversationId, ta, members, shaped, context, targeted = patTarget != null || mentions.isNotEmpty() || conversation.groupMode == 1)) said++''',
'''if (groupTurn(conversationId, ta, members, shaped, context, targeted = patTarget != null || mentions.isNotEmpty() || conversation.groupMode == 1 || groupLocation != null)) said++'''
)
for source_name, target in (
    ("GroupLocationRules.kt", "app/src/main/java/com/cleo/cleos/ai/GroupLocationRules.kt"),
    ("GroupLocationRulesTest.kt", "app/src/test/java/com/cleo/cleos/ai/GroupLocationRulesTest.kt"),
):
    dst = root / target
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(here / source_name, dst)

gradle = root / "app/build.gradle.kts"
text = gradle.read_text(encoding="utf-8")
for old, new in (
    ('versionName = "0.37.26"', 'versionName = "0.37.29"'),
    ('versionCode = 62048', 'versionCode = 62051'),
):
    if text.count(old) != 1:
        raise SystemExit(f"group-location: unexpected version anchor {old!r}")
    text = text.replace(old, new, 1)
gradle.write_text(text, encoding="utf-8")
print("v0.37.29 / 62051: opt-in group location (one lookup per request, no DB migration)")
