"""Attach sent sticker artwork through the same request path as ordinary pictures."""
from pathlib import Path
import shutil


def apply_sticker_vision(root: Path):
    source = Path(__file__).resolve().parent
    ai = "app/src/main/java/com/cleo/cleos/ai/"

    def replace(rel: str, old: str, new: str):
        path = root / rel
        text = path.read_text(encoding="utf-8")
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{rel}: expected exactly one match, found {count}: {old[:80]!r}")
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    shutil.copy2(source / "src/MessagePictures.kt", root / ai / "MessagePictures.kt")
    tests = root / "app/src/test/java/com/cleo/cleos/ai"
    tests.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source / "tests/StickerVisionTest.kt", tests / "StickerVisionTest.kt")

    prompt = ai + "Prompt.kt"
    replace(prompt, "val attached = if (images) attachedPictures(history) else emptySet()",
            "val attached = if (images) attachedPictures(history, stickers) else emptySet()")
    replace(prompt, "m.toApi(withTools, images, attached, words, reacted)",
            "m.toApi(withTools, images, attached, words, reacted, stickers)")
    replace(prompt, "private fun attachedPictures(history: List<MessageEntity>): Set<Long>",
            "private fun attachedPictures(history: List<MessageEntity>, stickers: StickerBook): Set<Long>")
    replace(prompt, "            val n = MessageImages.decode(m.images).size",
            "            val n = MessagePictures.files(m, stickers).size")
    replace(prompt, "private fun MessageEntity.userText(canSee: Boolean, attached: Boolean, words: (String) -> String): String",
            "private fun MessageEntity.userText(canSee: Boolean, attached: Boolean, words: (String) -> String, stickers: StickerBook): String")
    replace(prompt, '        val said = if (audio != null && text.isNotBlank()) "（语音）$text" else text', """        val spokenText = if (audio != null && text.isNotBlank()) "（语音）$text" else text
        val sentStickers = MessagePictures.stickers(this, stickers)
        val stickerLine = if (sentStickers.isEmpty()) null else {
            val names = sentStickers.joinToString("、") { it.name }
            when {
                attached -> "（附表情图片：" + names + "；请按图片画面理解，名字只是标记）"
                !canSee -> "（表情包：" + names + "；本轮没有附表情图片，只能按名字和说明理解）"
                else -> "（早先发的表情包：" + names + "；本轮没再附上图片）"
            }
        }
        val said = if (stickerLine == null) spokenText else stickerLine + "\\n" + spokenText""")
    replace(prompt, """        reacted: Map<Long, List<String>>,
    ): ApiMessage?""", """        reacted: Map<Long, List<String>>,
        stickers: StickerBook,
    ): ApiMessage?""")
    replace(prompt, "userText(canSee, attach, words)", "userText(canSee, attach, words, stickers)")
    replace(prompt, "images = if (attach) MessageImages.decode(images).map { it.file } else emptyList()",
            "images = if (attach) MessagePictures.files(this, stickers) else emptyList()")
    replace(prompt, """     * Stickers go as names (StickerText): the model never sees the pictures, only this list of what
     * is in them. "Not every time" because a model told it can send stickers sends one with every
""", """     * Stickers keep their names (StickerText) for display and sending. The person's sent artwork
     * can also be attached for vision. "Not every time" because a model offered stickers sends one with every
""")
    replace(prompt, '对方发来的表情包也是这样写的。你的表情包（名字：图里是什么）：',
            '对方发来的表情包保留名字标记；附有图片时按实际画面理解，名字只是标记。你的表情包（名字：图里是什么）：')
    replace(prompt, """     * [sendStickers] (its switch), and without, the person's stickers are told in words.
""", """     * [sendStickers] (its switch), and without, the person's stickers are told in words.
     * With [images], their sent stickers also carry artwork, independently of that switch.
""")

    repository = ai + "ChatRepository.kt"
    replace(repository,
            '            var withImages = endpointKey !in refusesImages && history.any { it.role == "user" && it.images != null }',
            """            val (stickers, sendStickers) = stickersFor(s)
            var withImages = endpointKey !in refusesImages && MessagePictures.hasImages(history, stickers)""")
    replace(repository, """            val heard = if (ToolGroup.Music in s.tools) runCatching { listening() }.getOrNull() else null
            val (stickers, sendStickers) = stickersFor(s)
            val calls = callsOutside(history)""", """            val heard = if (ToolGroup.Music in s.tools) runCatching { listening() }.getOrNull() else null
            val calls = callsOutside(history)""")
    replace(repository, "/** A sticker from the drawer: sent as its name, the way the TA sends them (StickerText). */",
            "/** A sticker keeps its token for display; Prompt resolves its artwork for the model. */")
    replace("app/src/main/java/com/cleo/cleos/data/Stickers.kt",
            "起个名字：TA 看不到图，是按名字认的", "给这张表情起个名字")
    replace("app/src/main/java/com/cleo/cleos/ui/chat/Stickers.kt",
            "TA 看不到图，是按名字和说明认的：名字起得像在说这张图，比如「兔子晕倒」。",
            "支持看图的模型会收到这张表情图片。名字方便选用；也可以补一句说明，供不能看图的模型理解。")
    replace("app/src/main/java/com/cleo/cleos/data/StickerText.kt",
            """ * place. Words, not a picture: a sticker costs the model what its name costs, and one that can't
 * look at pictures still knows what was sent.
""", """ * place. The prompt can attach the person's sent artwork like ordinary pictures; models that
 * cannot look at pictures still receive the name and description.
""")
