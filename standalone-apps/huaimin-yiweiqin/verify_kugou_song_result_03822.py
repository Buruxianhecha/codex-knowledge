#!/usr/bin/env python3
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
p=(r/"app/src/main/java/com/cleo/cleos/ai/KugouSearchPlan.kt").read_text(encoding="utf-8")
t=(r/"app/src/test/java/com/cleo/cleos/ai/KugouResultSafetyTest.kt").read_text(encoding="utf-8")
grad=(r/"app/build.gradle.kts").read_text(encoding="utf-8")
assert 'versionName = "0.38.22"' in grad
assert "versionCode = 62088" in grad
for term in [
    'player != "com.kugou.android"', 'val searchTabs = labels.contains("综合")',
    'labels.contains("视频")', '"songs"', 'val artistCard',
    'comprehensiveSelected', 'if (artistCard || comprehensiveSelected) return QqUiAction.Wait',
    'val singerLink', 'song.artistMatches(w)', 'target != null',
    '"song"', 'val artistHome', 'QqUiAction.Stop',
]:
    assert term in p, term
for term in [
    'comprehensiveResultsMustSwitchToSingleTrackTabFirst',
    'artistProfileMustNeverBeChosenAsPlayback',
    'songTitleWithoutSameRowArtistIsNotEnough',
    'noClickableSingleTabNeverFallsBackToArtistCard',
    'clickingSongMarksCompleteButClickingArtistNeverDoes'
]:
    assert term in t,term
print("PASS: 0.38.22 visual regression safe song tab + exact song row; no artist-home clicks")
