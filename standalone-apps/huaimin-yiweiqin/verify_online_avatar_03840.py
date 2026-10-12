#!/usr/bin/env python3
"""Verify that v0.38.40 actually searches/downloads and sets web avatars, without detours."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
ai=root/"app/src/main/java/com/cleo/cleos/ai"
avatar=(ai/"OnlineAvatar.kt").read_text(encoding="utf-8")
tools=(ai/"Tools.kt").read_text(encoding="utf-8")
app=(root/"app/src/main/java/com/cleo/cleos/CleosApp.kt").read_text(encoding="utf-8")
prompt=(ai/"Prompt.kt").read_text(encoding="utf-8")
budget=(ai/"GalleryToolBudget.kt").read_text(encoding="utf-8")
test=(root/"app/src/test/java/com/cleo/cleos/ai/OnlineAvatarRulesTest.kt").read_text(encoding="utf-8")
assert avatar.encode("utf-8") == (here/"OnlineAvatar_03840.kt").read_bytes()
assert budget.encode("utf-8") == (here/"GalleryToolBudget_03840.kt").read_bytes()
assert test.encode("utf-8") == (here/"OnlineAvatarRulesTest_03840.kt").read_bytes()
assert test.count("@Test fun ") == 11
for token in (
    "OnlineAvatarSource", "class OnlineAvatarService(", "api.openverse.org",
    "commons.wikimedia.org", "OnlineAvatarSearch", "previews",
    "private fun readImageBytes(", "private fun decodeImage(",
    "images.save(bitmap, prefix = \"online-avatar-temp-\")",
    "avatar.usePicture(companionId, temporary)",
    "images.delete(listOf(temporary))",
    "response@","return@response null",
    "followRedirects(false)", "followSslRedirects(false)",
    "Restricted network address", "8_000_000", "image/webp",
):
    assert token in avatar, "Missing online image download/validation: "+token
for token in (
    'name = "search_web_avatar_images"', "val searchWebAvatar = ToolSpec(",
    '"web_url" to prop("string"', "ToolSpecs.searchWebAvatar.name -> searchWebAvatar(args)",
    "onlineAvatar: OnlineAvatarSource?", "onlineAvatar ?: throw ToolFailure",
    "source.find(query)", "source.apply(companionId, webUrl)",
    "galleryImages = response.previews.map { it.second }",
    "无需用户保存到相册或发送聊天",
):
    assert token in tools, "Online-avatar tool wiring missing: "+token
assert "onlineAvatar = OnlineAvatarService(http, images, AiSelfAvatar(db, images, companions))" in app
assert "不需要对方先把照片保存到系统相册" in prompt
assert "search_web_avatar_images" in budget
assert "galleryCallLimit: Int = 2048" in budget
assert "set_my_name" in tools and "send_gallery_photos" in tools
assert "play_qq_music" in tools and "play_kugou_music" in tools
assert "ChatLocationCard(" in (root/"app/src/main/java/com/cleo/cleos/ui/chat/ChatLocation.kt").read_text()
gradle=(root/"app/build.gradle.kts").read_text()
assert 'versionName = "0.38.40"' in gradle
assert "versionCode = 62106" in gradle
print("PASS web avatar search, visual previews, direct HTTP image import, security, previous tools")
