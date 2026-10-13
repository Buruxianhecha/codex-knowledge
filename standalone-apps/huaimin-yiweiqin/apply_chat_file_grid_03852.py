#!/usr/bin/env python3
"""0.38.52: put 文件 in the eighth (+) tile like the provided WeChat reference.

Never rebuild the existing chat UI or change any file-transfer/storage semantics.
This migration runs after 0.38.51's successful file integration.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
base = root / "app/src/main/java/com/cleo/cleos/ui/chat"
screen = base / "ChatScreen.kt"
panel = base / "ChatWalletPanel.kt"
gradle = root / "app/build.gradle.kts"

def replace_once(src: str, old: str, new: str, label: str) -> str:
    found = src.count(old)
    if found != 1:
        raise RuntimeError(f"{label}: expected one anchor, got {found}")
    return src.replace(old, new, 1)

s = screen.read_text(encoding="utf-8")
p = panel.read_text(encoding="utf-8")
g = gradle.read_text(encoding="utf-8")

# Keep exactly the same picker callback but make it the standard grid action.
old_open = """                plusPanel = {
                    Column {
                        TextButton(onClick = {
                            plusOpen=false
                            filePicker.launch(arrayOf("*/*"))
                        }, modifier=Modifier.fillMaxWidth()) {
                            Text("📎 文件 · HTML / PDF / Word / Excel / 更多格式")
                        }
                        ChatPlusPanel("""
new_open = """                plusPanel = {
                    ChatPlusPanel(
                        onFile = {
                            plusOpen = false
                            filePicker.launch(arrayOf("*/*"))
                        },"""
s = replace_once(s, old_open, new_open, "replace extra top banner with 8th grid callback")
old_close = """                        voiceInputBusy = dictationBusy,
                        )
                    }
                },"""
new_close = """                        voiceInputBusy = dictationBusy,
                    )
                },"""
s = replace_once(s,old_close,new_close,"remove wrapper and preserve all existing actions")

p = replace_once(p, """    onVoiceInput: () -> Unit,
    voiceInputRecording: Boolean,""",
"""    onVoiceInput: () -> Unit,
    onFile: () -> Unit,
    voiceInputRecording: Boolean,""", "grid file callback")
p = replace_once(p, """            onVoiceInput,
        ),
    )
    Column(""",
"""            onVoiceInput,
        ),
        Action("文件", Icons.Rounded.Folder, true, onFile),
    )
    Column(""", "append standard file icon in eighth slot")

g = replace_once(g, 'versionName = "0.38.51"', 'versionName = "0.38.52"', "versionName")
g = replace_once(g, "versionCode = 62117", "versionCode = 62118", "versionCode")

# Fail before modifying any file if we might have lost existing commands.
start = p.index("internal fun ChatPlusPanel(")
end = p.index("\n@Composable", start + 10)
plus = p[start:end]
expected_labels = ("相册","拍摄","语音通话","位置","红包","转账","语音输入","文件")
for label in expected_labels:
    if f'Action("{label}"' not in plus and f'-> "{label}"' not in plus and label != "语音输入":
        raise RuntimeError("Missing preserved + menu tile: " + label)
if plus.count('Action("文件", Icons.Rounded.Folder, true, onFile)') != 1:
    raise RuntimeError("Expected one file icon tile")
if plus.index('Action("文件",') < plus.index('Action("转账",'):
    raise RuntimeError("File tile must come after transfer and voice input")
if "📎 文件 · HTML / PDF / Word / Excel / 更多格式" in s:
    raise RuntimeError("Old redundant document header remains")
if s.count('filePicker.launch(arrayOf("*/*"))') != 1:
    raise RuntimeError("File picker must be bound exactly once to the + menu")
if s.count("onFile = {") != 1:
    raise RuntimeError("File callback must be wired")
if "ChatFileTransfer.decode(message.content)" not in s:
    raise RuntimeError("Original file-message bubble missing")

screen.write_text(s, encoding="utf-8")
panel.write_text(p, encoding="utf-8")
gradle.write_text(g, encoding="utf-8")
print("0.38.52 Build 62118: + → eight standard tiles with 文件 as row 2 column 4; no top document banner")
