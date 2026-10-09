#!/usr/bin/env python3
"""Promote tested 0.37.41beta to full 0.38.0 release with real Moments, favorites."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def change(rel,old,new,label):
    p=root/rel
    text=p.read_text(encoding="utf-8")
    count=text.count(old)
    if count!=1:raise RuntimeError(f"{label}: {count} matches")
    p.write_text(text.replace(old,new,1),encoding="utf-8")

for source,dest in [
    ("MomentsStore_03741.kt","app/src/main/java/com/cleo/cleos/data/MomentsStore.kt"),
    ("MomentsMyProfile_0380.kt","app/src/main/java/com/cleo/cleos/ui/MomentsMyProfile.kt"),
    ("FavoritesScreen_0380.kt","app/src/main/java/com/cleo/cleos/ui/FavoritesScreen.kt"),
    ("MomentsAutonomy_0380.kt","app/src/main/java/com/cleo/cleos/ai/MomentsAutonomy.kt"),
]:
    output=root/dest
    output.parent.mkdir(parents=True,exist_ok=True)
    copyfile(here/source,output)

hub="app/src/main/java/com/cleo/cleos/ui/HubScreens.kt"
main="app/src/main/java/com/cleo/cleos/ui/MainScreen.kt"
app="app/src/main/java/com/cleo/cleos/CleosApp.kt"
screen="app/src/main/java/com/cleo/cleos/ui/MomentsScreen.kt"
chat="app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
backup="app/src/main/java/com/cleo/cleos/data/BackupService.kt"

change(hub,
'''fun DiscoverTab(bottomInset:Dp,onMoments:()->Unit,onDiary:()->Unit,onTodo:()->Unit,
                onLetters:()->Unit,onMemory:()->Unit) {''',
'''fun DiscoverTab(bottomInset:Dp,onMoments:()->Unit,onFavorites:()->Unit,
                onDiary:()->Unit,onTodo:()->Unit,onLetters:()->Unit,onMemory:()->Unit) {''',
"hub inputs")
change(hub,
'''            item { DirectoryRow("朋友圈","发布动态、照片、点赞、评论及邀请 AI 互动",Icons.Rounded.Group,onMoments) }''',
'''            item { DirectoryRow("朋友圈","个人主页、动态、AI 主动逛与发表",Icons.Rounded.Group,onMoments) }
            item { DirectoryRow("收藏","收藏的文字消息与语音，独立保存",Icons.Rounded.Bookmark,onFavorites) }''',
"hub order")

change(main,
'''                                "moments" -> MomentsScreen(''',
'''                                "favorites" -> FavoritesScreen(onBack = { secondPage = "" })
                                "moments" -> MomentsScreen(''',
"nested favorites screen")
change(main,
'''                                    onMoments = { secondPage = "moments" },''',
'''                                    onMoments = { secondPage = "moments" },
                                    onFavorites = { secondPage = "favorites" },''',
"nested favorites navigation")

p=root/screen
text=p.read_text(encoding="utf-8")
start=text.index('            item {\n                GlassSurface',text.index("LazyColumn("))
end=text.index('            if(data.posts.isEmpty()) item',start)
text=text[:start]+'            item { MomentsMyProfile() }\n'+text[end:]
p.write_text(text,encoding="utf-8")

change(app,
'''    val chatClient = ChatClient(http, keyPool=apiKeyPool, providerPool=providerFailoverPool)''',
'''    val chatClient = ChatClient(http, keyPool=apiKeyPool, providerPool=providerFailoverPool)
    val momentsAutonomy = com.cleo.cleos.ai.MomentsAutonomy(this)''',
"scheduled AI model holder")
change(app,
'''    init {
        // The first TA is made from the old settings before anything asks who is being talked to.''',
'''    init {
        com.cleo.cleos.ai.MomentsAutonomy.schedule(context)
        // The first TA is made from the old settings before anything asks who is being talked to.''',
"schedule persistent, default-off AI work")

change(chat,
'''    val palette = LocalGlassPalette.current
    val mine = message.role == "user"''',
'''    val palette = LocalGlassPalette.current
    val favoritesContainer = appContainer()
    val mine = message.role == "user"''',
"capture Compose app container before asynchronous favorites action")
change(chat,
'''                    if (words.isNotBlank()) {
                        DropdownMenuItem(text = { Text("复制") }, onClick = {''',
'''                    if (message.role in listOf("user","assistant") &&
                        (words.isNotBlank() || audio != null)) {
                        DropdownMenuItem(text = { Text("收藏") }, onClick = {
                            menu = false
                            scope.launch {
                                try {
                                    favoritesContainer.moments.favorite(
                                        message, if (mine) "我" else aiLabel ?: "TA")
                                } catch (e: Exception) {
                                    android.util.Log.w("Favorites","保存消息失败",e)
                                }
                            }
                        })
                    }
                    if (words.isNotBlank()) {
                        DropdownMenuItem(text = { Text("复制") }, onClick = {''',
"save from actual long-press menu")

change(backup,
'''    this?.posts?.flatMap { it.photos } ?: emptyList()''',
'''    (this?.posts?.flatMap { it.photos } ?: emptyList()) +
        listOfNotNull(this?.profile?.cover,this?.profile?.avatar) +
        (this?.savedMessages?.mapNotNull { it.audioFile } ?: emptyList())''',
"back up profile photos and voice favorites")
change(backup,
'''            incomingMoments.posts.flatMap { it.photos }.forEach { name ->''',
'''            if(incomingMoments.profile.name.length > 32 || incomingMoments.profile.bio.length > 200)
                throw BackupException("朋友圈主页资料过长")
            if(incomingMoments.savedMessages.size > 20000 ||
                incomingMoments.ai.size > 200)
                throw BackupException("朋友圈收藏或 AI 配置数量异常")
            incomingMoments.savedMessages.forEach { item ->
                if(item.text.length > 50000 || item.author.length > 100 ||
                    (item.audioFile != null && !MomentsRules.validMediaName(item.audioFile)))
                    throw BackupException("收藏语音或文字格式不安全")
            }
            incomingMoments.orEmptyPhotos().forEach { name ->''',
"verify all backup assets, including voice and header")
change("app/src/main/java/com/cleo/cleos/data/MomentsStore.kt",
'''    fun validImageName(name: String): Boolean =''',
'''    fun validMediaName(name: String): Boolean = name.safeMediaName()
    fun validImageName(name: String): Boolean =''',
"saved audio filename validator")

build="app/build.gradle.kts"
change(build,'versionName = "0.37.41beta"','versionName = "0.38.0"',"stable name")
change(build,'versionCode = 62065','versionCode = 62066',"new version code")

print("0.38.0 / 62066 stable: WeChat-like header, AI autonomy, saved messages, voice, backup.")
