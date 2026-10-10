#!/usr/bin/env python3
"""Static integration regression for v0.38.9 chat header + composer + menu."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
base = root / "app/src/main/java/com/cleo/cleos"
screen = (base / "ui/chat/ChatScreen.kt").read_text(encoding="utf-8")
panel = (base / "ui/chat/ChatWalletPanel.kt").read_text(encoding="utf-8")
assert 'versionName = "0.38.9"' in (root / "app/build.gradle.kts").read_text(encoding="utf-8")
assert 'versionCode = 62075' in (root / "app/build.gradle.kts").read_text(encoding="utf-8")

# Header must not display the two circled shortcuts, in EITHER conversation mode.
start = screen.index("            GlassTopBar(", screen.index("private fun") if False else 0)
end = screen.index("                onTitleClick =", start)
top = screen[start:end]
assert 'GlassIconButton(Icons.Rounded.Call, "打电话"' not in top
assert 'GlassIconButton(Icons.Rounded.AddComment' not in top
assert 'GlassIconButton(Icons.Rounded.Forum' in top, "keep conversation history entry"

# Keep the call itself available via chat plus, rather than deleting call features.
assert 'onCall = { plusOpen = false; startCall() }' in screen
assert 'onCamera = {' in screen
assert 'onAlbum = {' in screen
assert 'onRedPacket = {' in screen
assert 'onTransfer = {' in screen

idx = screen.index("private fun ChatInputBar(")
composer = screen[idx:]
assert "onPick: () -> Unit" not in composer, "redundant photo parameter survived"
assert "Icons.Rounded.AddPhotoAlternate" not in composer, "redundant photo shortcut survived"
assert composer.count("MicButton(") == 1, "hold-to-talk mic must appear exactly once on left"
assert composer.index("MicButton(") < composer.index("BasicTextField("), "mic should precede text field"
assert composer.index("BasicTextField(") < composer.index("Icons.Rounded.EmojiEmotions")
assert composer.index("Icons.Rounded.EmojiEmotions") < composer.index("Icons.Rounded.Add,")
assert "if (busy || canSend)" in composer, "no empty trailing slot after +"
assert "if (busy && !canSend)" in composer, "stop control must remain"
assert 'Icon(Icons.Rounded.ArrowUpward, contentDescription = "发送"' in composer, "send control must remain"

for item in ('Action("相册"', 'Action("拍摄"', 'Action("语音通话"',
             'Action("红包"', 'Action("转账"'):
    assert item in panel, item
assert 'Action("礼物"' not in panel, "disabled gift must be removed"
print("v0.38.9 toolbar regression OK: top right cleared, mic left, text/emoji/+ right, photo & gift removed; all actions preserved")
