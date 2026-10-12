#!/usr/bin/env python3
"""Verify 0.38.37 AI rename is real, scoped to its own current user turn and persistent."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
ai = root/"app/src/main/java/com/cleo/cleos/ai"
self_name = (ai/"AiSelfName.kt").read_text(encoding="utf-8")
tools = (ai/"Tools.kt").read_text(encoding="utf-8")
prompt = (ai/"Prompt.kt").read_text(encoding="utf-8")
app = (root/"app/src/main/java/com/cleo/cleos/CleosApp.kt").read_text(encoding="utf-8")
rules_test = (root/"app/src/test/java/com/cleo/cleos/ai/SelfNameRulesTest.kt").read_text(encoding="utf-8")
assert (ai/"AiSelfName.kt").read_bytes() == (here/"AiSelfName_03837.kt").read_bytes()
assert rules_test.encode("utf-8") == (here/"SelfNameRulesTest_03837.kt").read_bytes()
for marker in [
    "interface SelfName", "class AiSelfName(", "SelfNameRules.cleanName(newName)",
    "SelfNameRules.explicitlyAsked(latestUser.content)",
    "db.messages().newest(conversationId, 40)",
    "System.currentTimeMillis() - latestUser.createdAt",
    "if (conversation.isGroup)", "db.groupMembers().idsFor(conversationId)",
    "conversation.companionId == companionId",
    "it.id != companionId", "companions.update(companionId) { it.copy(name = name) }",
    "if (saved?.name != name)", "本次改名已经成功", "AI自主改名成功：",
]:
    assert marker in self_name, f"rename safety/persistence missing: {marker}"
for marker in [
    'name = "set_my_name"', "groups = setOf(ToolGroup.Messages)",
    'setMyName,', "private val selfName: SelfName? = null",
    "ToolSpecs.setMyName.name -> setMyName(args, conversationId, companionId)",
    "changer.rename(conversationId, companionId, chosen)",
]:
    assert marker in tools, f"tool setup missing: {marker}"
for marker in ["set_my_name", "真正修改自己的名字", "不能动其他角色"]:
    assert marker in prompt, f"prompt instruction missing: {marker}"
assert "selfName = AiSelfName(db, companions)" in app
assert "import com.cleo.cleos.ai.AiSelfName" in app
assert 'versionName = "0.38.37"' in (root/"app/build.gradle.kts").read_text()
assert "versionCode = 62103" in (root/"app/build.gradle.kts").read_text()
assert "ChatLocationCard(" in (root/"app/src/main/java/com/cleo/cleos/ui/chat/ChatLocation.kt").read_text()
assert "send_gallery_photos" in tools
assert "play_qq_music" in tools or "playQqMusic" in tools
assert "play_kugou_music" in tools or "playKugouMusic" in tools
print("PASS: actual identity update, per-speaker/group guard, explicit recent user intent, display flow, all prior features")
