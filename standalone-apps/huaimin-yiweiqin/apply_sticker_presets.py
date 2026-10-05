"""Add built-in emoji sticker presets and a separate heart tab for personal stickers."""
from pathlib import Path


def apply_sticker_presets(root: Path):
    def replace(rel: str, old: str, new: str):
        path = root / rel
        text = path.read_text(encoding="utf-8")
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{rel}: expected exactly one match, found {count}: {old[:100]!r}")
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    data = "app/src/main/java/com/cleo/cleos/data/Stickers.kt"
    replace(
        data,
        "import android.graphics.Bitmap\nimport android.graphics.ImageDecoder\n",
        "import android.graphics.Bitmap\nimport android.graphics.Canvas\nimport android.graphics.ImageDecoder\nimport android.graphics.Paint\nimport android.graphics.Typeface\n",
    )
    replace(
        data,
        "    val all: Flow<List<StickerEntity>> = db.stickers().observeAll()\n\n",
        """    val all: Flow<List<StickerEntity>> = db.stickers().observeAll()

    /** Built-in emoji presets: generated locally once, so they work offline and travel through the normal sticker path. */
    suspend fun ensureBuiltIns() = withContext(Dispatchers.IO) {
        val dao = db.stickers()
        val existing = dao.all()
        BUILT_INS.forEachIndexed { index, item ->
            val alreadyThere = existing.any { sticker ->
                sticker.name == item.name || StickerBook.aliases(sticker).contains(item.name)
            }
            if (alreadyThere) return@forEachIndexed

            val fileName = "sticker-preset-${index + 1}.png"
            val file = images.file(fileName)
            if (!file.exists()) renderEmoji(item.emoji, file)
            dao.insert(
                StickerEntity(
                    name = item.name,
                    description = item.description,
                    file = fileName,
                    width = PRESET_SIZE,
                    height = PRESET_SIZE,
                    animated = false,
                    createdAt = PRESET_CREATED_AT + index,
                ),
            )
        }
    }

    private fun renderEmoji(emoji: String, file: File) {
        val bitmap = Bitmap.createBitmap(PRESET_SIZE, PRESET_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 188f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val metrics = paint.fontMetrics
        val baseline = PRESET_SIZE / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(emoji, PRESET_SIZE / 2f, baseline, paint)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

""",
    )
    replace(
        data,
        """    companion object {
        const val PREFIX = "sticker-"
""",
        """    companion object {
        private const val PRESET_SIZE = 256
        private const val PRESET_CREATED_AT = -10_000L

        private data class BuiltInSticker(val emoji: String, val name: String, val description: String)

        private val BUILT_INS = listOf(
            BuiltInSticker("🙂", "微笑", "温柔微笑"),
            BuiltInSticker("😍", "心动", "双眼爱心，很喜欢"),
            BuiltInSticker("😘", "亲亲", "飞吻亲亲"),
            BuiltInSticker("😮", "惊讶", "张嘴惊讶"),
            BuiltInSticker("😒", "不爽", "斜眼不爽"),
            BuiltInSticker("😪", "困困", "困倦想睡"),
            BuiltInSticker("😭", "大哭", "伤心大哭"),
            BuiltInSticker("🤣", "笑翻", "笑到停不下来"),
            BuiltInSticker("😡", "生气", "非常生气"),
            BuiltInSticker("😁", "呲牙", "开心露齿笑"),
            BuiltInSticker("😅", "尴尬", "流汗尴尬笑"),
            BuiltInSticker("😌", "安心", "闭眼安心"),
            BuiltInSticker("😉", "眨眼", "眨眼示意"),
            BuiltInSticker("😛", "调皮", "吐舌调皮"),
            BuiltInSticker("🥰", "甜甜", "幸福喜欢"),
            BuiltInSticker("🥺", "可怜", "可怜巴巴请求"),
            BuiltInSticker("🥹", "感动", "眼含泪光感动"),
            BuiltInSticker("😢", "流泪", "难过流泪"),
            BuiltInSticker("🤩", "星星眼", "惊喜崇拜"),
            BuiltInSticker("🤭", "偷笑", "捂嘴偷笑"),
            BuiltInSticker("🤔", "思考", "认真思考"),
            BuiltInSticker("😊", "害羞", "害羞开心"),
            BuiltInSticker("😱", "震惊", "震惊尖叫"),
            BuiltInSticker("😎", "酷", "戴墨镜很酷"),
            BuiltInSticker("🙄", "无语", "翻白眼无语"),
            BuiltInSticker("😴", "睡觉", "睡着了"),
            BuiltInSticker("🤗", "抱抱", "张手拥抱"),
            BuiltInSticker("🙏", "拜托", "双手合十拜托"),
            BuiltInSticker("👍", "点赞", "竖起大拇指"),
            BuiltInSticker("👏", "鼓掌", "鼓掌夸奖"),
            BuiltInSticker("🫰", "比心", "手指比心"),
            BuiltInSticker("❤️", "爱心", "红色爱心"),
            BuiltInSticker("🌹", "玫瑰", "送一朵玫瑰"),
            BuiltInSticker("😂", "笑哭", "笑到流眼泪"),
            BuiltInSticker("😚", "亲一口", "闭眼亲亲"),
            BuiltInSticker("😳", "脸红", "惊讶脸红"),
        )

        const val PREFIX = "sticker-"
""",
    )

    ui = "app/src/main/java/com/cleo/cleos/ui/chat/Stickers.kt"
    replace(
        ui,
        "import androidx.compose.material.icons.rounded.Add\n",
        "import androidx.compose.material.icons.rounded.Add\nimport androidx.compose.material.icons.rounded.Favorite\n",
    )
    replace(
        ui,
        """/**
 * The person's stickers, inside the input's glass above the text. A tap sends one, a long press
 * renames or deletes it, ＋ adds one from the gallery. The names show under the pictures: they
 * are all the TA gets of them.
 */
@Composable
fun StickerDrawer(
    stickers: List<StickerEntity>,
    onSend: (StickerEntity) -> Unit,
    onAdd: () -> Unit,
    onRename: (StickerEntity) -> Unit,
    onDelete: (StickerEntity) -> Unit,
) {
    val palette = LocalGlassPalette.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CellPicture + 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(DrawerHeight)
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "add") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(CellPicture)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.content.copy(alpha = 0.08f))
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "加表情包", tint = palette.contentSecondary, modifier = Modifier.size(26.dp))
                }
                Text("加一张", color = palette.contentSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
        items(stickers, key = { it.id }) { s -> StickerCell(s, onSend, onRename, onDelete) }
    }
}
""",
        """/**
 * Sticker drawer with two shelves:
 * - preset: built-in yellow-face/gesture stickers, ready to tap;
 * - heart: the person's own favourites, with the gallery upload button.
 */
@Composable
fun StickerDrawer(
    stickers: List<StickerEntity>,
    onSend: (StickerEntity) -> Unit,
    onAdd: () -> Unit,
    onRename: (StickerEntity) -> Unit,
    onDelete: (StickerEntity) -> Unit,
) {
    val palette = LocalGlassPalette.current
    var mine by remember { mutableStateOf(false) }
    val presets = stickers.filter { it.createdAt < 0L }
    val personal = stickers.filter { it.createdAt >= 0L }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(CellPicture + 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(DrawerHeight)
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StickerShelfButton(
                    selected = !mine,
                    onClick = { mine = false },
                ) {
                    Text("😊 预设", fontSize = 13.sp)
                }
                StickerShelfButton(
                    selected = mine,
                    onClick = { mine = true },
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = "我的表情",
                        modifier = Modifier.size(18.dp),
                    )
                    Text("我的", fontSize = 13.sp)
                }
            }
        }

        if (!mine) {
            items(presets, key = { "preset-${it.id}" }) { s ->
                PresetStickerCell(s, onSend)
            }
        } else {
            item(key = "add") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(CellPicture)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.content.copy(alpha = 0.08f))
                            .clickable(onClick = onAdd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "加表情包",
                            tint = palette.contentSecondary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Text("加一张", color = palette.contentSecondary, fontSize = 11.sp, maxLines = 1)
                }
            }
            items(personal, key = { it.id }) { s -> StickerCell(s, onSend, onRename, onDelete) }
        }
    }
}

@Composable
private fun StickerShelfButton(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable Row.() -> Unit,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.22f) else palette.content.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PresetStickerCell(
    sticker: StickerEntity,
    onSend: (StickerEntity) -> Unit,
) {
    val c = appContainer()
    Box(
        Modifier
            .size(CellPicture + 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSend(sticker) },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = c.images.file(sticker.file),
            contentDescription = sticker.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(CellPicture),
        )
    }
}
""",
    )
