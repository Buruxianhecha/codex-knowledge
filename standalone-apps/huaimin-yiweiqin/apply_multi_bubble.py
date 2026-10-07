"""Deliver ordinary assistant sentences as individually persisted, paced chat messages."""
from pathlib import Path
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve()
HERE = Path(__file__).resolve().parent


def replace(rel, old, new):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected exactly one sentence-delivery target, found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def between(rel, start, end, new):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(end) != 1:
        raise SystemExit(f"{rel}: ambiguous sentence-delivery block")
    first = text.index(start)
    last = text.index(end, first)
    path.write_text(text[:first] + new + text[last:], encoding="utf-8")


for name in ("AssistantBubbleSplitter", "AssistantBubbleDelivery"):
    for source_dir, target_dir, suffix in (
        ("src", "main", ""), ("tests", "test", "Test"),
    ):
        target = ROOT / f"app/src/{target_dir}/java/com/cleo/cleos/ai/{name}{suffix}.kt"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(HERE / source_dir / f"{name}{suffix}.kt", target)

replace(
    "app/src/main/java/com/cleo/cleos/ai/Prompt.kt",
    '            add("想分成几条消息说的时候，用 send_message 一条一条发：一条只说一件事，要发几条就在同一次回复里调用几次。只说一句就直接回复。用 send_message 发过的话，别再在回复里写一遍，也别说「发好了」。")\n',
    '            add("像人聊天一样一句接着一句说：普通聊天有几句独立的话，就分几条真实消息，优先在同一次回复里逐次调用 send_message，一次调用只写一句。对方要求分三次发时就发三条。不要把几句话塞在一次调用里，也不要用空行假装分开发。应用会在每条间短暂停顿，你不需要说等待或发好了。用工具发过的内容别在普通回复里重复。代码、列表、表格等需要整体阅读的内容保持完整。")\n',
)
repo = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"

# Buffer network tokens: an entire response must never flash in one live bubble first.
replace(
    repo,
    '                        live(StreamingReply(conversationId, text.toString(), thinking = false, thought = thoughtShown, thoughtMs = thinkingMs))',
    '                        live(StreamingReply(conversationId, "", thinking = false, thought = thoughtShown, thoughtMs = thinkingMs))',
)
replace(
    repo,
    '        class Called(val message: ApiMessage, val savedId: Long?, val thought: MessageThought? = null) : Step',
    '        class Called(val message: ApiMessage, val savedId: Long?, val thought: MessageThought? = null, val requestedBubbles: Int? = null) : Step',
)

# Only each database insert is atomic. Inter-message delays must remain cancellable.
between(
    repo,
    '        return withContext(NonCancellable) {\n            val shown = thought()\n',
    '\n    }\n\n    /**\n     * Runs the calls, storing each result with its line for the chat.',
    '''        val shown = thought()
        val requested = if (wake) null else AssistantBubbleSplitter.requestedCount(messages.lastOrNull { it.role == "user" }?.content)
        return if (error == null && calls.isNotEmpty()) {
            val body = text.toString()
            val sentBack = reasoning.toString().ifEmpty { null }
            val speaks = calls.any { it.name in ToolSpecs.speaking }
            val id = if (speaks && body.isBlank()) null else storeAssistantBubbles(
                conversationId,
                if (body.isBlank()) listOf(body) else AssistantBubbleSplitter.split(body, MAX_MESSAGES, requested),
                shown?.let(MessageThoughts::encode),
                proactive = wake,
                quiet = wake,
                toolCalls = calls.takeUnless { speaks }.orEmpty(),
                reasoning = sentBack.takeUnless { speaks },
            )
            Step.Called(ApiMessage("assistant", body, calls, reasoning = sentBack), savedId = id, thought = shown.takeIf { id == null }, requestedBubbles = requested)
        } else if (wake) {
            Step.Said(text.toString(), error, shown)
        } else {
            finish(conversationId, startedAt, text.toString(), error, keepEmpty = true, shown, requested)
            Step.Ended(ok = error == null)
        }''',
)

replace(repo, '                        delay((400L + it.length * 25L).coerceAtMost(1500L))',
        '                        delay(AssistantBubbleDelivery.gapAfter(it))')

# A model can put three sentences in a single send_message tool call too.
between(
    repo,
    '                    withContext(NonCancellable) {\n                        val at = System.currentTimeMillis()\n                        db.messages().insert(\n                            MessageEntity(\n                                conversationId = conversationId,\n                                role = "assistant",\n                                content = words,',
    '\n                    thought = null\n',
    '''                    val parts = if (call.name == ToolSpecs.sendMessage.name && inCall == null) {
                        AssistantBubbleSplitter.split(words, MAX_MESSAGES - sent,
                            step.requestedBubbles.takeIf { sends.size == 1 && said.isBlank() })
                    } else listOf(words)
                    storeAssistantBubbles(
                        conversationId, parts, thought, quote, voice,
                        proactive = wake, inCall = inCall, quiet = quiet,
                    )''',
)
replace(repo, '                    sent++\n', '                    sent += parts.size\n')

# Tool activity shows only typing/activity; never re-show a combined preamble.
path = ROOT / repo
text = path.read_text(encoding="utf-8")
first = text.index("    private suspend fun runTools(")
last = text.index("    private suspend fun runOutside(", first)
section = text[first:last]
old = 'StreamingReply(conversationId, said, thinking = false, savedId = step.savedId.takeIf { said.isNotEmpty() }'
if section.count(old) != 2:
    raise SystemExit("Expected two tool-message live rows")
section = section.replace(old, 'StreamingReply(conversationId, "", thinking = false')
section = section.replace('                said,\n                thinking = false,\n                savedId = step.savedId.takeIf { said.isNotEmpty() },',
                          '                "",\n                thinking = false,')
path.write_text(text[:first] + section + text[last:], encoding="utf-8")

between(
    repo,
    '    private suspend fun finish(\n',
    '    private suspend fun note(',
    '''    /** Each bubble is its own database message; all speaking paths share this pacing. */
    private suspend fun storeAssistantBubbles(
        conversationId: Long,
        parts: List<String>,
        thought: String? = null,
        quote: MessageQuote? = null,
        voice: MessageAudio? = null,
        proactive: Boolean = false,
        inCall: Long? = null,
        quiet: Boolean = false,
        toolCalls: List<ToolCall> = emptyList(),
        reasoning: String? = null,
    ): Long? {
        var savedId: Long? = null
        AssistantBubbleDelivery.deliver(
            parts,
            waiting = { if (!quiet) show(StreamingReply(conversationId, "", thinking = false)) },
        ) { index, words ->
            // A stop can prevent the next message, but never half-save the one already sent.
            withContext(NonCancellable) {
                val at = System.currentTimeMillis()
                savedId = db.messages().insert(
                    MessageEntity(
                        conversationId = conversationId,
                        role = "assistant",
                        content = words,
                        createdAt = at,
                        thought = thought.takeIf { index == 0 },
                        quote = quote?.takeIf { index == 0 }?.let(MessageQuotes::encode),
                        audio = voice?.takeIf { index == 0 }?.let(MessageAudios::encode),
                        proactive = proactive,
                        call = inCall,
                        toolCalls = toolCalls.takeIf { index == parts.lastIndex && it.isNotEmpty() }?.let(ToolCallCodec::encode),
                        reasoning = reasoning.takeIf { index == parts.lastIndex },
                    ),
                )
                db.conversations().touch(conversationId, at)
            }
            if (!quiet) hide(conversationId)
        }
        return savedId
    }

    private suspend fun finish(
        conversationId: Long,
        startedAt: Long,
        body: String,
        error: String?,
        keepEmpty: Boolean,
        thought: MessageThought? = null,
        requestedBubbles: Int? = null,
    ) {
        if (error == null) {
            storeAssistantBubbles(conversationId, AssistantBubbleSplitter.split(body, MAX_MESSAGES, requestedBubbles), thought?.let(MessageThoughts::encode))
        } else if (body.isNotEmpty() || keepEmpty) {
            withContext(NonCancellable) {
                db.messages().insert(
                    MessageEntity(
                        conversationId = conversationId,
                        role = "assistant",
                        content = body,
                        createdAt = startedAt,
                        error = error,
                        thought = thought?.let(MessageThoughts::encode),
                    ),
                )
                db.conversations().touch(conversationId, System.currentTimeMillis())
            }
        }
        hide(conversationId)
    }

''',
)
# Proactive text shares the same storage path; SKIP was checked before this block.
between(
    repo,
    '                        // A model that doesn\'t send messages through the tool says it in plain words.\n',
    '                        return result("")\n',
    '''                        storeAssistantBubbles(
                            conversationId, AssistantBubbleSplitter.split(text, MAX_MESSAGES),
                            step.thought?.let(MessageThoughts::encode), proactive = true, quiet = true,
                        )
''',
)
print("Sentence-by-sentence paced message delivery applied.")
