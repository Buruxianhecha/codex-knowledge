#!/usr/bin/env python3
"""0.38.43 web image: ensure real message persistence and all prior avatar functions."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
ai=r/"app/src/main/java/com/cleo/cleos/ai"
web=(ai/"OnlineAvatar.kt").read_text(encoding="utf-8")
tools=(ai/"Tools.kt").read_text(encoding="utf-8")
chat=(ai/"ChatRepository.kt").read_text(encoding="utf-8")
prompt=(ai/"Prompt.kt").read_text(encoding="utf-8")
budget=(ai/"GalleryToolBudget.kt").read_text(encoding="utf-8")
test=(r/"app/src/test/java/com/cleo/cleos/ai/OnlineWebChatRulesTest.kt")
assert web.encode("utf-8")== (here/"OnlineAvatar_03843.kt").read_bytes()
assert test.read_bytes()== (here/"OnlineWebChatRulesTest_03843.kt").read_bytes()
assert test.read_text().count("@Test fun ")==8
for token in (
    "data class OnlineChatImage(", "suspend fun importForChat(imageUrl: String): OnlineChatImage",
    "override suspend fun importForChat(imageUrl: String): OnlineChatImage",
    "readImageBytes(imageUrl, 8_000_000)", "decodeImage(bytes, 2048)",
    'prefix = "ai-web-chat-"', "bitmap.recycle()", "OnlineAvatarRules.safeHttpsUrl(imageUrl)",
    "followRedirects(false)", "Restricted network address",
):
    assert token in web, f"safe media import missing: {token}"
for token in (
    'name = "send_web_image"', "groups = setOf(ToolGroup.Avatar, ToolGroup.Gallery)",
    "ToolSpecs.sendWebImage.name -> sendWebImage(args)",
    "webSendUrls: List<String> = emptyList()",
    "private fun sendWebImage(a: JsonObject): ToolOutcome",
    "suspend fun importWebImageForChat(url: String): OnlineChatImage",
    "webSendUrls = listOf(url)",
):
    assert token in tools, f"network image tool missing: {token}"
for token in (
    "private suspend fun persistWebImages(",
    "tools.importWebImageForChat(url)",
    "MessageImages.encode(imported)",
    "db.messages().insert(MessageEntity(",
    'role = "assistant"', "senderCompanionId = companionId",
    "outcome = persistWebImages(conversationId, companionId, outcome.webSendUrls, wake, inCall)",
    "outcome = persistWebImages(conversationId, ta.id, outcome.webSendUrls,",
    "images.delete(imported.map { it.file })",
    "网络图片没有发出去，不能声称成功",
):
    assert token in chat, f"actual image bubble insert missing: {token}"
assert '"send_web_image"' in budget
assert "不能把换头像成功说成图片已发送" in prompt
assert "发送到聊天用 send_web_image" in prompt
for old in ('search_web_avatar_images', 'set_my_avatar', 'send_gallery_photos', 'set_my_name', 'play_qq_music', 'play_kugou_music'):
    assert old in tools, f"previous tool lost: {old}"
assert "PersonalityGrowthRules.forPrompt(" in chat
assert "ChatLocationCard(" in (r/"app/src/main/java/com/cleo/cleos/ui/chat/ChatLocation.kt").read_text()
gradle=(r/"app/build.gradle.kts").read_text()
assert 'versionName = "0.38.43"' in gradle and "versionCode = 62109" in gradle
print("PASS: secure downloaded picture, real chat image bubbles in private/group, truthful results, old features")
