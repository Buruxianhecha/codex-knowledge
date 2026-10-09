#!/usr/bin/env python3
"""Preserve the actual group role for AI-initiated pat events.

Part of 0.37.36; no Room schema change (PatRecord already stores targetCompanionId).
"""
from pathlib import Path
from shutil import copyfile
import sys
root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

def patch(path, before, after, label):
    p = root / path
    t = p.read_text(encoding="utf-8")
    hits = t.count(before)
    if hits != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, got {hits}")
    p.write_text(t.replace(before, after, 1), encoding="utf-8")

tools = "app/src/main/java/com/cleo/cleos/ai/Tools.kt"
chat = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
app = "app/src/main/java/com/cleo/cleos/CleosApp.kt"

patch(tools,
'''private val patBack: suspend (conversationId: Long, suffix: String) -> Unit = { _, _ -> },''',
'''private val patBack: suspend (conversationId: Long, suffix: String, speakerId: Long) -> Unit = { _, _, _ -> },''',
"per-character patBack callback")
patch(tools,
'''ToolSpecs.patUser.name -> patUser(args, conversationId)''',
'''ToolSpecs.patUser.name -> patUser(args, conversationId, companionId)''',
"pat user tool passes real speaker identity")
patch(tools,
'''private suspend fun patUser(a: JsonObject, conversationId: Long): ToolOutcome {
        patBack(conversationId, Pats.cleanSuffix(ToolArgs.text(a, "suffix").orEmpty()))''',
'''private suspend fun patUser(a: JsonObject, conversationId: Long, companionId: Long): ToolOutcome {
        patBack(conversationId, Pats.cleanSuffix(ToolArgs.text(a, "suffix").orEmpty()), companionId)''',
"pat user callback receives companionId")
patch(app,
'''patBack = { id, suffix -> chat.patBack(id, suffix) },''',
'''patBack = { id, suffix, actor -> chat.patBack(id, suffix, actor) },''',
"app callback is role aware")
patch(chat,
'''suspend fun patBack(conversationId: Long, suffix: String) {''',
'''suspend fun patBack(conversationId: Long, suffix: String, actor: Long? = null) {''',
"group patBack caller identity")
patch(chat,
'''content = Pats.encode(PatRecord(Pats.FROM_AI, 1, verb, Pats.cleanSuffix(suffix))),''',
'''content = Pats.encode(PatRecord(Pats.FROM_AI, 1, verb, Pats.cleanSuffix(suffix),
                        targetCompanionId = actor)),''',
"persist AI pat speaker in existing PatRecord")

# Group prompt now offers explicitly enabled non-device social actions to any
# group member, while still reserving all phone/MCP calls to one person per turn.
patch(chat,
'''            tools = if (toolSupport) permittedGroups else
                if (ToolGroup.Memory in s.tools) setOf(ToolGroup.Memory) else emptySet(),''',
'''            tools = if (toolSupport) permittedGroups else
                ((setOf(ToolGroup.Memory, ToolGroup.Pat, ToolGroup.Messages) intersect s.tools) +
                    (if (ToolGroup.Speak in s.tools && Speech.ready(s)) setOf(ToolGroup.Speak) else emptySet())),''',
"group prompt indicates enabled social and speaking tools for every role")
patch(chat,
'''            val toolPool = (if (toolSupport) tools.specs(permittedGroups) +
                externalTools.map { it.spec } else speechOnly +
                    (if (ToolGroup.Memory in s.tools) tools.specs(setOf(ToolGroup.Memory)) else emptyList())) +
''',
'''            val toolPool = (if (toolSupport) tools.specs(permittedGroups) +
                externalTools.map { it.spec } else speechOnly +
                    (if (ToolGroup.Memory in s.tools) tools.specs(setOf(ToolGroup.Memory)) else emptyList()) +
                    (if (ToolGroup.Pat in s.tools) tools.specs(setOf(ToolGroup.Pat)) else emptyList())) +
''',
"group permitted pat tool for additional speakers")

# Avoid repeated pats within a single group speaker's current tool cycle.
patch(chat,
'''        var toolRounds = 0
        val recap = db.conversations().get(conversationId)?.recap''',
'''        var toolRounds = 0
        var patUsedThisTurn = false
        val recap = db.conversations().get(conversationId)?.recap''',
"bounded social action counter")
patch(chat,
'''                                call.name in offeredNames -> {
                                    val external = externalTools.firstOrNull { it.fnName == call.name }''',
'''                                call.name == ToolSpecs.patUser.name && call.name in offeredNames &&
                                    patUsedThisTurn ->
                                    ToolOutcome("这个角色本轮已经拍过对方，请用文字继续交流。", "本轮拍一拍次数已达上限")
                                call.name in offeredNames -> {
                                    if (call.name == ToolSpecs.patUser.name) patUsedThisTurn = true
                                    val external = externalTools.firstOrNull { it.fnName == call.name }''',
"limit each group speaker pat action")

out = root / "app/src/test/java/com/cleo/cleos/data/GroupPatSpeakerTest.kt"
out.parent.mkdir(parents=True, exist_ok=True)
copyfile(here / "GroupPatSpeakerTest.kt", out)
print("0.37.36 group AI pats: caller ID propagated, stored and bounded")
