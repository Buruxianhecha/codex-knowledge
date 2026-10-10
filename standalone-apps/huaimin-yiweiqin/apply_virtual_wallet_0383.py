#!/usr/bin/env python3
"""v0.38.3 incremental wallet integration on top of the frozen 0.38.2 tree."""
from pathlib import Path
from shutil import copyfile
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
base=Path("app/src/main/java/com/cleo/cleos")

def once(rel,before,after,what):
    p=root/rel
    text=p.read_text(encoding="utf-8")
    count=text.count(before)
    if count!=1: raise RuntimeError(f"{what}: expected one anchor, found {count}: {rel}")
    p.write_text(text.replace(before,after,1),encoding="utf-8")

for source,dest in [
    ("VirtualWalletStore_0383.kt",base/"data"/"VirtualWalletStore.kt"),
    ("VirtualWalletScreen_0383.kt",base/"ui"/"VirtualWalletScreen.kt"),
    ("VirtualWalletRulesTest_0383.kt",Path("app/src/test/java/com/cleo/cleos/data/VirtualWalletRulesTest.kt")),
]:
    target=root/dest
    target.parent.mkdir(parents=True,exist_ok=True)
    copyfile(here/source,target)

once(base/"CleosApp.kt",
'''    val moments = com.cleo.cleos.data.MomentsStore(context, images).also {''',
'''    val wallet = com.cleo.cleos.data.VirtualWalletStore(context)
    val moments = com.cleo.cleos.data.MomentsStore(context, images).also {''',
"instantiate one isolated wallet store")

once(base/"ui"/"HubScreens.kt",
'''fun DiscoverTab(bottomInset:Dp,onMoments:()->Unit,onFavorites:()->Unit,
                onDiary:()->Unit,onTodo:()->Unit,onLetters:()->Unit,onMemory:()->Unit) {''',
'''fun DiscoverTab(bottomInset:Dp,onMoments:()->Unit,onFavorites:()->Unit,onWallet:()->Unit,
                onDiary:()->Unit,onTodo:()->Unit,onLetters:()->Unit,onMemory:()->Unit) {''',
"discover wallet callback")

once(base/"ui"/"HubScreens.kt",
'''            item { DirectoryRow("收藏","收藏的文字消息与语音，独立保存",Icons.Rounded.Bookmark,onFavorites) }''',
'''            item { DirectoryRow("收藏","收藏的文字消息与语音，独立保存",Icons.Rounded.Bookmark,onFavorites) }
            item { DirectoryRow("钱包","虚拟余额、转账、普通红包和拼手气红包",Icons.Rounded.AccountBalanceWallet,onWallet) }''',
"discover wallet menu item")

once(base/"ui"/"MainScreen.kt",
'''                                "favorites" -> FavoritesScreen(onBack = { secondPage = "" })''',
'''                                "wallet" -> VirtualWalletScreen(onBack = { secondPage = "" })
                                "favorites" -> FavoritesScreen(onBack = { secondPage = "" })''',
"nested virtual wallet screen")

once(base/"ui"/"MainScreen.kt",
'''                                    onFavorites = { secondPage = "favorites" },''',
'''                                    onFavorites = { secondPage = "favorites" },
                                    onWallet = { secondPage = "wallet" },''',
"wallet navigation callback")

gradle=Path("app/build.gradle.kts")
once(gradle,'versionName = "0.38.2"','versionName = "0.38.3"',"version name")
once(gradle,'versionCode = 62068','versionCode = 62069',"version code")
print("0.38.3/62069: wallet ledger, discover page, isolated virtual balance and packet tests")
