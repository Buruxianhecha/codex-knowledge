#!/usr/bin/env python3
"""v0.37.35: group replies share single-chat prompt memory, event and tool paths.

Input is the pinned Cleos tree after all patches up to v0.37.34.
All edits are exact-once with failing assertions: do not silently patch another baseline.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
repo = root / "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
view = root / "app/src/main/java/com/cleo/cleos/ui/chat/ChatViewModel.kt"
group_file = root / "app/src/main/java/com/cleo/cleos/ai/GroupChats.kt"
code = repo.read_text(encoding="utf-8")

def once(src, old, new, why):
    count = src.count(old)
    if count != 1:
        raise SystemExit(f"{why}: expected exactly one anchor, found {count}: {old[:100]!r}")
    return src.replace(old, new, 1)

# Events already wake the per-conversation reply coordinator, but groupReply
# previously dropped the event before selecting a speaker.
code = once(code,
'''            (m.role == "user" && m.note == null && m.error == null) ||
                (m.role == "pat" && Pats.decode(m.content)?.who == Pats.AI)
''',
'''            (m.role == "user" && m.note == null && m.error == null) ||
                (m.role == "pat" && Pats.decode(m.content)?.who == Pats.AI) ||
                Recalls.isEvent(m) || ReactionEvents.isEvent(m)
''', "group event trigger")

code = once(code,
'''        val isQuestion = !continued && GroupTurnRecovery.isQuestion(latestText)
''',
'''        val isQuestion = !continued && GroupTurnRecovery.isQuestion(latestText)
        val eventTurn = !continued && trigger?.let {
            Recalls.isEvent(it) || ReactionEvents.isEvent(it)
        } == true
        // A user action is not permission for an autonomous continuation to operate the phone.
        val allowToolCalls = !continued && trigger?.role == "user"
        val groupListening = if (ToolGroup.Music in s.tools)
            runCatching { listening() }.getOrNull() else null
''', "group event / tool authorization")

code = once(code,
'''            conversation.groupMode == 1 -> eligible
            else -> naturalOrder.filter { it.id !in muted }.take(GroupChats.MAX_OPPORTUNITIES)
''',
'''            conversation.groupMode == 1 -> eligible
            eventTurn -> naturalOrder.filter { it.id !in muted }.take(1)
            else -> naturalOrder.filter { it.id !in muted }.take(GroupChats.MAX_OPPORTUNITIES)
''', "events should not wake the whole room")

code = once(code,
'''                    historyRequested = !continued && ChatHistorySearch.wanted(latestText))) said++
''',
'''                    historyRequested = !continued && ChatHistorySearch.wanted(latestText),
                    allowToolCalls = allowToolCalls,
                    currentMusic = groupListening)) said++
''', "group turn authorization")
code = once(code,
'''        if (said == 0 && candidates.isNotEmpty() &&
            (mentions.isNotEmpty() || isQuestion || groupLocation != null || groupWeather != null)) {
''',
'''        // Keep a real group-level rolling recap as the conversation exceeds the live window.
        // The recap is only ever read by participants in this same group.
        recaps.foldLater(conversationId)
        if (said == 0 && candidates.isNotEmpty() &&
            (mentions.isNotEmpty() || isQuestion || eventTurn || groupLocation != null || groupWeather != null)) {
''', "group recap fold")

# Work only in the group-specific model path, leaving normal 1:1 tool behavior unchanged.
start = code.index("    private suspend fun groupTurn(")
end = code.index("    private suspend fun reply(conversationId: Long)", start)
body = code[start:end]
body = once(body,
'''        historyRequested: Boolean = false,
    ): Boolean {
''',
'''        historyRequested: Boolean = false,
        allowToolCalls: Boolean = false,
        currentMusic: String? = null,
    ): Boolean {
''', "group turn parameters")
body = once(body,
'''        val (stickers, _) = stickersFor(s)
''',
'''        val (stickers, sendStickers) = stickersFor(s)
''', "group sticker settings")
body = once(body,
'''        var historyTools = historyRequested && endpointKey !in refusesTools &&
            db.conversations().get(conversationId)?.groupShareOutside == true
        var historyRounds = 0
''',
'''        var toolSupport = allowToolCalls && endpointKey !in refusesTools
        var historyTools = historyRequested && endpointKey !in refusesTools &&
            db.conversations().get(conversationId)?.groupShareOutside == true
        var toolRounds = 0
        val recap = db.conversations().get(conversationId)?.recap
        // Use the exact per-character permissions and model, not the group's owner.
        // Location/weather are handled once per explicit group request before model turns.
        // Later is deliberately excluded: a group reply must not schedule autonomous wakes.
        val permittedGroups = groupsFor(s, ta) -
            setOf(ToolGroup.Location, ToolGroup.Weather, ToolGroup.Later)
        val externalTools = if (toolSupport) mcp.tools() else emptyList()
''', "group permission state")
body = once(body,
'''        fun build() = Prompt.messages(
            s, ta, history, ZonedDateTime.now(), tools = emptySet(), images = withImages, memories = memories,
            recap = null, stickers = stickers, sendStickers = false,
''',
'''        fun build() = Prompt.messages(
            s, ta, history, ZonedDateTime.now(),
            tools = if (toolSupport) permittedGroups else
                if (ToolGroup.Memory in s.tools) setOf(ToolGroup.Memory) else emptySet(),
            images = withImages, memories = memories,
            recap = recap, outside = if (toolSupport) externalTools else emptyList(),
            listening = currentMusic, stickers = stickers,
            sendStickers = toolSupport && sendStickers,
''', "group memory and recap into prompt")
body = once(body,
'''            val groupSpecs = if (historyTools) listOf(ToolSpecs.searchChatHistory, ToolSpecs.readChatMessage) else emptyList()
            when (val step = step(conversationId, endpoint, messages, groupSpecs,
''',
'''            val groupSpecs = (if (toolSupport) tools.specs(permittedGroups) +
                externalTools.map { it.spec } else emptyList()) +
                (if (historyTools) listOf(ToolSpecs.searchChatHistory, ToolSpecs.readChatMessage) else emptyList())
            when (val step = step(conversationId, endpoint, messages, groupSpecs,
''', "group tool specifications")
body = once(body,
'''                thinking = thinking, showThought = ta.deepThinking, wake = true)) {
''',
'''                thinking = thinking, showThought = ta.deepThinking, wake = true,
                speakerCompanionId = ta.id)) {
''', "group tool-call speaker id")
body = once(body,
'''                    } else if (historyTools) {
                        historyTools = false
                        refusesTools += endpointKey
                    } else return false
''',
'''                    } else if (toolSupport || historyTools) {
                        toolSupport = false
                        historyTools = false
                        refusesTools += endpointKey
                    } else return false
''', "group tool downgrade")
begin_called = body.index("                is Step.Called -> {")
end_called = body.index("                is Step.Ended -> return false",begin_called)
called = '''                is Step.Called -> {
                    if (toolRounds >= 3) {
                        note(conversationId, "群聊本轮工具调用次数已达到安全上限。")
                        return false
                    }
                    val offeredNames = groupSpecs.map { it.name }.toSet()
                    var spoke = false
                    val results = mutableListOf<ApiMessage>()
                    for (call in step.message.toolCalls) {
                        currentCoroutineContext().ensureActive()
                        val outcome = try {
                            when {
                                call.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name) &&
                                    historyTools && call.name in offeredNames ->
                                    searchHistoryCall(conversationId, call)
                                call.name in ToolSpecs.speaking && call.name in offeredNames -> {
                                    val args = ToolArgs.parse(call.arguments)
                                    val words = args?.let { ToolArgs.text(it, "text") }?.trim().orEmpty()
                                    if (words.isBlank()) {
                                        ToolOutcome("没有文本，不能发出消息。", "消息内容为空")
                                    } else {
                                        val voice = if (call.name == ToolSpecs.sendVoice.name) {
                                            val selectedVoice = ta.speechVoiceOverride?.trim().orEmpty()
                                            val voiceSettings = if (selectedVoice.isEmpty()) s else
                                                s.copy(speechVoices = s.speechVoices +
                                                    (s.speechEngine to selectedVoice), speechVoice = selectedVoice)
                                            try {
                                                speaker.speak(voiceSettings, words)
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                note(conversationId, "语音生成失败，已改为文字：" +
                                                    (e.message ?: e.javaClass.simpleName))
                                                null
                                            }
                                        } else null
                                        storeAssistantBubbles(conversationId,
                                            if (voice == null) AssistantBubbleSplitter.split(
                                                words, GroupChats.MAX_BUBBLES, null) else listOf(words),
                                            voice = voice, quiet = true, speakerCompanionId = ta.id)
                                        spoke = true
                                        ToolOutcome(ToolSpecs.SENT, "")
                                    }
                                }
                                call.name in offeredNames -> {
                                    val external = externalTools.firstOrNull { it.fnName == call.name }
                                    if (external != null) {
                                        val live = StreamingReply(conversationId, "", thinking = false,
                                            activity = "在用" + external.serverName)
                                        runOutside(conversationId, external, call, live, ta.name)
                                    } else {
                                        // Permissions may have been turned off while the model was thinking.
                                        tools.run(call, settings.current().copy(
                                            tools = settings.current().tools intersect permittedGroups),
                                            conversationId, ta.id)
                                    }
                                }
                                else -> ToolOutcome("本轮未授权此工具，未执行。", "群聊拒绝未提供的工具")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ToolOutcome("调用失败：" + (e.message ?: e.javaClass.simpleName),
                                "群聊工具调用出错")
                        }
                        withContext(NonCancellable) {
                            val at = stamp()
                            db.messages().insert(MessageEntity(
                                conversationId = conversationId, role = "tool", content = outcome.result,
                                toolCallId = call.id, createdAt = at, note = outcome.note))
                            db.conversations().touch(conversationId, at)
                        }
                        results += ApiMessage("tool", outcome.result, toolCallId = call.id)
                    }
                    if (step.message.toolCalls.isNotEmpty() &&
                        step.message.toolCalls.all { it.name in ToolSpecs.speaking }) return spoke
                    messages = messages + step.message + results
                    toolRounds++
                }
'''
body = body[:begin_called] + called + body[end_called:]
code = code[:start] + body + code[end:]

# The shared step() path stores the assistant's text before tool execution.
# Preserve a group's real speaker here; it must not become an anonymous bubble.
code = once(code,
'''        showThought: Boolean,
        wake: Boolean = false,
    ): Step {
''',
'''        showThought: Boolean,
        wake: Boolean = false,
        speakerCompanionId: Long? = null,
    ): Step {
''', "step speaker parameter")
code = once(code,
'''                reasoning = sentBack.takeUnless { speaks },
            )
            Step.Called(''',
'''                reasoning = sentBack.takeUnless { speaks },
                speakerCompanionId = speakerCompanionId,
            )
            Step.Called(''', "step saved message role")
repo.write_text(code, encoding="utf-8")

# Show the actual conversation-level recap in groups rather than always hiding it.
ui = view.read_text(encoding="utf-8")
ui = once(ui,
'''                recap = if (conversation?.isGroup == true) null else conversation?.recap,
''',
'''                recap = conversation?.recap,
''', "group recap UI")
view.write_text(ui, encoding="utf-8")

rules = group_file.read_text(encoding="utf-8")
rules = once(rules, "const val MAX_BUBBLES = 2", "const val MAX_BUBBLES = 3", "three paced group bubbles")
rules = once(rules,
    "一次最多说两小句，需要分开发时最多两条真实消息。",
    "一次最多说三小句，需要分开发时最多三条真实消息，各条之间自然短暂停顿。", "group tone")
rules = once(rules, "保持你自己的完整人格、记忆、态度和关系，",
    "保持你自己的完整人格、记忆、态度和关系，群聊前情提要只用于回忆本群，不能将别人的话认作自己的，", "group recap identity")
group_file.write_text(rules, encoding="utf-8")
print("0.37.35 group parity patch applied: event triggers, recap, memories, tools, MCP, role voice and speaker identity")
