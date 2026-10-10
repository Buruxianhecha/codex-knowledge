#!/usr/bin/env python3
"""v0.38.9: simplify chat header and toolbar without removing underlying call/photo abilities."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
base = Path("app/src/main/java/com/cleo/cleos")
chat_path = base / "ui/chat/ChatScreen.kt"

def once(path: Path | str, old: str, new: str, reason: str):
    file = root / path
    source = file.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{reason}: expected 1 match, got {count} in {path}")
    file.write_text(source.replace(old, new, 1), encoding="utf-8")

# Remove the TWO top-right shortcuts in both single and group chats.
# Do not affect the phone entry under the chat's + panel.
once(chat_path,
    '                    if (!selecting) GlassIconButton(Icons.Rounded.Call, "打电话", { startCall() }, page)\n',
    '',
    "remove top-right call icon")
once(chat_path,
    '                    if (!state.isGroup) GlassIconButton(Icons.Rounded.AddComment, "新对话", vm::newConversation, page)\n',
    '',
    "remove top-right create-conversation plus icon")

# Reuse the existing press-and-hold microphone implementation: no new audio
# permission, codec, recording or cancel path. Remove redundant picture shortcut.
once(chat_path, '''
            Box(Modifier.size(BarHeight), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !editing && attachments.size < MAX_ATTACHMENTS, onClick = onPick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "发图片", tint = palette.contentSecondary, modifier = Modifier.size(24.dp))
                }
            }
''', '''
            Box(Modifier.size(BarHeight), contentAlignment = Alignment.Center) {
                // Recording remains a hold-to-talk gesture with the existing
                // slide-up-to-cancel behaviour. This replaces the duplicate photo shortcut.
                MicButton(
                    Modifier.size(38.dp).clip(CircleShape),
                    recording,
                    { if (editing) false else onVoiceStart() },
                    onVoiceMove,
                    onVoiceEnd,
                )
            }
''', "move existing mic to the far left of the composer")

# The right side is now for the + menu and, while composing, the send/stop
# control. In an empty idle composer, + is the rightmost button.
once(chat_path, '''
                } else if (!canSend && !editing) {
                    // Nothing typed: the button is for talking instead.
                    MicButton(button, recording, onVoiceStart, onVoiceMove, onVoiceEnd)
                } else {
''', '''
                } else if (canSend || editing) {
''', "remove old right-side microphone; retain send/stop behavior")

# Do not reserve an empty 50dp slot after "+" when no message can be sent:
# when idle the plus button must be the actual rightmost control.
once(chat_path, '''
            Box(Modifier.size(BarHeight), contentAlignment = Alignment.Center) {
                // Plain fills inside the glass, like the chips on a card: glass in glass reads as a hole.
''', '''
            if (busy || canSend || editing) {
                Box(Modifier.size(BarHeight), contentAlignment = Alignment.Center) {
                    // Plain fills inside the glass, like the chips on a card: glass in glass reads as a hole.
''', "hide empty right-side send placeholder")
once(chat_path, '''
                }
            }
        }
    }
}

@Composable
private fun AttachmentThumb''', '''
                }
                }
            }
        }
    }
}

@Composable
private fun AttachmentThumb''', "close conditional trailing send/stop button")

# The photo picker is still used in the + attachment panel; remove only the
# obsolete InputBar argument so there is no disconnected unused shortcut.
once(chat_path, '''
                onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
''', '', "remove redundant composer photo callback")
once(chat_path, '''
    onPick: () -> Unit,
    plusOpen: Boolean = false,
''', '''
    plusOpen: Boolean = false,
''', "remove unused composer photo parameter")

plus = root / base / "ui/chat/ChatWalletPanel.kt"
content = plus.read_text(encoding="utf-8")
old = '        Action("礼物", Icons.Rounded.Redeem, false, {}),\n'
if content.count(old) != 1:
    raise RuntimeError("gift action must appear exactly once in the + panel")
plus.write_text(content.replace(old, '', 1), encoding="utf-8")

once("app/build.gradle.kts", 'versionName = "0.38.8"', 'versionName = "0.38.9"', "versionName")
once("app/build.gradle.kts", 'versionCode = 62074', 'versionCode = 62075', "versionCode")

print("v0.38.9 (62075): removed top call & top plus, removed gift & redundant photo, mic moved left, chat plus retained")
