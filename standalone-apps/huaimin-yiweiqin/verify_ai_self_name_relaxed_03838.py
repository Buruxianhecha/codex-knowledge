#!/usr/bin/env python3
"""Regression guard: 0.38.38 conversational rename, persistent and own-character only."""
from pathlib import Path
import sys

r = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
ai = r / "app/src/main/java/com/cleo/cleos/ai"
src = (ai / "AiSelfName.kt").read_text(encoding="utf-8")
tools = (ai / "Tools.kt").read_text(encoding="utf-8")
prompt = (ai / "Prompt.kt").read_text(encoding="utf-8")
tests = (r / "app/src/test/java/com/cleo/cleos/ai/SelfNameRulesTest.kt").read_text(encoding="utf-8")
assert src.encode("utf-8") == (here / "AiSelfName_03838.kt").read_bytes()
assert tests.encode("utf-8") == (here / "SelfNameRulesTest_03838.kt").read_bytes()
for bad in (
    "explicitlyAsked(", "latestUser", "600_000L",
    "尚未明确授权", "本轮没有改名授权",
    "本次改名已经成功，不必再反复修改",
):
    assert bad not in src, f"overly strict authorization gate remains: {bad}"
for good in (
    "SelfNameRules.cleanName(newName)",
    "if (conversation.isGroup)",
    "db.groupMembers().idsFor(conversationId).contains(companionId)",
    "conversation.companionId == companionId",
    "companions.get(companionId)",
    "it.id != companionId",
    "companions.update(companionId) { it.copy(name = name) }",
    "if (saved?.name != name)",
):
    assert good in src, f"self identity/persistence protection missing: {good}"
for good in ("set_my_name", "那你换你喜欢的吧", "没有十分钟限时", "只能改你自己的名字"):
    assert good in tools, f"tool natural-chat guidance missing: {good}"
for good in ("set_my_name", "整段正常聊天理解", "无需固定句式", "随你选", "不能改其他角色"):
    assert good in prompt, f"prompt natural-chat guidance missing: {good}"
assert tests.count("@Test fun ") == 8, "8 tests must run"
gradle = (r / "app/build.gradle.kts").read_text()
assert 'versionName = "0.38.38"' in gradle
assert "versionCode = 62104" in gradle
assert "send_gallery_photos" in tools
assert "play_qq_music" in tools or "playQqMusic" in tools
assert "play_kugou_music" in tools or "playKugouMusic" in tools
assert "ChatLocationCard(" in (r / "app/src/main/java/com/cleo/cleos/ui/chat/ChatLocation.kt").read_text()
print("PASS: natural conversational AI rename, no regex/time gate, persistent own-identity edits, prior features")
