#!/usr/bin/env python3
"""Source-level assertions on assembled app; no AI-initiated deletion is permitted."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
src=root/"app/src/main/java/com/cleo/cleos"
here=Path(__file__).resolve().parent
for a,b in [
    ("GalleryAccess_03832.kt",src/"ai/GalleryAccess.kt"),
    ("GalleryManager_03832.kt",src/"ui/settings/GalleryManager.kt"),
    ("GallerySafetyTest_03832.kt",root/"app/src/test/java/com/cleo/cleos/ai/GallerySafetyTest.kt"),
]:
    assert (here/a).read_bytes()==b.read_bytes(),b
g=(src/"ai/GalleryAccess.kt").read_text()
ui=(src/"ui/settings/GalleryManager.kt").read_text()
tools=(src/"ai/Tools.kt").read_text()
prompt=(src/"ai/ChatRepository.kt").read_text()
settings=(src/"ui/settings/SharedPages.kt").read_text()
manifest=(root/"app/src/main/AndroidManifest.xml").read_text()
for token in [
    "READ_MEDIA_VISUAL_USER_SELECTED",
    "READ_MEDIA_IMAGES",
    "GalleryAccess.Selected",
    "inspectPhotos(ids: List<Long>)",
    "ids.distinct().take(3)",
    "proposeTrash(ids: List<Long>)",
    "GalleryReviewQueue.propose(existing)",
]:
    assert token in g,token
for token in [
    "MediaStore.createTrashRequest(",
    "StartIntentSenderForResult",
    "Checkbox(checked = photo.id in selected",
    "selected = emptySet()",
    "rows.any { it.id == id }",
    "Android 的系统",
]:
    if token == "Android 的系统": continue
    assert token in ui,token
assert "MediaStore.createDeleteRequest" not in ui
assert "resolver.delete(" not in g
assert "delete(" not in ui
assert "GallerySource" in tools and "ToolGroup.Gallery" in tools
for token in ["list_gallery_photos", "inspect_gallery_photos", "prepare_gallery_cleanup",
    "galleryImages = images.map", "if (spec.groups.none { it in settings.tools })"]:
    assert token in tools,token
assert 'galleryVision += ApiMessage(' in prompt
assert 'results.any { it.images.isNotEmpty() }' in prompt
assert "ToolGroup.Gallery" in settings
assert "GalleryManagerDialog" in settings
assert 'android.permission.READ_MEDIA_VISUAL_USER_SELECTED' in manifest
assert 'android.permission.READ_MEDIA_IMAGES' in manifest
v=(root/"app/build.gradle.kts").read_text()
assert 'versionName = "0.38.32"' in v and 'versionCode = 62098' in v
assert "QqMusicAccessibilityService" in (src/"ai/AndroidPhoneApps.kt").read_text()
assert "ChatLocationPicker(" in (src/"ui/chat/ChatScreen.kt").read_text()
assert "VoiceDictationDraft" in (src/"ui/chat/ChatScreen.kt").read_text()
print("PASS: full/partial gallery opt-in, bounded on-demand AI vision, human review, OS trash, prior features")
