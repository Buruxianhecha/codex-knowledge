#!/usr/bin/env python3
"""Regression checks for the v0.38.14 previous-track mini-player feature."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
bar = (root / "app/src/main/java/com/cleo/cleos/ui/chat/Listening.kt").read_text(encoding="utf-8")
chat = (root / "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt").read_text(encoding="utf-8")
music = (root / "app/src/main/java/com/cleo/cleos/ai/Music.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
assert 'versionName = "0.38.14"' in gradle
assert "versionCode = 62080" in gradle
assert "import androidx.compose.material.icons.rounded.SkipPrevious" in bar
assert "    onPrevious: () -> Unit," in bar
assert "    onToggle: () -> Unit," in bar and "    onNext: () -> Unit," in bar
previous = 'Control(Icons.Rounded.SkipPrevious, "上一首", onPrevious)'
play_pause = 'Control(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.playing) "暂停" else "接着放", onToggle)'
next_song = 'Control(Icons.Rounded.SkipNext, "下一首", onNext)'
assert bar.count(previous) == 1 and bar.count(play_pause) == 1 and bar.count(next_song) == 1
assert bar.index(previous) < bar.index(play_pause) < bar.index(next_song)
assert 'onPrevious = { runCatching { c.music.control(MusicAction.Previous) } }' in chat
assert 'onNext = { runCatching { c.music.control(MusicAction.Next) } }' in chat
assert 'MusicAction.Previous -> t.skipToPrevious()' in music
assert 'MusicAction.Next -> t.skipToNext()' in music
assert 'MusicAction.Pause -> t.pause()' in music and 'MusicAction.Play -> t.play()' in music
print("PASS: prior / play-pause / next buttons and media-session routing wired, version 0.38.14/62080")
