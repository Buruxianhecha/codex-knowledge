#!/usr/bin/env python3
"""0.38.8 add true system camera; remove only group header plus; keep existing AI picture path."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base=Path("app/src/main/java/com/cleo/cleos")
chat=base/"ui/chat/ChatScreen.kt"
test=root/"app/src/test/java/com/cleo/cleos/data/StarcoinBalanceEditTest.kt"
test.parent.mkdir(parents=True,exist_ok=True)
test.write_bytes((Path(__file__).resolve().parent/"StarcoinBalanceEditTest_0388.kt").read_bytes())
def once(path,before,after,label):
    p=root/path
    data=p.read_text(encoding="utf-8")
    count=data.count(before)
    if count!=1: raise RuntimeError(f"{label}: expected one anchor, got {count} ({path})")
    p.write_text(data.replace(before,after,1),encoding="utf-8")
once(chat,
'''GlassIconButton(Icons.Rounded.AddComment, if (state.isGroup) "新单聊" else "新对话", vm::newConversation, page)''',
'''if (!state.isGroup) GlassIconButton(Icons.Rounded.AddComment, "新对话", vm::newConversation, page)''',
"keep one-to-one new chat, delete group header shortcut")
once(chat,
'''    val focusManager = LocalFocusManager.current''',
'''    var cameraOutput by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraOutput?.let(android.net.Uri::parse)
        if (taken && uri != null) {
            vm.attach(listOf(uri)) // preview the captured photo; sending remains user-confirmed
        } else {
            cameraFilePath?.let { java.io.File(it).delete() }
        }
        cameraOutput = null
        cameraFilePath = null
        pickingAttachment = false
    }
    val focusManager = LocalFocusManager.current''',
"register real camera launcher after attachment picker")
once(chat,
'''                        onCall = { plusOpen = false; startCall() },''',
'''                        onCamera = {
                            plusOpen = false
                            try {
                                val directory = java.io.File(context.cacheDir, "huaimin-camera")
                                if (!directory.exists() && !directory.mkdirs()) error("拍摄缓存创建失败")
                                directory.listFiles()?.filter {
                                    System.currentTimeMillis() - it.lastModified() > 172_800_000L
                                }?.forEach { it.delete() }
                                val file = java.io.File(directory,
                                    "photo-" + java.util.UUID.randomUUID().toString() + ".jpg")
                                if (!file.createNewFile()) error("照片文件创建失败")
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, context.packageName + ".huaimin.camera", file)
                                cameraFilePath = file.absolutePath
                                cameraOutput = uri.toString()
                                pickingAttachment = true
                                vm.typing(true, processingMedia = true)
                                cameraCapture.launch(uri)
                            } catch (e: Exception) {
                                cameraFilePath?.let { java.io.File(it).delete() }
                                cameraFilePath = null
                                cameraOutput = null
                                pickingAttachment = false
                                vm.typing(false)
                                voiceHint = "无法打开手机相机：" + (e.message ?: "相机不可用")
                            }
                        },
                        onCall = { plusOpen = false; startCall() },''',
"wire real camera + tool to secure content URI")
manifest=root/"app/src/main/AndroidManifest.xml"
m=manifest.read_text(encoding="utf-8")
if "android.support.FILE_PROVIDER_PATHS" in m and "huaimin.camera" in m:
    raise RuntimeError("camera provider already defined")
anchor='    <application'
if m.count(anchor)!=1: raise RuntimeError("unexpected manifest layout")
start=m.index(anchor)
end=m.index(">",start)+1
m=m[:end]+'''
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.huaimin.camera"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/huaimin_camera_paths" />
        </provider>'''+m[end:]
manifest.write_text(m,encoding="utf-8")
paths=root/"app/src/main/res/xml/huaimin_camera_paths.xml"
paths.parent.mkdir(parents=True,exist_ok=True)
if paths.exists(): raise RuntimeError("camera provider paths already exist")
paths.write_text('''<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="capture" path="huaimin-camera/" />
</paths>
''',encoding="utf-8")
hub=root/base/"ui/HubScreens.kt"
h=hub.read_text(encoding="utf-8")
if 'DirectoryRow("信箱"' in h: raise RuntimeError("duplicate Discover mailbox not removed")
hub.write_text(h.replace("虚拟余额、转账、普通红包和拼手气红包","星币余额、转账、普通红包和拼手气红包"),encoding="utf-8")
# Only new UI/prompt strings change, not existing on-device user chat data or records.
for path in (base/"ai/WalletChatBridge.kt",base/"ai/Tools.kt",
             base/"ai/WalletReactiveEvents.kt",base/"ui/chat/ChatScreen.kt",
             base/"ui/chat/ChatWalletPanel.kt",base/"ui/VirtualWalletScreen.kt"):
    p=root/path
    p.write_text(p.read_text(encoding="utf-8").replace("虚拟币","星币").replace("虚拟红包","星币红包").replace("虚拟转账","星币转账"),encoding="utf-8")
once("app/build.gradle.kts",'versionName = "0.38.7"','versionName = "0.38.8"',"version")
once("app/build.gradle.kts",'versionCode = 62073','versionCode = 62074',"code")
print("0.38.8/62074 real camera, starcoin naming, only chat composer plus in group")
