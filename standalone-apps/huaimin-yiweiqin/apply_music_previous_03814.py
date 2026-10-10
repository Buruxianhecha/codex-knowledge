#!/usr/bin/env python3
"""v0.38.14: add an actual previous-track control to the chat mini music player."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
src = root / "app/src/main/java/com/cleo/cleos/ui/chat"

def replace_once(path: Path, before: str, after: str, description: str) -> None:
    contents = path.read_text(encoding="utf-8")
    count = contents.count(before)
    if count != 1:
        raise RuntimeError(f"{description}: expected exactly one anchor, found {count}: {path}")
    path.write_text(contents.replace(before, after, 1), encoding="utf-8")

bar = src / "Listening.kt"
chat = src / "ChatScreen.kt"
replace_once(
    bar,
    "import androidx.compose.material.icons.rounded.SkipNext\n",
    "import androidx.compose.material.icons.rounded.SkipNext\nimport androidx.compose.material.icons.rounded.SkipPrevious\n",
    "import previous-track icon",
)
replace_once(
    bar,
    "    onToggle: () -> Unit,\n    onNext: () -> Unit,\n",
    "    onPrevious: () -> Unit,\n    onToggle: () -> Unit,\n    onNext: () -> Unit,\n",
    "mini-player callback contract",
)
replace_once(
    bar,
    '        Control(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.playing) "暂停" else "接着放", onToggle)\n',
    '        Control(Icons.Rounded.SkipPrevious, "上一首", onPrevious)\n        Control(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.playing) "暂停" else "接着放", onToggle)\n',
    "previous / play-pause / next ordering",
)
replace_once(
    chat,
    '                        onNext = { runCatching { c.music.control(MusicAction.Next) } },\n',
    '                        onPrevious = { runCatching { c.music.control(MusicAction.Previous) } },\n                        onNext = { runCatching { c.music.control(MusicAction.Next) } },\n',
    "route previous control to existing media session",
)
gradle = root / "app/build.gradle.kts"
replace_once(gradle, 'versionName = "0.38.13"', 'versionName = "0.38.14"', "versionName")
replace_once(gradle, "versionCode = 62079", "versionCode = 62080", "versionCode")
print("0.38.14/62080: music mini-player previous / play-pause / next, existing music controller reused")
