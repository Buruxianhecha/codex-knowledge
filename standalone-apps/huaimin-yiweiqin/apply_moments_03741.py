#!/usr/bin/env python3
"""0.37.41beta: put a real Moments page first on Discover, preserving all 0.37.40 features."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def edit(path,old,new,label):
    f=root/path
    value=f.read_text(encoding="utf-8")
    if value.count(old)!=1: raise RuntimeError(f"{label}: expected one anchor, got {value.count(old)}")
    f.write_text(value.replace(old,new,1),encoding="utf-8")

main="app/src/main/java/com/cleo/cleos/ui/MainScreen.kt"
hub="app/src/main/java/com/cleo/cleos/ui/HubScreens.kt"
app="app/src/main/java/com/cleo/cleos/CleosApp.kt"
build="app/build.gradle.kts"

edit(hub,
'''fun DiscoverTab(bottomInset:Dp,onDiary:()->Unit,onTodo:()->Unit,
                onLetters:()->Unit,onMemory:()->Unit) {''',
'''fun DiscoverTab(bottomInset:Dp,onMoments:()->Unit,onDiary:()->Unit,onTodo:()->Unit,
                onLetters:()->Unit,onMemory:()->Unit) {''',"discover signature")
edit(hub,
'''            item { DirectoryRow("日记","所有日记、私密日记、TA 的日记、写日记",Icons.Rounded.AutoStories,onDiary) }''',
'''            item { DirectoryRow("朋友圈","发布动态、照片、点赞、评论及邀请 AI 互动",Icons.Rounded.Group,onMoments) }
            item { DirectoryRow("日记","所有日记、私密日记、TA 的日记、写日记",Icons.Rounded.AutoStories,onDiary) }''',"moments must be the first entry")
edit(main,
'''                                "diary" -> DiaryTab(24.dp, onOpenDiaryEntry, onBack = { secondPage = "" })''',
'''                                "moments" -> MomentsScreen(
                                    onBack = { secondPage = "" },
                                    onOpenImage = onOpenImage,
                                )
                                "diary" -> DiaryTab(24.dp, onOpenDiaryEntry, onBack = { secondPage = "" })''',"actual moments screen route")
edit(main,
'''                                    onDiary = { secondPage = "diary" },''',
'''                                    onMoments = { secondPage = "moments" },
                                    onDiary = { secondPage = "diary" },''',"discover first row navigation")
edit(app,
'''    val images = ImageStore(context)''',
'''    val images = ImageStore(context)
    val moments = com.cleo.cleos.data.MomentsStore(context, images)''',"moments store single app-level instance")

for name,dest in [
    ("MomentsStore_03741.kt","app/src/main/java/com/cleo/cleos/data/MomentsStore.kt"),
    ("MomentsScreen_03741.kt","app/src/main/java/com/cleo/cleos/ui/MomentsScreen.kt"),
]:
    target=root/dest
    target.parent.mkdir(parents=True,exist_ok=True)
    copyfile(here/name,target)

edit(build,'versionName = "0.37.40beta"','versionName = "0.37.41beta"',"version name")
edit(build,'versionCode = 62064','versionCode = 62065',"version code")
print("0.37.41beta / 62065: actual Moments screen and store, first Discover card, other four cards preserved")
