#!/usr/bin/env python3
"""Regression guard for realme/Android 16 chat location-map overflow."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
src = root / "app/src/main/java/com/cleo/cleos/ui/chat/ChatLocation.kt"
text = src.read_text(encoding="utf-8")
assert src.read_bytes() == (here / "ChatLocation_03836.kt").read_bytes()
map_fn = text.split("private fun StaticLocationMap(", 1)[1].split(
    "@Composable\ninternal fun ChatLocationPicker(", 1
)[0]
card = text.split("internal fun ChatLocationCard(", 1)[1].split(
    "/**\n * China-first keyless map handoff", 1
)[0]
for required in (
    "FrameLayout(context)",
    "clipChildren = true",
    "clipToPadding = true",
    "clipToOutline = true",
    "ViewOutlineProvider.BOUNDS",
    "modifier = modifier.clipToBounds()",
    "FrameLayout.LayoutParams.MATCH_PARENT",
    "setMultiTouchControls(!previewOnly)",
    "if (!previewOnly) view.overlays.add(Marker(view)",
):
    assert required in map_fn, f"Map view containment regressed: {required}"
for required in (
    ".widthIn(max = 272.dp)",
    ".fillMaxWidth()",
    ".clip(RoundedCornerShape(16.dp))",
    ".height(112.dp)",
    ".clip(RoundedCornerShape(10.dp))",
    "Modifier.fillMaxSize().clipToBounds()",
    "previewOnly = true",
    "Icons.Rounded.LocationOn",
    "Modifier.matchParentSize().combinedClickable(",
    "onLongClick = onLongClick",
    "© OpenStreetMap contributors",
    "查看地图",
):
    assert required in card, f"Chat card layout/action missing: {required}"
assert "Marker(view)" not in card, "Default native marker must not render in bubble"
assert "AndroidView(" not in card, "Map AndroidView should be bounded by StaticLocationMap"
assert "ChatLocationPayload" in text, "Existing V1 location message codec missing"
assert "internal fun ChatLocationPicker(" in text, "Location selection is missing"
assert "internal fun ChatLocationViewer(" in text, "Tap-to-open map details missing"
assert "openLocationNavigation(" in text, "Map navigation missing"
assert "ChinaMapLink" in text, "Mainland-map handoff missing"
assert 'versionName = "0.38.36"' in (root / "app/build.gradle.kts").read_text()
assert 'versionCode = 62102' in (root / "app/build.gradle.kts").read_text()
print("PASS: clipped MapView host, bounded preview, compact pin, actions, history and map viewer")
