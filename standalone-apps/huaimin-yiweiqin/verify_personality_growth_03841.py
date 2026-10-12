#!/usr/bin/env python3
"""Guard: AI grows own persona, reading only authorized chats, preserving user's preset."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
ai=root/"app/src/main/java/com/cleo/cleos/ai"
src=(ai/"PersonalityGrowth.kt").read_text(encoding="utf-8")
prompt=(ai/"Prompt.kt").read_text(encoding="utf-8")
repo=(ai/"ChatRepository.kt").read_text(encoding="utf-8")
app=(root/"app/src/main/java/com/cleo/cleos/CleosApp.kt").read_text(encoding="utf-8")
vm=(root/"app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt").read_text(encoding="utf-8")
page=(root/"app/src/main/java/com/cleo/cleos/ui/settings/TaPages.kt").read_text(encoding="utf-8")
test=(root/"app/src/test/java/com/cleo/cleos/ai/PersonalityGrowthRulesTest.kt").read_text(encoding="utf-8")
assert src.encode("utf-8") == (here/"PersonalityGrowth_03841.kt").read_bytes()
assert test.encode("utf-8") == (here/"PersonalityGrowthRulesTest_03841.kt").read_bytes()
assert test.count("@Test fun ") == 11
for token in (
    "class PersonalityGrowth(", "private val lock = Mutex()",
    "db.groupMembers().idsFor(currentConversationId).contains(companionId)",
    "room.companionId == companionId", "db.messages().newest(currentConversationId, 70)",
    "db.messages().queryAuthorizedHistory(",
    "companionId = companionId", "excludeConversationId = currentConversationId",
    "if (note != null && now - note.updatedAt < PersonalityGrowthRules.MIN_INTERVAL_MS)",
    "MemoryEntity(", "name = PersonalityGrowthRules.NAME",
    "db.memories().update(latest.copy(summary = result",
    "secrets.key(ta.apiBaseUrl)", "client.stream(",
):
    assert token in src, f"Scoped AI growth/persistence missing: {token}"
assert "it.senderCompanionId == companionId" in src
assert "val evidence = messages.joinToString" in src
assert "ta.persona" in src
assert "PersonalityGrowthRules.learned(memories)" in prompt
assert "if (ta.persona.isNotBlank()) add(ta.persona.trim())" in prompt
assert "memories.filterNot { it.name == PersonalityGrowthRules.NAME }" in prompt
assert repo.count("PersonalityGrowthRules.forPrompt(") >= 5
assert "personalityGrowth?.maybeGrow(ta.id, conversationId)" in repo
assert "personalityGrowth?.maybeGrow(member.id, conversationId)" in repo
assert "personalityGrowth = PersonalityGrowth(db, companions, secrets, chatClient)" in app
assert "fun growingPersona(id: Long)" in vm
assert "AI 自己形成的性格" in page
assert "你手动预设与编辑功能" in page or "手动预设与编辑功能" in page
assert "PersonaScreen" in (root/"app/src/main/java/com/cleo/cleos/ui/CleosNavHost.kt").read_text()
gradle=(root/"app/build.gradle.kts").read_text()
assert 'versionName = "0.38.41"' in gradle and "versionCode = 62107" in gradle
tools=(ai/"Tools.kt").read_text()
assert "set_my_name" in tools and "search_web_avatar_images" in tools
assert "play_qq_music" in tools and "play_kugou_music" in tools
assert "ChatLocationCard(" in (root/"app/src/main/java/com/cleo/cleos/ui/chat/ChatLocation.kt").read_text()
print("PASS separate AI personality, authorized group/private history, backup-backed storage, manual editing preserved")
