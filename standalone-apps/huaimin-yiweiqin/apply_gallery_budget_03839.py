#!/usr/bin/env python3
"""0.38.39: an independent 2048-call gallery/avatar budget for all AI reply modes."""
from pathlib import Path
from shutil import copyfile
import sys
root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

def replace_one(path, old, new):
    data = path.read_text(encoding="utf-8")
    count = data.count(old)
    if count != 1:
        raise RuntimeError(f"0.38.39 patch precondition: {path.name}: {old[:95]!r} found {count}, expected 1")
    path.write_text(data.replace(old,new,1),encoding="utf-8")

ai = root / "app/src/main/java/com/cleo/cleos/ai"
repo = ai / "ChatRepository.kt"
copyfile(here / "GalleryToolBudget_03839.kt", ai / "GalleryToolBudget.kt")
test = root / "app/src/test/java/com/cleo/cleos/ai/GalleryToolBudgetTest.kt"
test.parent.mkdir(parents=True, exist_ok=True)
copyfile(here / "GalleryToolBudgetTest_03839.kt", test)

replace_one(repo, """        val said = StringBuilder()
        var rounds = 0
        while (true) {""",
            """        val said = StringBuilder()
        var rounds = 0
        val galleryBudget = GalleryToolBudget(ordinaryRoundLimit = MAX_TOOL_ROUNDS)
        while (true) {""")
replace_one(repo, """                    if (rounds == MAX_TOOL_ROUNDS) return said.toString()""",
            """                    if (!galleryBudget.accept(step.message.toolCalls)) {
                        note(conversationId, galleryBudget.lastError)
                        return said.toString()
                    }""")

replace_one(repo, """        var rounds = 0
        suspend fun sent() = db.messages().proactiveSince(conversationId, since)""",
            """        var rounds = 0
        val galleryBudget = GalleryToolBudget(ordinaryRoundLimit = MAX_TOOL_ROUNDS)
        suspend fun sent() = db.messages().proactiveSince(conversationId, since)""")
replace_one(repo, """                        if (rounds == MAX_TOOL_ROUNDS) return result("连着用了太多次工具")""",
            """                        if (!galleryBudget.accept(step.message.toolCalls)) return result(galleryBudget.lastError)""")

replace_one(repo, """        var toolRounds = 0
        var patUsedThisTurn = false""",
            """        var toolRounds = 0
        val galleryBudget = GalleryToolBudget(ordinaryRoundLimit = 3)
        var patUsedThisTurn = false""")
replace_one(repo, """                    if (toolRounds >= 3) {
                        note(conversationId, "群聊本轮工具调用次数已达到安全上限。")
                        return false
                    }""",
            """                    if (!galleryBudget.accept(step.message.toolCalls)) {
                        note(conversationId, galleryBudget.lastError)
                        return false
                    }""")

replace_one(repo, """            var messages = prepare(build())
            var rounds = 0
            // What the last refusal made this reply leave out, and when.""",
            """            var messages = prepare(build())
            var rounds = 0
            val galleryBudget = GalleryToolBudget(ordinaryRoundLimit = MAX_TOOL_ROUNDS)
            // What the last refusal made this reply leave out, and when.""")
replace_one(repo, """                        if (rounds == MAX_TOOL_ROUNDS) {
                            note(conversationId, TOO_MANY_ROUNDS)
                            hide(conversationId)
                            return
                        }""",
            """                        if (!galleryBudget.accept(step.message.toolCalls)) {
                            note(conversationId, galleryBudget.lastError)
                            hide(conversationId)
                            return
                        }""")

gradle = root / "app/build.gradle.kts"
replace_one(gradle, 'versionName = "0.38.38"', 'versionName = "0.38.39"')
replace_one(gradle, "versionCode = 62104", "versionCode = 62105")
print("0.38.39: 2048 gallery/avatar calls per reply, ordinary calls bounded, duplicate loops blocked")
