#!/usr/bin/env python3
"""0.38.32: opted-in full/selected Android gallery access, AI review, system-confirmed trash."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
java=root/"app/src/main/java/com/cleo/cleos"
def edit(path,old,new,why):
    s=path.read_text(encoding="utf-8")
    count=s.count(old)
    if count!=1: raise RuntimeError(f"{why}: expected unique predecessor, got {count}: {old[:90]!r}")
    path.write_text(s.replace(old,new,1),encoding="utf-8")
copyfile(here/"GalleryAccess_03832.kt",java/"ai/GalleryAccess.kt")
copyfile(here/"GalleryManager_03832.kt",java/"ui/settings/GalleryManager.kt")
tools=java/"ai/Tools.kt"
edit(tools,"enum class ToolGroup {","enum class ToolGroup { Gallery,","ToolGroup permission")
edit(tools,
'''data class ToolOutcome(val result: String, val note: String, val request: SecretRequest? = null)''',
'''data class ToolOutcome(
    val result: String, val note: String,
    val request: SecretRequest? = null,
    /** Actual visual input for the CURRENT turn only; do not persist base64 in chat history. */
    val galleryImages: List<String> = emptyList(),
)''',"ephemeral vision data")
specs='''    val galleryList = ToolSpec(
        name = "list_gallery_photos",
        groups = setOf(ToolGroup.Gallery),
        action = "检索相册",
        description = "按文件名关键词、相册名称列出对方已授权手机相册照片的编号和元数据。无法按人物/内容猜照片；要看画面请随后调用 inspect_gallery_photos。结果分页，不能一次遍历上传相册。",
        parameters = schema(
            "query" to prop("string","文件名包含的词，可留空"),
            "album" to prop("string","精确相册名称，可留空"),
            "offset" to prop("integer","从第几条开始，默认0"),
            "limit" to prop("integer","最多30"),
        ),
    )
    private val galleryIds = buildJsonObject {
        put("type","array")
        putJsonObject("items") { put("type","integer") }
        put("description","先通过 list_gallery_photos 得到的照片 ID 数组")
    }
    val galleryInspect = ToolSpec(
        name = "inspect_gallery_photos",
        groups = setOf(ToolGroup.Gallery),
        action = "看相册照片",
        description = "按 ID 读取最多3张你有权访问的照片，缩放后作为视觉输入给AI。只有支持视觉的模型真的接收了图像才能解释照片内容；不支持时必须如实说明，不能根据文件名编造。不会下载到第三方相册服务器。",
        parameters = schema(required=listOf("ids"),"ids" to galleryIds),
    )
    val galleryCleanup = ToolSpec(
        name = "prepare_gallery_cleanup",
        groups = setOf(ToolGroup.Gallery),
        action = "整理照片建议",
        description = "仅把最多50张待清理照片加入审核队列，不删除、不移动。用户必须在设置→能做的事→AI相册管理中看到缩略图并手动勾选；Android系统还会单独确认移入回收站。不要宣称删除完成。",
        parameters = schema(required=listOf("ids"),"ids" to galleryIds),
    )
'''
edit(tools,"    val all = listOf(",specs+"    val all = listOf(","register gallery tool definitions")
edit(tools,"        musicControl,\n","        musicControl,\n        galleryList,\n        galleryInspect,\n        galleryCleanup,\n","tool registry")
edit(tools,
'''    private val music: MusicSource? = null,''',
'''    private val music: MusicSource? = null,
    private val gallery: GallerySource? = null,''',"gallery tool injection")
edit(tools,
'''                ToolSpecs.musicControl.name -> musicControl(args)''',
'''                ToolSpecs.musicControl.name -> musicControl(args)
                ToolSpecs.galleryList.name -> galleryList(args)
                ToolSpecs.galleryInspect.name -> galleryInspect(args)
                ToolSpecs.galleryCleanup.name -> galleryCleanup(args)''',"dispatch gallery operations")

functions='''    private fun userGallery(): GallerySource {
        val source = gallery ?: throw ToolFailure("这台设备未提供相册工具。","相册服务不可用")
        if (source.access() == GalleryAccess.None)
            throw ToolFailure("系统未授予相册权限。请用户自行前往设置→能做的事→AI访问相册开启授权。","未获系统相册授权")
        return source
    }
    private fun photoIds(a: JsonObject, max: Int): List<Long> {
        val arr = runCatching { a["ids"]?.jsonArray }.getOrNull()
            ?: throw ToolFailure("请传入 ids 数组；ID 来自 list_gallery_photos。","缺少照片编号")
        val ids = arr.mapNotNull { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() }
            .filter { it > 0 }.distinct()
        if (ids.isEmpty() || arr.size > max)
            throw ToolFailure("一次只允许处理1到" + max + "张，请分批操作。","超过相册操作上限")
        return ids
    }
    private suspend fun galleryList(a: JsonObject): ToolOutcome {
        val source = userGallery()
        val query = ToolArgs.text(a,"query").orEmpty()
        val album = ToolArgs.text(a,"album").orEmpty()
        val offset = ToolArgs.number(a,"offset")?.toInt()?.coerceIn(0,5000) ?: 0
        val limit = ToolArgs.number(a,"limit")?.toInt()?.coerceIn(1,30) ?: 20
        val photos = source.listPhotos(query,album,offset,limit)
        return ToolOutcome(GalleryResultFormatter.display(photos,source.access()),
            "检索了" + photos.size + "张已授权照片的元数据")
    }
    private suspend fun galleryInspect(a: JsonObject): ToolOutcome {
        val source = userGallery()
        val images = source.inspectPhotos(photoIds(a,3))
        if (images.isEmpty()) throw ToolFailure(
            "无法读取这些照片，请检查相册权限或照片是否已移走。","无法查看选中照片")
        val info = images.joinToString("\\n") { p ->
            "ID=" + p.photo.id + "，文件名=" + p.photo.title + "，相册=" + p.photo.album
        }
        return ToolOutcome(
            "以下是已授权的照片视觉输入，只有模型能读取本轮真实图像时才可描述内容。" +
                " 如不支持视觉必须说明，不能凭文件名猜测：\\n" + info,
            "准备查看" + images.size + "张照片",
            galleryImages = images.map { it.dataUrl },
        )
    }
    private suspend fun galleryCleanup(a: JsonObject): ToolOutcome {
        val count = userGallery().proposeTrash(photoIds(a,50))
        if (count == 0) throw ToolFailure("没有可审核的照片，可能照片已删除或权限已变化。","无可用照片")
        return ToolOutcome(
            "已将" + count + "张照片加入本机待审核建议（未删除）。请用户亲自打开" +
                "设置→能做的事→AI相册管理，逐张预览勾选，再让 Android 系统确认移入回收站。",
            "有" + count + "张照片等待用户审核，尚未删除",
        )
    }

'''
edit(tools,"    private suspend fun musicControl(",functions+"    private suspend fun musicControl(","gallery implementation")
# Support array parsing but avoid modifying existing ToolArgs.
edit(tools,"import kotlinx.serialization.json.JsonObject",
"import kotlinx.serialization.json.JsonObject\nimport kotlinx.serialization.json.jsonArray\nimport kotlinx.serialization.json.jsonPrimitive","gallery array imports")
# ToolArgs.number may be absent across the predecessor, use direct JSON numeric primitive.
s=tools.read_text(encoding="utf-8")
s=s.replace('ToolArgs.number(a,"offset")?.toInt()?.coerceIn(0,5000)',
            'a["offset"]?.jsonPrimitive?.intOrNull?.coerceIn(0,5000)')
s=s.replace('ToolArgs.number(a,"limit")?.toInt()?.coerceIn(1,30)',
            'a["limit"]?.jsonPrimitive?.intOrNull?.coerceIn(1,30)')
tools.write_text(s,encoding="utf-8")

app=java/"CleosApp.kt"
edit(app,
'''        music = music,''',
'''        music = music,
        gallery = com.cleo.cleos.ai.DeviceGallery(context),''',"instantiate gallery")
settings=java/"ui/settings/SharedPages.kt"
edit(settings,
'''import com.cleo.cleos.ai.ToolGroup''',
'''import com.cleo.cleos.ai.ToolGroup
import com.cleo.cleos.ai.GalleryPermissions
import com.cleo.cleos.ai.GalleryAccess
import com.cleo.cleos.ai.GalleryReviewQueue''',"settings imports")
edit(settings,
'''    fun on(group: ToolGroup) = group in settings.tools''',
'''    fun on(group: ToolGroup) = group in settings.tools
    var galleryOpen by remember { mutableStateOf(false) }
    var galleryHint by remember { mutableStateOf<String?>(null) }
    val askGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (GalleryPermissions.access(context) != GalleryAccess.None) {
            galleryHint = null
            vm.setTool(ToolGroup.Gallery, true)
        } else {
            galleryHint = "系统没有授予相册访问权限。可以在系统设置中选择全部照片或部分照片。"
        }
    }
    if (galleryOpen) GalleryManagerDialog { galleryOpen = false }''',"settings gallery state")
edit(settings,
'''        ExplainedSwitch("换自己的头像",''',
'''        ExplainedSwitch(
            "AI 访问与整理相册",
            "允许 TA 在聊天时搜索、看图并建议清理已授权的照片",
            "关闭时立即停止新的AI相册读取；Android 可选择全部照片或部分照片。" +
                "每次看图最多3张缩略图，缩略图会发送到你设置的AI模型服务分析，并非纯本地处理。" +
                "AI不能直接删除，必须逐张人工审核且经系统确认移入回收站。",
            on(ToolGroup.Gallery),
        ) { turnOn ->
            if (!turnOn) {
                GalleryReviewQueue.clear()
                vm.setTool(ToolGroup.Gallery, false)
            } else if (GalleryPermissions.access(context) != GalleryAccess.None) {
                vm.setTool(ToolGroup.Gallery, true)
            } else askGallery.launch(GalleryPermissions.requests)
        }
        if (on(ToolGroup.Gallery)) {
            Under {
                TextButton(onClick = { galleryOpen = true }) {
                    Text("打开 AI 相册管理 · 审核整理与回收站")
                }
            }
        }
        galleryHint?.let {
            Under { Text(it, fontSize = 12.sp, color = palette.contentSecondary) }
        }
        RowDivider(inset = 0.dp)
        ExplainedSwitch("换自己的头像",''',"gallery switch and human review entry")
pages=java/"ui/settings/SettingsPages.kt"
edit(pages,"    ToolGroup.Weather, ToolGroup.Location,",
"    ToolGroup.Gallery, ToolGroup.Weather, ToolGroup.Location,","capability count")
chat=java/"ai/ChatRepository.kt"
edit(chat,
'''        for (call in others) {
            val outer = outside.firstOrNull { it.fnName == call.name }''',
'''        val galleryVision = ArrayList<ApiMessage>()
        for (call in others) {
            val outer = outside.firstOrNull { it.fnName == call.name }''',"transient gallery vision attachments")
edit(chat,
'''            results[call.id] = ApiMessage("tool", outcome.result, toolCallId = call.id)''',
'''            results[call.id] = ApiMessage("tool", outcome.result, toolCallId = call.id)
            if (outcome.galleryImages.isNotEmpty()) {
                galleryVision += ApiMessage(
                    role = "user",
                    content = "以下图片由已获用户授权的本机相册工具提供，并非用户新发言。" +
                        "请只根据真实图像回答；若当前模型不能看图须明确说明。",
                    images = outcome.galleryImages,
                )
            }''',"send only requested thumbnails into visual model turn")
edit(chat,
'''        return step.message.toolCalls.mapNotNull { results[it.id] }''',
'''        return step.message.toolCalls.mapNotNull { results[it.id] } + galleryVision''',"append ephemeral vision only in model call")
edit(chat,
'''                        val results = runTools(conversationId, step, s.copy(tools = groups), ta.id, outside, ta.name)''',
'''                        val results = runTools(conversationId, step, s.copy(tools = groups), ta.id, outside, ta.name)
                        if (results.any { it.images.isNotEmpty() }) withImages = true''',
"safe multimodal refusal handling")

manifest=root/"app/src/main/AndroidManifest.xml"
edit(manifest,
'''    <uses-permission android:name="android.permission.RECORD_AUDIO" />''',
'''    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <!-- Full or selected photo permission is always chosen by the Android user. -->
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />''',
"Android media permissions")
gradle=root/"app/build.gradle.kts"
edit(gradle,'versionName = "0.38.31"','versionName = "0.38.32"',"version name")
edit(gradle,'versionCode = 62097','versionCode = 62098',"version code")
print("0.38.32: gallery permission opt-in, AI thumbnail inspection, in-app approval and OS trash")
