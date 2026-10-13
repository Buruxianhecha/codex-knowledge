#!/usr/bin/env python3
"""Incremental patch: source-aware analysis for modest HTML and text, including saved files."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
home=Path(__file__).resolve().parent
src=root/"app/src/main/java/com/cleo/cleos"
tests=root/"app/src/test/java/com/cleo/cleos"
ver=root/"app/build.gradle.kts"
v=ver.read_text(encoding="utf-8")
assert v.count('versionName = "0.38.57"')==1
assert v.count("versionCode = 62123")==1

def edit(rel,pairs):
    f=src/rel
    t=f.read_text(encoding="utf-8")
    for old,new in pairs:
        if t.count(old)!=1:
            raise RuntimeError(f"Expected one occurrence, got {t.count(old)} in {rel}: {old[:120]}")
        t=t.replace(old,new,1)
    f.write_text(t,encoding="utf-8")

for name,dest in [
    ("ChatFileTransfer_03858.kt",src/"data/ChatFileTransfer.kt"),
    ("DocumentInsightExtractor_03858.kt",src/"data/DocumentInsightExtractor.kt"),
    ("ChatFileTransferTest_03858.kt",tests/"data/ChatFileTransferTest.kt")
]:
    assert (home/name).is_file(),name
    dest.write_bytes((home/name).read_bytes())

edit("ai/Prompt.kt",[
    ('import com.cleo.cleos.data.ChatFileTransfer',
     'import com.cleo.cleos.data.ChatFileTransfer\nimport java.io.File'),
    ('        extraContext: String? = null,\n    ): List<ApiMessage> {',
     '        extraContext: String? = null,\n        documentResolver: ((String) -> File?)? = null,\n    ): List<ApiMessage> {'),
    ('        val reacted = reactions(history)',
     '        val reacted = reactions(history)\n'
     '        val recentFileId = history.asReversed().take(14).firstOrNull {\n'
     '            it.role == "user" && ChatFileTransfer.decode(it.content) != null\n'
     '        }?.id'),
    ('            m.toApi(withTools, images, attached, words, reacted, stickers)?.let',
     '            m.toApi(withTools, images, attached, words, reacted, stickers,\n'
     '                documentResolver, m.id == recentFileId)?.let'),
    ('private fun MessageEntity.userText(canSee: Boolean, attached: Boolean, words: (String) -> String, stickers: StickerBook): String {',
     'private fun MessageEntity.userText(canSee: Boolean, attached: Boolean, words: (String) -> String,\n'
     '        stickers: StickerBook, original: ((String)->File?)?, preferComplete: Boolean): String {'),
    ('        val text = words(ChatFileTransfer.forModel(content))',
     '        val text = words(ChatFileTransfer.forModel(content, original, preferComplete))'),
    ('        reacted: Map<Long, List<String>>,\n        stickers: StickerBook,\n    ): ApiMessage? = when (role) {',
     '        reacted: Map<Long, List<String>>,\n        stickers: StickerBook,\n'
     '        original: ((String)->File?)?, preferComplete: Boolean,\n'
     '    ): ApiMessage? = when (role) {'),
    ('            userText(canSee, attach, words, stickers).takeIf',
     '            userText(canSee, attach, words, stickers, original, preferComplete).takeIf')
])
edit("ai/ChatRepository.kt",[
    ('            extraContext=listOfNotNull(store.context(goal),\n'
     '                life?.context(ta.id, goal.description)).joinToString("\\n\\n"),',
     '            extraContext=listOfNotNull(store.context(goal),\n'
     '                life?.context(ta.id, goal.description)).joinToString("\\n\\n"),\n'
     '            documentResolver = { fileName -> images.file(fileName) },'),
    ('            extraContext = life?.context(ta.id, history.lastOrNull { it.role == "user" }?.content.orEmpty()),',
     '            extraContext = life?.context(ta.id, history.lastOrNull { it.role == "user" }?.content.orEmpty()),\n'
     '            documentResolver = { fileName -> images.file(fileName) },'),
    ('                extraContext = life?.context(ta.id, instruction)),',
     '                extraContext = life?.context(ta.id, instruction),\n'
     '                documentResolver = { fileName -> images.file(fileName) }),'),
    ('                (if (retryTargeted) "\\n\\n用户已经明确提问或 @，上次没有发出有效文本。请直接回应，不要再写 SKIP。" else ""),',
     '                (if (retryTargeted) "\\n\\n用户已经明确提问或 @，上次没有发出有效文本。请直接回应，不要再写 SKIP。" else ""),\n'
     '            documentResolver = { fileName -> images.file(fileName) },'),
    ('                    .joinToString("\\n\\n").ifBlank { null },',
     '                    .joinToString("\\n\\n").ifBlank { null },\n'
     '                documentResolver = { fileName -> images.file(fileName) },'),
    ('                extraContext = listOfNotNull(turnRule, life?.context(ta.id, latestText))\n'
     '                    .joinToString("\\n\\n"))',
     '                extraContext = listOfNotNull(turnRule, life?.context(ta.id, latestText))\n'
     '                    .joinToString("\\n\\n"),\n'
     '                documentResolver = { fileName -> images.file(fileName) })')
])

assert "completeSmallSource(" in (src/"data/DocumentInsightExtractor.kt").read_text()
assert "documentResolver" in (src/"ai/Prompt.kt").read_text()
assert (tests/"data/ChatFileTransferTest.kt").read_text().count("@Test ")==15
ver.write_text(v.replace('versionName = "0.38.57"','versionName = "0.38.58"',1)
    .replace("versionCode = 62123","versionCode = 62124",1),encoding="utf-8")
print("0.38.58 Build 62124: complete modest HTML source for new and existing attachments")
