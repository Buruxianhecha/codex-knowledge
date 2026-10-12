#!/usr/bin/env python3
"""0.38.42 controls are per-character, backed up, user-editable and effective in runtime."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
ai=root/"app/src/main/java/com/cleo/cleos/ai"
ui=root/"app/src/main/java/com/cleo/cleos/ui/settings"
files={
 "personality":ai/"PersonalityGrowth.kt",
 "config":ai/"PersonalityGrowthConfig.kt",
 "app":root/"app/src/main/java/com/cleo/cleos/CleosApp.kt",
 "vm":ui/"SettingsViewModel.kt",
 "page":ui/"TaPages.kt",
 "prompt":ai/"Prompt.kt",
}
t={key:p.read_text(encoding="utf-8") for key,p in files.items()}
assert t["personality"].encode() == (here/"PersonalityGrowth_03842.kt").read_bytes()
assert t["config"].encode() == (here/"PersonalityGrowthConfig_03842.kt").read_bytes()
unit=(root/"app/src/test/java/com/cleo/cleos/ai/PersonalityGrowthConfigTest.kt")
assert unit.read_bytes() == (here/"PersonalityGrowthConfigTest_03842.kt").read_bytes()
assert unit.read_text().count("@Test fun ") == 12
for item in (
    "val enabled: Boolean = true",
    "val minMessages: Int = 14",
    "val minHours: Int = 6",
    "val manualEnabled: Boolean = true",
    "fun observe(companionId: Long)",
    "suspend fun update(companionId: Long",
    "private val mutex = Mutex()",
    "db.memories().insert(MemoryEntity(",
    "db.memories().update(current.copy",
):
    assert item in t["config"], "Missing persistent independent settings: "+item
for item in (
    "suspend fun growNow(companionId: Long): String",
    "preferences.get(companionId).manualEnabled",
    "if (!force && !config.enabled)",
    "if (!force && note != null",
    "minimum = config.minMessages",
    "intervalMs = config.minHours * 3600000L",
    "if (force && messages.isEmpty())",
    "db.groupMembers().idsFor(room.id)",
    "room.companionId == companionId",
    "return@withLock false",
):
    assert item in t["personality"], "Runtime control missing: "+item
for item in (
    "val personalityGrowthConfig = PersonalityGrowthConfigStore(db)",
    "val personalityGrowth = PersonalityGrowth(db, companions, secrets, chatClient, personalityGrowthConfig)",
    "personalityGrowth = personalityGrowth,",
):
    assert item in t["app"], "Container wiring missing: "+item
for item in (
    "fun growthOptions(id: Long)",
    "fun changeGrowth(id: Long, change:",
    "fun growNow(id: Long)",
    "c.personalityGrowth.growNow(id)",
):
    assert item in t["vm"], "VM wiring missing: "+item
for item in (
    'Text("AI 自主性格成长设置"',
    'Text("自动形成和更新性格"',
    'Text("允许手动立即总结"',
    'Slider(value = messageTarget',
    'Slider(value = intervalTarget',
    'vm.growNow(vm.companionId)',
    'Text("AI 自己形成的性格"',
    "onClick = onOpenPersona",
):
    assert item in t["page"], "UI function missing: "+item
assert "PersonalityGrowthConfigStore.isConfig(it)" in t["prompt"]
assert 'versionName = "0.38.42"' in (root/"app/build.gradle.kts").read_text()
assert "versionCode = 62108" in (root/"app/build.gradle.kts").read_text()
tools=(ai/"Tools.kt").read_text()
assert "search_web_avatar_images" in tools and "set_my_name" in tools
assert "play_qq_music" in tools and "play_kugou_music" in tools
print("PASS: per-character growth settings, real immediate summary, old presets/history and tools retained")
