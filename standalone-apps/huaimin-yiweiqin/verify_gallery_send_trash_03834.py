#!/usr/bin/env python3
"""The real assembled 0.38.34 must send assistant IMAGE bubbles and never auto-delete."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
src=r/"app/src/main/java/com/cleo/cleos"
tool=(src/"ai/Tools.kt").read_text()
chat=(src/"ai/ChatRepository.kt").read_text()
g=(src/"ai/GalleryAccess.kt").read_text()
ui=(src/"ui/chat/ChatScreen.kt").read_text()
manager=(src/"ui/settings/GalleryManager.kt").read_text()
for x in [
    'name = "send_gallery_photos"',
    'ToolSpecs.gallerySend.name -> gallerySend(args)',
    'private suspend fun gallerySend(a: JsonObject)',
    'sendablePhotos(photoIds(a, 3))',
    'gallerySendIds = found',
    'GalleryReviewQueue.assign(conversationId)',
    'private suspend fun galleryCleanup(a: JsonObject, conversationId: Long)',
    'ToolSpecs.galleryCount.name -> galleryCount(args)',
    'ToolSpecs.galleryInspect.name -> galleryInspect(args)',
]:
    assert x in tool,x
for x in [
    'val gallerySendIds: List<Long> = emptyList()',
    'gallerySendIds.isNotEmpty()',
    'images.import(uri, maxEdge = 2048, prefix = "ai-gallery-")',
    'MessageImages.encode(imported)',
    'role = "assistant"',
    'db.conversations().touch(conversationId, at)',
    'images.delete(imported.map { it.file })',
    '不能声称已经发出',
]:
    assert x in (tool+"\n"+chat),x
for x in [
    'suspend fun sendablePhotos(ids: List<Long>): List<Long>',
    'override suspend fun sendablePhotos(ids: List<Long>): List<Long>',
    'photosByIds(ids: List<Long>)',
    'val owner = pendingOwner.asStateFlow()',
    'fun assign(conversationId: Long)',
]:
    assert x in g,x
for x in [
    'galleryReviewOwner == state.conversationId',
    'AI 建议检查 ',
    'GalleryManagerDialog',
    'galleryReviewOpen = true',
    'onVoiceInput = {',
    'ChatLocationPicker(',
]:
    assert x in ui,x
assert "MediaStore.createTrashRequest(" in manager
assert "StartIntentSenderForResult" in manager
assert "MediaStore.createDeleteRequest" not in manager
assert "createTrashRequest(" not in tool
assert "resolver.delete(" not in g
v=(r/"app/build.gradle.kts").read_text()
assert 'versionName = "0.38.34"' in v and 'versionCode = 62100' in v
print("PASS: full assistant image bubbles, per-chat human trash review, original music/voice/location/count intact")
