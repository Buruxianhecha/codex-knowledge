#!/usr/bin/env python3
"""0.38.28: after selecting QQ Music song, start the actual player safely."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
java=root/"app/src/main/java/com/cleo/cleos"
test=root/"app/src/test/java/com/cleo/cleos/ai"
for source,target in [
    ("QqFinalPlayRules_03828.kt",java/"ai/QqFinalPlayRules.kt"),
    ("QqFinalPlayRulesTest_03828.kt",test/"QqFinalPlayRulesTest.kt"),
    ("QqMusicAccessibilityService_03828.kt",java/"QqMusicAccessibilityService.kt"),
]:
    target.parent.mkdir(parents=True,exist_ok=True)
    copyfile(here/source,target)

def edit(file,old,new):
    s=file.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1: raise RuntimeError(f"{file}: expected one match, got {n}: {old[:120]!r}")
    file.write_text(s.replace(old,new,1),encoding="utf-8")

edit(java/"ai/AndroidPhoneApps.kt",
'''        QqMusicAccessibilityService.select(app.packageName, song) {
            song.matches(music.currentFor(app.packageName), app.packageName)
        }''',
'''        QqMusicAccessibilityService.select(
            player = app.packageName,
            song = song,
            isPlaying = { song.matches(music.currentFor(app.packageName), app.packageName) },
            resumePaused = { music.resumeExact(app.packageName, song) },
        )''')

edit(java/"ai/Music.kt",
'''    fun search(player: String, song: SongRequest): Boolean {''',
'''    /**
     * Only resume an exact QQ target already selected in the paused media
     * session; never send a blind media play/pause command to another song.
     */
    fun resumeExact(player: String, song: SongRequest): Boolean {
        if (player !in AppNames.qqMusic) return false
        val now = currentFor(player) ?: return false
        if (now.playing || now.player != player ||
            AppNames.key(now.title.trim('《', '》', '「', '」', '"')) != AppNames.key(song.title) ||
            (song.artist.isNotBlank() && !song.artistMatches(now.artist))
        ) return false
        val controller = controllers().firstOrNull { it.packageName == player } ?: return false
        val actions = controller.playbackState?.actions ?: return false
        val allowed = android.media.session.PlaybackState.ACTION_PLAY or
            android.media.session.PlaybackState.ACTION_PLAY_PAUSE
        if ((actions and allowed) == 0L) return false
        return runCatching { controller.transportControls.play(); true }.getOrDefault(false)
    }

    fun search(player: String, song: SongRequest): Boolean {''')
edit(root/"app/src/main/res/xml/qq_music_accessibility.xml",
    'android:canRetrieveWindowContent="true"',
    'android:canRetrieveWindowContent="true"\n    android:canPerformGestures="true"')
g=root/"app/build.gradle.kts"
for old,new in [('versionName = "0.38.27"','versionName = "0.38.28"'),
                ('versionCode = 62093','versionCode = 62094')]:
    edit(g,old,new)
print("0.38.28 / 62094: click final play in QQ only, real media-state success only")
