"""Add user-provided sticker image packs on top of the existing emoji preset drawer."""
from pathlib import Path
import base64
import io
import shutil
import sys
import zipfile

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(".").resolve()
HERE = Path(__file__).resolve().parent


def replace(rel: str, old: str, new: str):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected exactly one sticker-image patch target, found {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Materialise the exact 30 processed user images into Android assets.
archive_b64 = HERE / "assets" / "sticker_presets_v03716.b64"
raw = base64.b64decode("".join(archive_b64.read_text(encoding="utf-8").split()), validate=True)
expected = {
    *(f"sticker-mengmeizhi-{i:02d}.webp" for i in range(1, 25)),
    *(f"sticker-dog-{i:02d}.webp" for i in range(1, 7)),
}
asset_dir = ROOT / "app/src/main/assets/sticker_presets"
asset_dir.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(io.BytesIO(raw)) as bundle:
    names = {Path(n).name for n in bundle.namelist() if not n.endswith("/")}
    if names != expected:
        raise SystemExit(f"unexpected sticker preset archive: {sorted(names)}")
    for name in sorted(expected):
        target = asset_dir / name
        target.write_bytes(bundle.read(name))

# Shared catalog + tests.
for source_name, destination in (
    ("StickerPresetCatalog.kt", ROOT / "app/src/main/java/com/cleo/cleos/data/StickerPresetCatalog.kt"),
    ("StickerPresetCatalogTest.kt", ROOT / "app/src/test/java/com/cleo/cleos/data/StickerPresetCatalogTest.kt"),
):
    source = HERE / ("src" if source_name == "StickerPresetCatalog.kt" else "tests") / source_name
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)

data = "app/src/main/java/com/cleo/cleos/data/Stickers.kt"
replace(
    data,
    """class Stickers(context: Context, private val db: AppDatabase, private val images: ImageStore) {
    private val resolver = context.contentResolver
""",
    """class Stickers(context: Context, private val db: AppDatabase, private val images: ImageStore) {
    private val resolver = context.contentResolver
    private val assets = context.assets
""",
)

replace(
    data,
    """            )
        }
    }

    private fun renderEmoji(emoji: String, file: File) {
""",
    """            )
        }

        // User-provided image packs are copied from APK assets into the normal ImageStore so
        // sending, backup, multimodal vision and rendering all use the same mature sticker path.
        val afterEmoji = dao.all()
        StickerPresetCatalog.imageBuiltIns.forEachIndexed { index, item ->
            val file = images.file(item.assetFile)

            // Self-heal every launch: an older build may have created the tab/catalog but failed
            // before the private ImageStore file or database row was written.
            if (!file.isFile || file.length() == 0L) {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, ".${item.assetFile}.tmp")
                assets.open("${StickerPresetCatalog.ASSET_DIR}/${item.assetFile}").use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (file.exists()) file.delete()
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            }

            if (afterEmoji.any { it.file == item.assetFile }) return@forEachIndexed

            // These are our own verified bundled assets. Do not gate DB registration on
            // ImageDecoder: some OEM decoders reject a valid WebP during this tiny probe even
            // though Coil/Android can render it normally. The real image remains in ImageStore.
            dao.insert(
                StickerEntity(
                    name = item.name,
                    description = item.description,
                    file = item.assetFile,
                    width = IMAGE_PRESET_EDGE,
                    height = IMAGE_PRESET_EDGE,
                    animated = false,
                    createdAt = IMAGE_PRESET_CREATED_AT + index,
                ),
            )
        }
    }

    private fun renderEmoji(emoji: String, file: File) {
""",
)

replace(
    data,
    """        private const val PRESET_SIZE = 256
        private const val PRESET_CREATED_AT = -10_000L
""",
    """        private const val PRESET_SIZE = 256
        private const val PRESET_CREATED_AT = -10_000L
        private const val IMAGE_PRESET_CREATED_AT = -20_000L
        private const val IMAGE_PRESET_EDGE = 320
""",
)

ui = "app/src/main/java/com/cleo/cleos/ui/chat/Stickers.kt"
replace(
    ui,
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n",
)
replace(
    ui,
    "import androidx.compose.foundation.layout.Column\n",
    "import androidx.compose.foundation.layout.Column\nimport androidx.compose.foundation.layout.FlowRow\n",
)
replace(
    ui,
    """    var mine by remember { mutableStateOf(false) }
    val presets = stickers.filter { it.createdAt < 0L }
    val personal = stickers.filter { it.createdAt >= 0L }
""",
    """    val c = appContainer()
    LaunchedEffect(Unit) {
        // Repair older installs whose image-preset rows were not seeded successfully.
        c.stickers.ensureBuiltIns()
    }
    var mine by remember { mutableStateOf(false) }
    var shelf by remember { mutableStateOf("meng") }
    val presets = stickers.filter { it.createdAt < 0L }
    val personal = stickers.filter { it.createdAt >= 0L }
    val selectedPresets = presets.filter { sticker ->
        when (shelf) {
            "meng" -> sticker.file.startsWith("sticker-mengmeizhi-")
            "dog" -> sticker.file.startsWith("sticker-dog-")
            else -> !sticker.file.startsWith("sticker-mengmeizhi-") && !sticker.file.startsWith("sticker-dog-")
        }
    }
""",
)

replace(
    ui,
    """            Row(
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
""",
    """            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StickerShelfButton(
                    selected = !mine && shelf == "default",
                    onClick = { mine = false; shelf = "default" },
                ) {
                    Text("😊 默认", fontSize = 13.sp)
                }
                StickerShelfButton(
                    selected = !mine && shelf == "meng",
                    onClick = { mine = false; shelf = "meng" },
                ) {
                    Text("萌妹纸", fontSize = 13.sp)
                }
                StickerShelfButton(
                    selected = !mine && shelf == "dog",
                    onClick = { mine = false; shelf = "dog" },
                ) {
                    Text("小白狗", fontSize = 13.sp)
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
""",
)

replace(
    ui,
    '            items(presets, key = { "preset-${it.id}" }) { s ->\n',
    '            items(selectedPresets, key = { "preset-${it.id}" }) { s ->\n',
)

print("Sticker image presets applied: 24 萌妹纸 + 6 小白狗, with categorized drawer tabs.")
