#!/usr/bin/env python3
"""0.38.46: ensure the screenshot's false wait is actually repaired without relaxing file acceptance."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve();here=Path(__file__).resolve().parent
ai=root/"app/src/main/java/com/cleo/cleos/ai"
ui=root/"app/src/main/java/com/cleo/cleos/ui/chat"
files=[
    (ai/"GoalMode.kt",here/"GoalMode_03845.kt"),
    (ai/"GoalDelivery.kt",here/"GoalDelivery_03846.kt"),
    (ui/"GoalModeUi.kt",here/"GoalModeUi_03846.kt"),
    (root/"app/src/test/java/com/cleo/cleos/ai/GoalDeliveryRulesTest.kt",
        here/"GoalDeliveryRulesTest_03846.kt"),
]
for actual,expected in files:
    assert actual.read_bytes()==expected.read_bytes(), f"mismatch: {actual}"
test=(root/"app/src/test/java/com/cleo/cleos/ai/GoalDeliveryRulesTest.kt").read_text()
assert test.count("@Test fun ")==15
g=(ai/"GoalMode.kt").read_text(encoding="utf-8")
d=(ai/"GoalDelivery.kt").read_text(encoding="utf-8")
u=(ui/"GoalModeUi.kt").read_text(encoding="utf-8")
chat=(ai/"ChatRepository.kt").read_text(encoding="utf-8")
for expected in (
    "val delivery: GoalDelivery = GoalDelivery()",
    "GoalDeliveryRules.decide(g,steps,reason,wait)",
    "decision!=null -> decision.status",
    "suspend fun chooseDelivery(id: String, selected: String)",
    "GoalDeliveryRules.replacedCriteria(g,selected)",
    "version=g.version+1", "GoalDeliveryRules.formatGap(g.lastError)",
    "先继续检索与正文整理", "明确确认变更验收要求", "文件交付及字体检查",
):
    if expected not in g and expected not in d:
        raise AssertionError(f"missing delivery state logic: {expected}")
for expected in (
    'onControl("delivery_chat"', 'onControl("delivery_markdown"',
    "确认改为聊天正文交付", "确认改为 Markdown 交付",
    "原目标的文件或字体要求仍属于必需验收",
):
    assert expected in u, f"missing explicit choice UI: {expected}"
for expected in (
    '"delivery_chat","delivery_markdown" ->',
    'store.chooseDelivery(g.id,selected)',
    'scheduleGoal(conversationId)',
):
    assert expected in chat, f"missing control wiring: {expected}"
assert "GoalCheckpointTool.spec" in chat
assert "GoalRules.canComplete(criteria)" in g
assert "用户手动确认" in g
assert 'versionName = "0.38.46"' in (root/"app/build.gradle.kts").read_text()
assert 'versionCode = 62112' in (root/"app/build.gradle.kts").read_text()
for prev in ("send_table","send_web_image","set_my_avatar","play_qq_music","play_kugou_music"):
    assert prev in (ai/"Tools.kt").read_text(),f"lost original tool: {prev}"
print("PASS 0.38.46 Goal Mode: optional auto fallback, mandatory criteria preserved, explicit UI approval")
