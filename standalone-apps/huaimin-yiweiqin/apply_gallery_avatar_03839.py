#!/usr/bin/env python3
"""0.38.39: set_my_avatar accepts a real Android gallery media ID, not only user chat image refs."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
ai=root/"app/src/main/java/com/cleo/cleos/ai"

def replace(path, old, new):
    s=path.read_text(encoding="utf-8")
    if s.count(old)!=1:
        raise RuntimeError(f"Unexpected source in {path.name}: {old[:110]!r} (found {s.count(old)})")
    path.write_text(s.replace(old,new,1),encoding="utf-8")

tools=ai/"Tools.kt"
replace(tools,
    'description = "换你自己的头像：用对方在这段聊天里发来的一张图（取正中间的方块），或者一个表情。两者给一个。",',
    'description = "换你自己的头像。可以传 gallery_id 为 list_gallery_photos 返回的真实相册照片 ID；也可以传 image 选对方在聊天中发来的图片编号，或者一个 emoji。AI 自己从相册发送的图片，要优先用原照片 gallery_id，不能使用 latest（它只表示对方发来的图）。三者选一个。",')
replace(tools,
    '''            "image" to prop("string", "图的编号，比如 #45-1；写 latest 就是对方最近发来的那张"),
            "emoji" to prop("string", "一个表情，比如 🌙"),''',
    '''            "image" to prop("string", "对方发送的聊天图片编号（例如 #45-1）；latest 仅指对方发来的最近图片，不是 AI 发送的相册图片"),
            "gallery_id" to prop("integer", "list_gallery_photos 得到的照片 ID；AI 查找或发送相册照片后可直接用这个 ID 换头像"),
            "emoji" to prop("string", "一个表情，比如 🌙"),''')
replace(tools,
    '    private val avatar: SelfAvatar? = null,\n',
    '''    private val avatar: SelfAvatar? = null,
    private val galleryAvatar: (suspend (companionId: Long, galleryId: Long) -> Boolean)? = null,
''')
replace(tools,
    '''        val image = ToolArgs.text(a, "image")?.trim().orEmpty()
        val emoji = ToolArgs.text(a, "emoji")?.trim().orEmpty()
        return when {''',
    '''        val image = ToolArgs.text(a, "image")?.trim().orEmpty()
        val galleryId = ToolArgs.id(a["gallery_id"])
        val emoji = ToolArgs.text(a, "emoji")?.trim().orEmpty()
        return when {
            galleryId != null && galleryId > 0L -> {
                // Gallery authorization must be checked on every operation, not only when
                // the model first saw the media ID. The phone user controls access.
                val matching = userGallery().sendablePhotos(listOf(galleryId))
                if (galleryId !in matching)
                    throw ToolFailure("相册中找不到这张授权照片（ID=$galleryId），请重新检索。", "找不到那张相册照片")
                val update = galleryAvatar ?: throw ToolFailure("本机相册头像功能不可用。", "不能换头像")
                if (!update(companionId, galleryId))
                    throw ToolFailure("这张相册照片无法设为头像，请换一张试试。", "设置相册头像失败")
                ToolOutcome("换好了：已使用相册照片 $galleryId 作为你自己的头像。", "换了新头像")
            }''')
replace(tools,
    '''            else -> throw ToolFailure("image 和 emoji 给一个。", "没说换成什么")''',
    '''            else -> throw ToolFailure("从 image、gallery_id、emoji 中选一个作为新头像。", "没说换成什么")''')
app=root/"app/src/main/java/com/cleo/cleos/CleosApp.kt"
replace(app,
    '''        avatar = AiSelfAvatar(db, images, companions),
        selfName = AiSelfName(db, companions),''',
    '''        avatar = AiSelfAvatar(db, images, companions),
        galleryAvatar = { actor, photoId ->
            // Import a private image copy; never write to or delete the system photo.
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId
            )
            val stored = images.import(uri, maxEdge = 2048, prefix = "ai-gallery-avatar-")
            AiSelfAvatar(db, images, companions).usePicture(actor, stored.file)
        },
        selfName = AiSelfName(db, companions),''')
# No 5,000-photo pagination ceiling: large authorized libraries must stay browsable.
replace(tools,
    'val offset = a["offset"]?.jsonPrimitive?.intOrNull?.coerceIn(0,5000) ?: 0',
    'val offset = a["offset"]?.jsonPrimitive?.intOrNull?.coerceIn(0,1000000) ?: 0')
prompt=ai/"Prompt.kt"
replace(prompt,
    'if (ToolGroup.Avatar in tools) add("你可以用 set_my_avatar 换自己的头像：用对方发来的一张图，或者一个表情。")',
    'if (ToolGroup.Avatar in tools) add("你可以用 set_my_avatar 真实更换自己的头像：可以使用对方发的聊天图片编号、一个表情，也可以用 list_gallery_photos 查到的相册 gallery_id 直接设置为头像。如果你是从相册拿图并由自己发送的，应该把原照片 ID 作为 gallery_id 传入，不要传 latest（latest 只指对方发送的聊天图片）。找到喜欢的照片后可直接更换，不必反复索要图片。")')
print("PASS: set_my_avatar accepts gallery_id after verifying Android gallery permission")
