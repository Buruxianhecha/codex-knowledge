"""Turn direct multi-paragraph AI replies into real consecutive chat bubbles."""
from pathlib import Path
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve()
HERE = Path(__file__).resolve().parent


def replace(rel: str, old: str, new: str):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected exactly one multi-bubble patch target, found {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


for source_name, destination in (
    ("AssistantBubbleSplitter.kt", ROOT / "app/src/main/java/com/cleo/cleos/ai/AssistantBubbleSplitter.kt"),
    ("AssistantBubbleSplitterTest.kt", ROOT / "app/src/test/java/com/cleo/cleos/ai/AssistantBubbleSplitterTest.kt"),
):
    source = HERE / ("src" if source_name == "AssistantBubbleSplitter.kt" else "tests") / source_name
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)

repo = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
old = """    private suspend fun finish(
        conversationId: Long,
        startedAt: Long,
        body: String,
        error: String?,
        keepEmpty: Boolean,
        thought: MessageThought? = null,
    ) {
        if (body.isNotEmpty() || (keepEmpty && error != null)) {
            val id = db.messages().insert(
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
            // Hand over from the live bubble to the stored one without a gap:
            // the screen hides the live bubble once it sees savedId in its list.
            // The cleanup runs on its own so this job (and `busy`) ends now.
            val handover = StreamingReply(
                conversationId,
                body,
                thinking = false,
                savedId = id,
                finished = true,
                thought = thought?.text.orEmpty(),
                thoughtMs = thought?.ms,
            )
            show(handover)
            scope.launch {
                delay(1500)
                // Unless the next reply here has begun meanwhile.
                _streaming.update { if (it[conversationId] == handover) it - conversationId else it }
            }
        } else {
            hide(conversationId)
        }
    }
"""
new = """    private suspend fun finish(
        conversationId: Long,
        startedAt: Long,
        body: String,
        error: String?,
        keepEmpty: Boolean,
        thought: MessageThought? = null,
    ) {
        if (body.isNotEmpty() || (keepEmpty && error != null)) {
            val bubbles = if (error == null) AssistantBubbleSplitter.split(body, MAX_MESSAGES) else listOf(body)
            if (bubbles.size > 1) {
                // Some models ignore send_message and return one ordinary reply whose paragraphs are
                // separated by blank lines. Store those paragraphs as real messages instead of one
                // giant bubble containing visual spacer lines.
                bubbles.forEachIndexed { index, words ->
                    db.messages().insert(
                        MessageEntity(
                            conversationId = conversationId,
                            role = "assistant",
                            content = words,
                            createdAt = startedAt + index,
                            thought = thought?.takeIf { index == 0 }?.let(MessageThoughts::encode),
                        ),
                    )
                    // Keep the already-streamed full reply visible until the first real bubble exists,
                    // then let the remaining bubbles arrive one after another.
                    if (index == 0) hide(conversationId)
                    if (index < bubbles.lastIndex) delay(MULTI_BUBBLE_DELAY)
                }
                db.conversations().touch(conversationId, System.currentTimeMillis())
                hide(conversationId)
                return
            }

            val id = db.messages().insert(
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
            // Hand over from the live bubble to the stored one without a gap:
            // the screen hides the live bubble once it sees savedId in its list.
            // The cleanup runs on its own so this job (and `busy`) ends now.
            val handover = StreamingReply(
                conversationId,
                body,
                thinking = false,
                savedId = id,
                finished = true,
                thought = thought?.text.orEmpty(),
                thoughtMs = thought?.ms,
            )
            show(handover)
            scope.launch {
                delay(1500)
                // Unless the next reply here has begun meanwhile.
                _streaming.update { if (it[conversationId] == handover) it - conversationId else it }
            }
        } else {
            hide(conversationId)
        }
    }
"""
replace(repo, old, new)

replace(
    repo,
    '        /** How long the TA waits after the person\'s last message before answering: long enough for the next one. */\n        const val REPLY_WAIT = 2_000L\n',
    '''        /** How long the TA waits after the person's last message before answering: long enough for the next one. */
        const val REPLY_WAIT = 2_000L

        /** Small visual gap between fallback bubbles from one plain-text model response. */
        const val MULTI_BUBBLE_DELAY = 280L
''',
)

print("Multi-bubble fallback applied.")
