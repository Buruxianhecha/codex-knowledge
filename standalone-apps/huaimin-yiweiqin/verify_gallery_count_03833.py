#!/usr/bin/env python3
"""Verify a one-query count without regressing the original gallery/photo delete tools."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
ai=r/"app/src/main/java/com/cleo/cleos/ai"
gallery=(ai/"GalleryAccess.kt").read_text(encoding="utf-8")
tools=(ai/"Tools.kt").read_text(encoding="utf-8")
for needle in [
    "suspend fun countPhotos(query: String, album: String): Int",
    "override suspend fun countPhotos(query: String, album: String): Int",
    "arrayOf(MediaStore.Images.Media._ID)",
    "cursor.use { it.count }",
    "GalleryAccess.Selected",
    "GalleryCountText",
    "inspectPhotos(ids: List<Long>)",
    "proposeTrash(ids: List<Long>)",
    "GalleryReviewQueue.propose(existing)",
]:
    assert needle in gallery, needle
count_body=gallery.split("override suspend fun countPhotos(",1)[1].split("    private fun rows(",1)[0]
assert "QUERY_ARG_LIMIT" not in count_body and "QUERY_ARG_OFFSET" not in count_body
assert "loadThumbnail" not in count_body and "data:image" not in count_body
for needle in [
    'name = "count_gallery_photos"',
    '必须优先且仅调用这个工具一次',
    'count_gallery_photos 一次',
    'galleryCount,',
    'ToolSpecs.galleryCount.name -> galleryCount(args)',
    'private suspend fun galleryCount(a: JsonObject)',
    'source.countPhotos(query, album)',
    "GalleryCountText.render(count, source.access(), query, album)",
    "ToolSpecs.galleryList.name -> galleryList(args)",
    "ToolSpecs.galleryInspect.name -> galleryInspect(args)",
    "ToolSpecs.galleryCleanup.name -> galleryCleanup(args)",
    "if (spec.groups.none { it in settings.tools })",
]:
    assert needle in tools, needle
assert 'versionName = "0.38.33"' in (r/"app/build.gradle.kts").read_text()
assert 'versionCode = 62099' in (r/"app/build.gradle.kts").read_text()
assert "MediaStore.createTrashRequest(" in (r/"app/src/main/java/com/cleo/cleos/ui/settings/GalleryManager.kt").read_text()
print("PASS: real Android image count in one query, proper partial authorization, zero photo uploads, legacy tools preserved")
