#!/usr/bin/env python3
"""0.38.40: AI may search public web images and set an avatar directly by HTTPS URL."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
ai = root/"app/src/main/java/com/cleo/cleos/ai"

def one(path, old, new):
    txt = path.read_text(encoding="utf-8")
    occurrences = txt.count(old)
    if occurrences != 1:
        raise RuntimeError(f"0.38.40 precondition in {path.name}: {old[:100]!r}, {occurrences} occurrences")
    path.write_text(txt.replace(old,new,1),encoding="utf-8")

copyfile(here/"OnlineAvatar_03840.kt",ai/"OnlineAvatar.kt")
copyfile(here/"GalleryToolBudget_03840.kt",ai/"GalleryToolBudget.kt")

tools=ai/"Tools.kt"
one(tools,
    '    val setMyAvatar = ToolSpec(\n',
    '''    val searchWebAvatar = ToolSpec(
        name = "search_web_avatar_images",
        groups = setOf(ToolGroup.Avatar),
        action = "网上找头像",
        description = "免费搜索公开互联网的头像图片（Openverse / Wikimedia Commons），根据你自己的喜好找候选图，返回图片真实HTTPS直链、标题、来源及许可证，还有最多3张直接供模型看的预览。找到喜欢的图后直接用 set_my_avatar 的 web_url 参数设置，不需要用户下载到手机相册，更不用先发到聊天。也可从已经授权的 Tavily/MCP 搜索工具得到真实图片HTTPS直链。不要把普通网页链接当成图片直链。网络源可能不支持下载，失败则换一张。",
        parameters = schema(required = listOf("query"),
            "query" to prop("string","想找的头像主题和风格，自行决定，如 moon fox illustrated avatar")),
    )

    val setMyAvatar = ToolSpec(
''')
one(tools,
    'description = "换你自己的头像。可以传 gallery_id 为 list_gallery_photos 返回的真实相册照片 ID；也可以传 image 选对方在聊天中发来的图片编号，或者一个 emoji。AI 自己从相册发送的图片，要优先用原照片 gallery_id，不能使用 latest（它只表示对方发来的图）。三者选一个。",',
    'description = "真正换你自己的头像。可以传 web_url 直接下载网络图片并设置头像，图片不需要提前保存至相册、不发送聊天消息；先用 search_web_avatar_images 搜索到图片直链或使用联网搜索工具给出的可直接下载的真实图片链接。也可用 gallery_id 指定相册照片、image 指对方发来的聊天图片编号、emoji 指表情。四者选一个，失败后换图片，不要谎称成功。",')
one(tools,
    '            "gallery_id" to prop("integer", "list_gallery_photos 得到的照片 ID；AI 查找或发送相册照片后可直接用这个 ID 换头像"),\n',
    '''            "gallery_id" to prop("integer", "list_gallery_photos 得到的照片 ID；AI 查找或发送相册照片后可直接用这个 ID 换头像"),
            "web_url" to prop("string", "网络图片本身的公网 HTTPS 直链（JPG/PNG/WebP），不是搜索结果网页；直接下载设置为头像，用户无需保存或发送"),
''')
one(tools, '        setMyAvatar,\n', '        setMyAvatar,\n        searchWebAvatar,\n')
one(tools,
    '    private val galleryAvatar: (suspend (companionId: Long, galleryId: Long) -> Boolean)? = null,\n',
    '''    private val galleryAvatar: (suspend (companionId: Long, galleryId: Long) -> Boolean)? = null,
    private val onlineAvatar: OnlineAvatarSource? = null,
''')
one(tools,
    '                ToolSpecs.setMyAvatar.name -> setMyAvatar(args, conversationId, companionId)\n',
    '''                ToolSpecs.setMyAvatar.name -> setMyAvatar(args, conversationId, companionId)
                ToolSpecs.searchWebAvatar.name -> searchWebAvatar(args)
''')
one(tools,
    '    private suspend fun setMyAvatar(a: JsonObject, conversationId: Long, companionId: Long): ToolOutcome {',
    '''    private suspend fun searchWebAvatar(a: JsonObject): ToolOutcome {
        val source = onlineAvatar ?: throw ToolFailure("本机在线搜图能力暂不可用。", "网络搜图不可用")
        val query = ToolArgs.text(a, "query")?.trim().orEmpty()
        val response = source.find(query)
        if (response.results.isEmpty()) throw ToolFailure(
            "这次没有检索到可直接使用的在线图片。请更换关键词或用已开启的联网搜索工具查找图片直链。",
            "暂未找到合适的网络图片"
        )
        val listing = response.results.mapIndexed { idx, image ->
            (idx + 1).toString() + ". " + image.title + "\\n图片直链：" + image.imageUrl +
                "\\n来源：" + image.sourcePage + "\\n许可：" + image.license
        }.joinToString("\\n")
        return ToolOutcome(
            "已检索到网络图片。请根据标题、来源和真实预览选择喜欢的一张，将图片直链传给 set_my_avatar 的 web_url，" +
                "不要让对方下载、保存相册或发送到聊天。预览图依次对应候选号：" +
                response.previews.joinToString(", ") { it.first.toString() } + "\\n" + listing,
            "找到" + response.results.size + "张可尝试的网络头像图片",
            galleryImages = response.previews.map { it.second },
        )
    }

    private suspend fun setMyAvatar(a: JsonObject, conversationId: Long, companionId: Long): ToolOutcome {''')
one(tools,
    '        val galleryId = ToolArgs.id(a["gallery_id"])\n',
    '''        val galleryId = ToolArgs.id(a["gallery_id"])
        val webUrl = ToolArgs.text(a, "web_url")?.trim().orEmpty()
''')
one(tools,
    '''        return when {
            galleryId != null && galleryId > 0L -> {''',
    '''        return when {
            webUrl.isNotEmpty() -> {
                val source = onlineAvatar ?: throw ToolFailure("本机无法从网上设置头像。", "网络头像功能不可用")
                if (!source.apply(companionId, webUrl))
                    throw ToolFailure("这张网络图片设置失败，请换一张可用的直链。", "网络图片设头像失败")
                ToolOutcome("已从互联网获取图片并真正换成自己的头像。无需用户保存到相册或发送聊天。", "已从网上更换头像")
            }
            galleryId != null && galleryId > 0L -> {''')
one(tools,
    '            else -> throw ToolFailure("从 image、gallery_id、emoji 中选一个作为新头像。", "没说换成什么")',
    '            else -> throw ToolFailure("从 web_url、image、gallery_id、emoji 中选一个作为新头像。", "没说换成什么")')

prompt=ai/"Prompt.kt"
one(prompt,
    '''if (ToolGroup.Avatar in tools) add("你可以用 set_my_avatar 真实更换自己的头像：可以使用对方发的聊天图片编号、一个表情，也可以用 list_gallery_photos 查到的相册 gallery_id 直接设置为头像。如果你是从相册拿图并由自己发送的，应该把原照片 ID 作为 gallery_id 传入，不要传 latest（latest 只指对方发送的聊天图片）。找到喜欢的照片后可直接更换，不必反复索要图片。")''',
    '''if (ToolGroup.Avatar in tools) add("你可以在互联网上亲自寻找喜欢的头像并直接给自己换上！收到‘你能自己在网上找头像吗’‘自己选一张喜欢的图片换头像’等自然聊天要求时，主动调用 search_web_avatar_images 搜索联网公开图片，利用真实图片预览和标题来源挑选满意的图，然后立刻调用 set_my_avatar(web_url=图片直链) 真正换上。网络搜图是应用内能力，不需要对方先把照片保存到系统相册，也不需要对方或你先把图片发到聊天里。若已通过 Tavily/MCP 网络搜索得到可直接下载的图片HTTPS链接，也能直接用 web_url。仅返回搜到的链接不算换好；必须等工具成功。原有用聊天图片 image、系统相册 gallery_id、emoji 换头像的方式依然可用。尊重用户明确指定的头像风格，不在无关话题里随便替换自己的头像。")''')

app=root/"app/src/main/java/com/cleo/cleos/CleosApp.kt"
one(app,
    'import com.cleo.cleos.ai.AiSelfAvatar\n',
    'import com.cleo.cleos.ai.AiSelfAvatar\nimport com.cleo.cleos.ai.OnlineAvatarService\n')
one(app,
    '        selfName = AiSelfName(db, companions),\n',
    '''        selfName = AiSelfName(db, companions),
        onlineAvatar = OnlineAvatarService(http, images, AiSelfAvatar(db, images, companions)),
''')
test=root/"app/src/test/java/com/cleo/cleos/ai/OnlineAvatarRulesTest.kt"
test.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"OnlineAvatarRulesTest_03840.kt",test)
gradle=root/"app/build.gradle.kts"
one(gradle,'versionName = "0.38.39"','versionName = "0.38.40"')
one(gradle,"versionCode = 62105","versionCode = 62106")
print("0.38.40/62106: search online public images, visual previews, download and apply avatar without chat/gallery detour")
