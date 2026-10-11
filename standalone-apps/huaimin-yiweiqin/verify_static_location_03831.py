#!/usr/bin/env python3
"""Check actual assembled app, not only template source."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
src=r/"app/src/main/java/com/cleo/cleos/ui/chat"
for a,b in [
    ("ChatLocation_03831.kt",src/"ChatLocation.kt"),
    ("ChatLocationPayloadTest_03831.kt",r/"app/src/test/java/com/cleo/cleos/ui/chat/ChatLocationPayloadTest.kt"),
]:
    assert (here/a).read_bytes()==b.read_bytes(),b
plus=(src/"ChatWalletPanel.kt").read_text()
assert 'Action("位置", Icons.Rounded.Place, true, onLocation)' in plus
assert 'onVoiceInput: () -> Unit' in plus
assert 'voiceInputRecording: Boolean' in plus
for k in ["onAlbum","onCamera","onCall","onRedPacket","onTransfer"]:
    assert k in plus
chat=(src/"ChatScreen.kt").read_text()
for key in [
    "ChatLocationPicker(","vm.sendLocation(point)",
    "ChatLocationCard(","ChatLocationViewer(",
    "ChatLocationPayload.decode(message.content)",
    "requestLocation.launch(arrayOf(",
    "onVoiceInput = {",
    "onVoiceStart = { if (dictating || dictationBusy) false else startVoice() }",
]:
    assert key in chat,key
vm=(src/"ChatViewModel.kt").read_text()
assert "fun sendLocation(point: ChatLocationPoint): Boolean" in vm
assert "c.chat.send(id,ChatLocationPayload.encode(point),emptyList(),null,emptyMap())" in vm
gradle=(r/"app/build.gradle.kts").read_text()
assert 'versionName = "0.38.31"' in gradle
assert "versionCode = 62097" in gradle
assert 'implementation("org.osmdroid:osmdroid-android:6.1.20")' in gradle
manifest=(r/"app/src/main/AndroidManifest.xml").read_text()
assert 'android.permission.ACCESS_FINE_LOCATION' in manifest
assert 'android.permission.ACCESS_COARSE_LOCATION' in manifest
assert "HUAIMIN_LOCATION_V1:" in (src/"ChatLocation.kt").read_text()
assert "ChatLocationPayload.encode" in vm
print("PASS: 0.38.31 static map sharing; prior voice, wallet, album and camera unchanged")
