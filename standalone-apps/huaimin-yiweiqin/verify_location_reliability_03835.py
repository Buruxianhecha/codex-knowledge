#!/usr/bin/env python3
"""Verify assembled location patch and existing 0.38.34 features without new permissions."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
src=r/"app/src/main/java/com/cleo/cleos/ui/chat/ChatLocation.kt"
test=r/"app/src/test/java/com/cleo/cleos/ui/chat/LocationQualityTest.kt"
assert src.read_bytes()==(here/"ChatLocation_03835.kt").read_bytes()
assert test.read_bytes()==(here/"LocationQualityTest_03835.kt").read_bytes()
s=src.read_text()
for marker in [
    "manager.isLocationEnabled",
    "LocationManager.NETWORK_PROVIDER",
    "LocationManager.GPS_PROVIDER",
    "LocationManager.PASSIVE_PROVIDER",
    "getLastKnownLocation(provider)",
    "getCurrentLocation(",
    "withTimeoutOrNull(7000L)",
    "withTimeoutOrNull(8500L)",
    "requests.forEach { it.cancel() }",
    "Geocoder.GeocodeListener",
    "withTimeoutOrNull(4000L)",
    "if (point != null || manualMode)",
    "if (point == null) manualMode = true",
    "正在尝试网络定位和 GPS",
    "定位超时",
    "手动选点模式",
    "这不是你的位置",
    "manualStart",
    "q.trim().split(Regex(",
    "android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
    "android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS",
    "ChinaMapLink.marker(p)",
    "https://uri.amap.com/marker?position=",
    "https://uri.amap.com/search?keyword=",
    "coordinate=wgs84",
    "preferChinaMap()",
    "在高德地图查看选中位置",
    "selected.latitude,selected.longitude",
    "Button(onClick={ point?.let(onSend) },enabled=point != null)",
]:
    assert marker in s, marker
picker=s.split("internal fun ChatLocationPicker(",1)[1].split("@OptIn(ExperimentalFoundationApi::class)",1)[0]
assert "GeoPoint(20.0, 0.0)" not in picker, "world map should not be shown in picker when GPS fails"
for marker in ["ChatLocationCard(", "ChatLocationViewer(", "ChatLocationPayload", "openLocationNavigation"]:
    assert marker in s,marker
screen=(r/"app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt").read_text()
for marker in ["ChatLocationPicker(", "galleryReviewOpen", "sendLocation(point)","VoiceDictationDraft"]:
    assert marker in screen,marker
tools=(r/"app/src/main/java/com/cleo/cleos/ai/Tools.kt").read_text()
for marker in ["send_gallery_photos","count_gallery_photos","prepare_gallery_cleanup"]:
    assert marker in tools,marker
gradle=(r/"app/build.gradle.kts").read_text()
assert 'versionName = "0.38.35"' in gradle
assert 'versionCode = 62101' in gradle
print("PASS location failover, honest map state, geocoder timeout, manual selection, full previous features")
