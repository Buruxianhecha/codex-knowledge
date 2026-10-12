#!/usr/bin/env python3
"""Regression verify: 0.38.39 large gallery budget, true gallery-ID avatar, old features intact."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
ai=r/"app/src/main/java/com/cleo/cleos/ai"
budget=(ai/"GalleryToolBudget.kt").read_text(encoding="utf-8")
repo=(ai/"ChatRepository.kt").read_text(encoding="utf-8")
tools=(ai/"Tools.kt").read_text(encoding="utf-8")
prompt=(ai/"Prompt.kt").read_text(encoding="utf-8")
app=(r/"app/src/main/java/com/cleo/cleos/CleosApp.kt").read_text(encoding="utf-8")
test=(r/"app/src/test/java/com/cleo/cleos/ai/GalleryToolBudgetTest.kt").read_text(encoding="utf-8")
assert budget.encode("utf-8")== (here/"GalleryToolBudget_03839.kt").read_bytes()
assert test.encode("utf-8")== (here/"GalleryToolBudgetTest_03839.kt").read_bytes()
assert test.count("@Test fun ")==12, "GalleryToolBudgetTest needs 12 passing JUnit tests"
for marker in (
    "galleryCallLimit: Int = 2048",
    "duplicateRoundLimit: Int = 4",
    "fun acceptCalls(", "fun accept(calls: List<ToolCall>)",
    "list_gallery_photos", "count_gallery_photos", "inspect_gallery_photos",
    "send_gallery_photos", "prepare_gallery_cleanup", "set_my_avatar",
    "ordinaryRounds++", "galleryCalls += gallery.size",
):
    assert marker in budget, "Gallery budget regression: "+marker
assert repo.count("GalleryToolBudget(")>=4, "All normal/group/wake/phone flows need separate budget"
assert repo.count("galleryBudget.accept(step.message.toolCalls)")>=4, "Per-round guard missing"
assert "if (rounds == MAX_TOOL_ROUNDS)" not in repo
assert "if (toolRounds >= 3)" not in repo
for marker in (
    '"gallery_id" to prop("integer"',
    'ToolArgs.id(a["gallery_id"])',
    "userGallery().sendablePhotos(listOf(galleryId))",
    "if (galleryId !in matching)",
    "update(companionId, galleryId)",
    'coerceIn(0,1000000)',
):
    assert marker in tools, "Gallery avatar ID wiring or pagination missing: "+marker
assert "galleryAvatar = { actor, photoId ->" in app
assert 'prefix = "ai-gallery-avatar-"' in app
assert "usePicture(actor, stored.file)" in app
assert "gallery_id" in prompt
assert "latest 只指对方" in prompt
assert 'versionName = "0.38.39"' in (r/"app/build.gradle.kts").read_text()
assert "versionCode = 62105" in (r/"app/build.gradle.kts").read_text()
assert "set_my_name" in tools and "send_gallery_photos" in tools
assert "play_qq_music" in tools and "play_kugou_music" in tools
assert "ChatLocationCard(" in (r/"app/src/main/java/com/cleo/cleos/ui/chat/ChatLocation.kt").read_text()
print("PASS: 2048 gallery calls, 4-flow independent budgets, authorized gallery avatar, old features")
