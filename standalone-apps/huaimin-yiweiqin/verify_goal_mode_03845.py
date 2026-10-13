#!/usr/bin/env python3
"""Strict 0.38.45 integration check; fail before building if any cross-layer wiring is missing."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve(); here=Path(__file__).resolve().parent
ai=r/"app/src/main/java/com/cleo/cleos/ai"
ui=r/"app/src/main/java/com/cleo/cleos/ui/chat"
a=(ai/"GoalMode.kt").read_text(encoding="utf-8")
b=(ai/"GoalCheckpointTool.kt").read_text(encoding="utf-8")
chat=(ai/"ChatRepository.kt").read_text(encoding="utf-8")
vm=(ui/"ChatViewModel.kt").read_text(encoding="utf-8")
screen=(ui/"ChatScreen.kt").read_text(encoding="utf-8")
app=(r/"app/src/main/java/com/cleo/cleos/CleosApp.kt").read_text(encoding="utf-8")
u=(ui/"GoalModeUi.kt").read_text(encoding="utf-8")
test=(r/"app/src/test/java/com/cleo/cleos/ai/GoalModeRulesTest.kt")
for path in ("GoalMode", "GoalCheckpointTool"):
    assert (ai/(path+".kt")).read_bytes() == (here/(path+"_03845.kt")).read_bytes()
assert (ui/"GoalModeUi.kt").read_bytes() == (here/"GoalModeUi_03845.kt").read_bytes()
assert test.read_bytes() == (here/"GoalModeRulesTest_03845.kt").read_bytes()
assert test.read_text().count("@Test fun ")==14
for x in (
    "AtomicFile(", "Mutex()", "GoalArchive", "val version: Int", "val companionId: Long",
    "val conversationId: Long", "status: String", "GoalCriterion",
    "GoalReceipt", "GoalEvent", "GoalBudget", "suspend fun checkpoint(",
    "suspend fun startCall(", "suspend fun beginTool(", "suspend fun toolResult(",
    "suspend fun approve(", "suspend fun pause(", "suspend fun resume(",
    "suspend fun cancel(", "suspend fun edit(", "suspend fun verify(",
    "suspend fun reconcile(", "suspend fun adjustBudget(", "suspend fun userNote(",
    "lastError", "inFlight", "approvedFingerprint", "GoalRules.canComplete",
    "GoalRules.resumeState", "status=\"BLOCKED\"", "status=\"PAUSED\"",
):
    assert x in a, "Goal persistence, budget or verification missing: "+x
assert 'name="goal_checkpoint"' in b
for x in (
    "private val goalStore: GoalStore? = null",
    "private val goalExecuting = ConcurrentHashMap",
    "fun goalCreate(", "fun goalControl(", "fun goalCommand(",
    "private fun scheduleGoal(", "private suspend fun goalLoop(",
    "private suspend fun goalTurn(", "goalExecuting[conversationId]=",
    "goalStore?.beginTool(", "goalStore?.toolResult(",
    "GoalCheckpointTool.spec", "GoalRules.sideEffect(",
    "goalStore?.active(", 'goals', # no-op checked by next line
):
    if x=='goals':continue
    assert x in chat, "ChatRepository goal integration missing: "+x
for x in ("val goalStore = GoalStore(context.applicationContext)",
          "goalStore = goalStore,"):
    assert x in app, "Goal container missing: "+x
for x in ("c.chat.goalCommand(", "if(attachments.isEmpty()"):
    assert x in vm, "Goal /goal command missing: "+x
for x in ("GoalModeDialog(", "GoalStatusPill(", 'Text("◎ 目标"',
          "goalPanelOpen", "c.goalStore.records"):
    assert x in screen, "Goal UI entry missing: "+x
for x in ('Text("验收证据"', "我验收通过", "明确取消当前目标", "最长分钟"):
    assert x in u, "UI missing control: "+x
assert 'versionName = "0.38.45"' in (r/"app/build.gradle.kts").read_text()
assert "versionCode = 62111" in (r/"app/build.gradle.kts").read_text()
tools=(ai/"Tools.kt").read_text()
for x in ("send_table","send_web_image","search_web_avatar_images","send_gallery_photos","play_qq_music","play_kugou_music"):
    assert x in tools, "Previous tool lost: "+x
assert "ChatLocationCard(" in screen
assert "PersonalityGrowthRules.forPrompt(" in chat
print("PASS goal mode, versioned checkpoints, tool authorizations, budgets, true chat tools and existing features")
