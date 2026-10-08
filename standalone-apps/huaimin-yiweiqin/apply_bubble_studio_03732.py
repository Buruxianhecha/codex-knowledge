#!/usr/bin/env python3
"""0.37.32: native independent bubble studio, persistent profiles and real chat rendering."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
base = root/"app/src/main/java/com/cleo/cleos"

def once(path: Path, old: str, new: str):
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"bubble studio patch failed {path}: expected exactly 1 anchor, got {count}: {old[:130]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

# Serializable BubbleStudioConfig stored in its own DataStore key and included in safe ZIP backups.
settings=base/"data/SettingsRepository.kt"
once(settings,
'''    /** The colour of the person's own bubbles (ARGB), still glass; null follows the wallpaper. */
    val myBubble: Int? = null,''',
'''    /** The colour of the person's own bubbles (ARGB), still glass; null follows the wallpaper. */
    val myBubble: Int? = null,
    /** Independently tunable mine/default/per-character bubble glass; JSON survives backups. */
    val bubbleStudio: String = "",''')
once(settings,'''        "my_bubble" to "int",''','''        "my_bubble" to "int",
        "bubble_studio" to "string",''')
once(settings,'''        val myBubble = intPreferencesKey("my_bubble")''',
'''        val myBubble = intPreferencesKey("my_bubble")
        val bubbleStudio = stringPreferencesKey("bubble_studio")''')
once(settings,'''            myBubble = this[Keys.myBubble],''',
'''            myBubble = this[Keys.myBubble],
            bubbleStudio = this[Keys.bubbleStudio].orEmpty(),''')
once(settings,
'''            if (next.myBubble != null) prefs[Keys.myBubble] = next.myBubble else prefs.remove(Keys.myBubble)''',
'''            if (next.myBubble != null) prefs[Keys.myBubble] = next.myBubble else prefs.remove(Keys.myBubble)
            if (next.bubbleStudio.isNotEmpty()) prefs[Keys.bubbleStudio] = next.bubbleStudio
            else prefs.remove(Keys.bubbleStudio)''')

# Give an obvious first-party entry from the existing "外观" page. Preserve its old
# controls while studio is disabled, so existing users have no visual regressions.
look=base/"ui/settings/AppPages.kt"
once(look,'''internal fun LookPage(vm: SettingsViewModel, onOpenLab: () -> Unit) {''',
'''internal fun LookPage(vm: SettingsViewModel, onOpenLab: () -> Unit, onOpenBubbleStudio: () -> Unit) {''')
once(look,
'''    // It names itself ("我的气泡"), so its card has no title.
    Section(null) {
        MyBubbleColor(settings.myBubble, vm::setMyBubble)
    }
}''',
'''    Section("气泡实验室 · 深度自定义") {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp)
                .clickable(interactionSource = null, indication = null,
                    onClickLabel = "进入气泡实验室", onClick = onOpenBubbleStudio),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("进入气泡实验室", color = palette.content, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("自己 / 每个 AI 独立设置，玻璃、颜色、渐变、圆角、留白、预设与实时预览",
                    color = palette.contentSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null, tint = palette.contentSecondary)
        }
    }
    if (!com.cleo.cleos.data.BubbleStudioCodec.decode(settings.bubbleStudio).enabled) {
        Section(null) { MyBubbleColor(settings.myBubble, vm::setMyBubble) }
    }
}''')
screen=base/"ui/settings/SettingsScreen.kt"
once(screen,'''    onOpenLab: () -> Unit,
    onOpenMcp: (String) -> Unit,''',
'''    onOpenLab: () -> Unit,
    onOpenBubbleStudio: () -> Unit,
    onOpenMcp: (String) -> Unit,''')
once(screen,'''                    SettingsPage.Look -> LookPage(vm, onOpenLab)''',
'''                    SettingsPage.Look -> LookPage(vm, onOpenLab, onOpenBubbleStudio)''')

nav=base/"ui/CleosNavHost.kt"
once(nav,'''import com.cleo.cleos.ui.lab.GlassLabScreen''',
'''import com.cleo.cleos.ui.lab.GlassLabScreen
import com.cleo.cleos.ui.bubbles.BubbleStudioScreen''')
once(nav,'''@Serializable
object LabRoute''',
'''@Serializable
object BubbleStudioRoute

@Serializable
object LabRoute''')
once(nav,'''                onOpenLab = { nav.go(LabRoute) },
                onOpenMcp = { nav.go(McpEditRoute(it)) },''',
'''                onOpenLab = { nav.go(LabRoute) },
                onOpenBubbleStudio = { nav.go(BubbleStudioRoute) },
                onOpenMcp = { nav.go(McpEditRoute(it)) },''')
once(nav,'''        composable<LabRoute> { GlassLabScreen(onBack = nav::back) }''',
'''        composable<LabRoute> { GlassLabScreen(onBack = nav::back) }
        composable<BubbleStudioRoute> { BubbleStudioScreen(onBack = nav::back) }''')

chat=base/"ui/chat/ChatScreen.kt"
once(chat,'''import com.cleo.cleos.data.AppSettings''',
'''import com.cleo.cleos.data.AppSettings
import com.cleo.cleos.data.BubbleStudioCodec
import com.cleo.cleos.ui.bubbles.BubbleSurface
import com.cleo.cleos.ui.bubbles.LocalBubbleCompanion
import com.cleo.cleos.ui.bubbles.LocalBubbleStudio
import com.cleo.cleos.ui.bubbles.bubbleTextColor''')
once(chat,
'''CompositionLocalProvider(LocalFaces provides faces, LocalStickers provides stickerBook, LocalChatType provides chatType, LocalPat provides patActions)''',
'''CompositionLocalProvider(
            LocalFaces provides faces, LocalStickers provides stickerBook,
            LocalChatType provides chatType, LocalPat provides patActions,
            LocalBubbleStudio provides remember(appSettings.bubbleStudio) { BubbleStudioCodec.decode(appSettings.bubbleStudio) },
            LocalBubbleCompanion provides state.companionId,
        )''')
once(chat,
'''                            onLongClick = { menu = true },
                        )
                    } else {
                        pieces.forEach { piece ->''',
'''                            onLongClick = { menu = true },
                            companionId = aiLabelId ?: LocalBubbleCompanion.current,
                        )
                    } else {
                        pieces.forEach { piece ->''')
once(chat,
'''                                is StickerText.Piece.Words -> GlassSurface(
                                    modifier = Modifier
                                        .widthIn(max = bubbleMaxWidth())
                                        .combinedClickable(
                                            interactionSource = null,
                                            indication = null,
                                            onClick = {},
                                            onLongClick = { menu = true },
                                        ),
                                    style = if (mine) palette.bubbleMine else palette.bubble,
                                    shape = GlassShape.Rounded(20.dp),
                                    contentPadding = BubblePadding,
                                ) {
                                    Text(
                                        piece.text,
                                        color = if (mine) palette.mineContent else palette.content,
                                        style = LocalChatType.current.body,
                                    )
                                }''',
'''                                is StickerText.Piece.Words -> BubbleSurface(
                                    mine = mine, companionId = aiLabelId ?: LocalBubbleCompanion.current,
                                    modifier = Modifier
                                        .widthIn(max = bubbleMaxWidth())
                                        .combinedClickable(
                                            interactionSource = null,
                                            indication = null,
                                            onClick = {},
                                            onLongClick = { menu = true },
                                        ),
                                ) {
                                    Text(
                                        piece.text,
                                        color = bubbleTextColor(mine, aiLabelId ?: LocalBubbleCompanion.current),
                                        style = LocalChatType.current.body,
                                    )
                                }''')
once(chat,
'''    onLongClick: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val ink = if (mine) palette.mineContent else palette.content
    val length = 92.dp''',
'''    onLongClick: () -> Unit,
    companionId: Long? = LocalBubbleCompanion.current,
) {
    val palette = LocalGlassPalette.current
    val ink = bubbleTextColor(mine, companionId)
    val length = 92.dp''')
once(chat,
'''    GlassSurface(
        modifier = Modifier
            .widthIn(min = length, max = bubbleMaxWidth())
            .combinedClickable(interactionSource = null, indication = null, onClick = onClick, onLongClick = onLongClick),
        style = if (mine) palette.bubbleMine else palette.bubble,
        shape = GlassShape.Rounded(20.dp),
        contentPadding = BubblePadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {''',
'''    BubbleSurface(
        mine = mine, companionId = companionId,
        modifier = Modifier
            .widthIn(min = length, max = bubbleMaxWidth())
            .combinedClickable(interactionSource = null, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {''')

# Streaming and typing states use the very same renderer and companion default.
once(chat,
'''                        GlassSurface(
                            style = palette.bubble,
                            shape = GlassShape.Rounded(20.dp),
                            contentPadding = BubblePadding,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypingDots()''',
'''                        BubbleSurface(mine = false) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypingDots()''')
once(chat,
'''                            is StickerText.Piece.Words -> GlassSurface(
                                modifier = Modifier.widthIn(max = bubbleMaxWidth()),
                                style = palette.bubble,
                                shape = GlassShape.Rounded(20.dp),
                                contentPadding = BubblePadding,
                            ) {
                                Text(piece.text, color = palette.content, style = LocalChatType.current.body)
                            }''',
'''                            is StickerText.Piece.Words -> BubbleSurface(
                                mine = false, modifier = Modifier.widthIn(max = bubbleMaxWidth()),
                            ) {
                                Text(piece.text, color = bubbleTextColor(false), style = LocalChatType.current.body)
                            }''')

# Materialize renderer, schema, tests in standard Gradle source paths.
for source,destination in [
    ("BubbleStudioCodec.kt",base/"data/BubbleStudioCodec.kt"),
    ("BubbleSurface.kt",base/"ui/bubbles/BubbleSurface.kt"),
    ("BubbleStudioScreen.kt",base/"ui/bubbles/BubbleStudioScreen.kt"),
    ("BubbleStudioCodecTest.kt",root/"app/src/test/java/com/cleo/cleos/data/BubbleStudioCodecTest.kt"),
]:
    dest = destination
    dest.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(here/source,dest)

gradle=root/"app/build.gradle.kts"
once(gradle,'versionName = "0.37.31"','versionName = "0.37.32"')
once(gradle,'versionCode = 62053','versionCode = 62054')
print("v0.37.32 / 62054: bubble lab with native renderer, individual roles and DataStore backup; Room schema unchanged")
