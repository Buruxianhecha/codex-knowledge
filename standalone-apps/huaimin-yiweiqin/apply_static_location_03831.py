#!/usr/bin/env python3
"""0.38.31: integrate explicit, static, WeChat-like chat location sharing.

No migration; location payloads are ordinary messages. No live tracking.
"""
from pathlib import Path
from shutil import copyfile
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
src=root/"app/src/main/java/com/cleo/cleos/ui/chat"
tests=root/"app/src/test/java/com/cleo/cleos/ui/chat"
tests.mkdir(parents=True,exist_ok=True)
copyfile(here/"ChatLocation_03831.kt",src/"ChatLocation.kt")
copyfile(here/"ChatLocationPayloadTest_03831.kt",tests/"ChatLocationPayloadTest.kt")

def replace_once(s,old,new,reason):
    n=s.count(old)
    if n!=1: raise RuntimeError(f"{reason}: expected 1 anchor, found {n}")
    return s.replace(old,new,1)

plus=src/"ChatWalletPanel.kt"
s=plus.read_text(encoding="utf-8")
s=replace_once(s,
'''    onTransfer: () -> Unit,
    onVoiceInput: () -> Unit,''',
'''    onTransfer: () -> Unit,
    onLocation: () -> Unit,
    onVoiceInput: () -> Unit,''',"plus location callback")
s=replace_once(s,
'''Action("位置", Icons.Rounded.Place, false, {}),''',
'''Action("位置", Icons.Rounded.Place, true, onLocation),''',"enable plus location")
plus.write_text(s,encoding="utf-8")

vm=src/"ChatViewModel.kt"
s=vm.read_text(encoding="utf-8")
old='''    /**
     * A sticker from the drawer, at once, on its own, the way chat apps send them: with the quote
'''
new='''    /**
     * Send an explicitly chosen static point without accidentally consuming current draft,
     * image attachments, @ mentions or pending quotes.
     */
    internal fun sendLocation(point: ChatLocationPoint): Boolean {
        val id=conversationId.value ?: return false
        return c.chat.send(id,ChatLocationPayload.encode(point),emptyList(),null,emptyMap())
    }

'''+old
s=replace_once(s,old,new,"send location without draft")
vm.write_text(s,encoding="utf-8")

chat=src/"ChatScreen.kt"
s=chat.read_text(encoding="utf-8")
s=replace_once(s,
'''    var plusOpen by rememberSaveable { mutableStateOf(false) }''',
'''    var plusOpen by rememberSaveable { mutableStateOf(false) }
    var locationPickerOpen by remember(state.conversationId) { mutableStateOf(false) }
    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { locationPickerOpen = true }''',"location permission and picker state")
s=replace_once(s,
'''                        onTransfer = { plusOpen = false; walletAction = ChatMoneyKind.TRANSFER },
                        onVoiceInput = {''',
'''                        onTransfer = { plusOpen = false; walletAction = ChatMoneyKind.TRANSFER },
                        onLocation = {
                            plusOpen = false
                            val fine = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val coarse = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (fine || coarse) locationPickerOpen = true
                            else requestLocation.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ))
                        },
                        onVoiceInput = {''',"plus location launch")
s=replace_once(s,
'''    walletAction?.let { kind ->
''',
'''    if (locationPickerOpen) {
        ChatLocationPicker(
            onDismiss = { locationPickerOpen = false },
            onSend = { point ->
                if (vm.sendLocation(point)) {
                    locationPickerOpen = false
                    sentCount++
                } else voiceHint = "位置没有发送成功，请重新打开聊天后再试"
            },
        )
    }
    walletAction?.let { kind ->
''',"location picker controlled by user")
s=replace_once(s,
'''    var mergedOpen by remember { mutableStateOf(false) }
''',
'''    var mergedOpen by remember { mutableStateOf(false) }
    var shownLocation by remember { mutableStateOf<ChatLocationPoint?>(null) }
''',"location detail popup state")
s=replace_once(s,
'''    val pieces = remember(message.content, book) { StickerText.split(message.content, book) }''',
'''    val pieces = remember(message.content, book) { StickerText.split(message.content, book) }
    val locationPoint = remember(message.content) { ChatLocationPayload.decode(message.content) }''',"parse location")
s=replace_once(s,
'''                    } else if (BatchMessageActions.isMerged(message.content)) {''',
'''                    } else if (locationPoint != null) {
                        ChatLocationCard(
                            point = locationPoint,
                            onClick = { shownLocation = locationPoint },
                            onLongClick = { menu = true },
                        )
                    } else if (BatchMessageActions.isMerged(message.content)) {''',"render location bubble instead of raw JSON")
s=replace_once(s,
'''                if (mergedOpen) {''',
'''                shownLocation?.let { point ->
                    ChatLocationViewer(point) { shownLocation = null }
                }
                if (mergedOpen) {''',"view full map from location card")
chat.write_text(s,encoding="utf-8")

gradle=root/"app/build.gradle.kts"
s=gradle.read_text(encoding="utf-8")
s=replace_once(s,
'''    implementation(libs.coil.compose)''',
'''    implementation(libs.coil.compose)
    // Native map: no API key or paid developer account. Tiles need internet.
    implementation("org.osmdroid:osmdroid-android:6.1.20")''',"OSM interactive native map dependency")
s=replace_once(s,'versionName = "0.38.30"','versionName = "0.38.31"',"versionName")
s=replace_once(s,'versionCode = 62096','versionCode = 62097',"versionCode")
gradle.write_text(s,encoding="utf-8")
print("0.38.31/62097: static location picker, map, search, send, chat card, map detail & navigation")
