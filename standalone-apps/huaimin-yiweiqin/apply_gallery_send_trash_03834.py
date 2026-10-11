#!/usr/bin/env python3
"""0.38.34: AI can SEND actual gallery images; proposed trash is reviewed in chat."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
src=r/"app/src/main/java/com/cleo/cleos"
def change(p,old,new,why):
    s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1: raise RuntimeError(f"{why}: {n} matching anchors")
    p.write_text(s.replace(old,new,1),encoding="utf-8")

gallery=src/"ai/GalleryAccess.kt"
change(gallery,
    "    suspend fun inspectPhotos(ids: List<Long>): List<GalleryPicture>",
    """    /** Validate every photo ID against currently accessible MediaStore records. */
    suspend fun sendablePhotos(ids: List<Long>): List<Long>
    suspend fun inspectPhotos(ids: List<Long>): List<GalleryPicture>""",
    "gallery send interface")
change(gallery,
    "    override suspend fun inspectPhotos(ids: List<Long>): List<GalleryPicture> =",
    '''    override suspend fun sendablePhotos(ids: List<Long>): List<Long> =
        withContext(Dispatchers.IO) {
            if (access() == GalleryAccess.None) return@withContext emptyList()
            ids.distinct().take(3).filter { id ->
                id > 0L && runCatching { photo(id) != null }.getOrDefault(false)
            }
        }

    override suspend fun inspectPhotos(ids: List<Long>): List<GalleryPicture> =''',
    "validate readable send IDs")
change(gallery,
    '''    val ids = pending.asStateFlow()
    fun propose(ids: List<Long>) { pending.value = ids.distinct().take(50) }
    fun clear() { pending.value = emptyList() }''',
    '''    val ids = pending.asStateFlow()
    private val pendingOwner = MutableStateFlow<Long?>(null)
    val owner = pendingOwner.asStateFlow()
    fun propose(ids: List<Long>) {
        pendingOwner.value = null
        pending.value = ids.distinct().take(50)
    }
    fun assign(conversationId: Long) {
        pendingOwner.value = conversationId.takeIf { it > 0 }
    }
    fun clear() {
        pending.value = emptyList()
        pendingOwner.value = null
    }''',
    "scope review to requesting chat")

tools=src/"ai/Tools.kt"
change(tools,
    '''    val galleryCleanup = ToolSpec(''',
    '''    val gallerySend = ToolSpec(
        name = "send_gallery_photos",
        groups = setOf(ToolGroup.Gallery),
        action = "发送相册图片",
        description = "用户让你从相册拿照片发给他时，必须用这个工具！先用 list_gallery_photos 获取真实照片ID，再传1到3个ID。它会把选中的照片作为真正的图片气泡发送到当前聊天，原相册照片保留。不能用 inspect_gallery_photos 代替发送，也不能声称只能看不能发送。若没有照片ID，先检索后发送。",
        parameters = schema(required = listOf("ids"), "ids" to galleryIds),
    )
    val galleryCleanup = ToolSpec(''',
    "send-gallery tool schema")
change(tools,
    '''        galleryInspect,
        galleryCleanup,''',
    '''        galleryInspect,
        gallerySend,
        galleryCleanup,''',
    "gallery tool registration")
change(tools,
    '''                ToolSpecs.galleryInspect.name -> galleryInspect(args)
                ToolSpecs.galleryCleanup.name -> galleryCleanup(args)''',
    '''                ToolSpecs.galleryInspect.name -> galleryInspect(args)
                ToolSpecs.gallerySend.name -> gallerySend(args)
                ToolSpecs.galleryCleanup.name -> galleryCleanup(args, conversationId)''',
    "dispatch photo send and scoped cleanup")
change(tools,
    '''    val galleryImages: List<String> = emptyList(),''',
    '''    val galleryImages: List<String> = emptyList(),
    /** MediaStore IDs allowed to be copied into a real assistant message by ChatRepository. */
    val gallerySendIds: List<Long> = emptyList(),''',
    "tool outcome for send")
change(tools,
    '''    private suspend fun galleryCleanup(a: JsonObject): ToolOutcome {''',
    '''    private suspend fun gallerySend(a: JsonObject): ToolOutcome {
        val source = userGallery()
        val found = source.sendablePhotos(photoIds(a, 3))
        if (found.isEmpty()) throw ToolFailure(
            "没有找到能发送的照片，请检查照片ID和系统授权，或重新检索。",
            "没有可发送照片",
        )
        return ToolOutcome(
            "请求发送照片 " + found.joinToString(",") +
                "；请等待图片实际保存到聊天后再宣布发送成功。",
            "正在把" + found.size + "张相册照片发送到聊天",
            gallerySendIds = found,
        )
    }

    private suspend fun galleryCleanup(a: JsonObject, conversationId: Long): ToolOutcome {''',
    "tool send implementation and cleanup chat argument")
change(tools,
    '''        val count = userGallery().proposeTrash(photoIds(a,50))
        if (count == 0)''',
    '''        val count = userGallery().proposeTrash(photoIds(a,50))
        if (count > 0) GalleryReviewQueue.assign(conversationId)
        if (count == 0)''',
    "label cleanup queue to actual chat")
change(tools,
    '''            "已将" + count + "张照片加入本机待审核建议（未删除）。请用户亲自打开" +
                "设置→能做的事→AI相册管理，逐张预览勾选，再让 Android 系统确认移入回收站。",''',
    '''            "已将" + count + "张照片加入本机待审核建议（未删除）。" +
                "聊天输入框上方会出现「查看待清理照片」入口，" +
                "请用户在该页面逐张预览勾选，再让 Android 系统确认移入回收站。",''',
    "explain in-chat deletion review")

chat=src/"ai/ChatRepository.kt"
change(chat,
    '''            val outcome = try {
                if (call.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name))
                    searchHistoryCall(conversationId, call)
                else if (outer != null) runOutside(conversationId, outer, call, live, ai)
                else tools.run(call, s, conversationId, companionId)
''',
    '''            var outcome = try {
                if (call.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name))
                    searchHistoryCall(conversationId, call)
                else if (outer != null) runOutside(conversationId, outer, call, live, ai)
                else tools.run(call, s, conversationId, companionId)
''',
    "make outcome mutable for error feedback")
change(chat,
    '''            // The tool has acted (a todo exists now), so its result is stored even if''',
    '''            // The AI must actually send a photo message, not merely view a thumbnail.
            // Keep the source gallery photo untouched and independently preserve the
            // imported copy in private chat storage (normal image backup/restore path).
            if (outcome.gallerySendIds.isNotEmpty()) {
                val imported = ArrayList<MessageImage>()
                try {
                    for (id in outcome.gallerySendIds.take(3)) {
                        val uri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )
                        val stored = images.import(uri, maxEdge = 2048, prefix = "ai-gallery-")
                        imported += MessageImage(stored.file, stored.width, stored.height)
                    }
                    if (imported.isEmpty()) throw IllegalStateException("没有成功导入图片")
                    withContext(NonCancellable) {
                        val at = System.currentTimeMillis()
                        db.messages().insert(
                            MessageEntity(
                                conversationId = conversationId,
                                role = "assistant",
                                content = "",
                                images = MessageImages.encode(imported),
                                createdAt = at,
                                proactive = wake,
                                call = inCall,
                            ),
                        )
                        db.conversations().touch(conversationId, at)
                    }
                    outcome = outcome.copy(
                        result = "已向当前聊天发送了" + imported.size +
                            "张真实图片消息，原相册照片未修改。",
                        note = "已发送" + imported.size + "张相册照片",
                    )
                } catch (e: CancellationException) {
                    images.delete(imported.map { it.file })
                    throw e
                } catch (e: Exception) {
                    images.delete(imported.map { it.file })
                    outcome = outcome.copy(
                        result = "图片没有发送成功：" + (e.message ?: "相册访问或图片解码出错") +
                            "。请重新检查系统相册授权，不能声称已经发出。",
                        note = "发送相册图片失败",
                    )
                }
            }
            // The tool has acted (a todo exists now), so its result is stored even if''',
    "physically send photo bubbles and truthfully report failure")

screen=src/"ui/chat/ChatScreen.kt"
change(screen,
    '''    var sentCount by remember { mutableIntStateOf(0) }''',
    '''    var sentCount by remember { mutableIntStateOf(0) }
    // Review is attached to the chat that requested it, not to unrelated TAs.
    val galleryReviewIds by com.cleo.cleos.ai.GalleryReviewQueue.ids.collectAsState()
    val galleryReviewOwner by com.cleo.cleos.ai.GalleryReviewQueue.owner.collectAsState()
    var galleryReviewOpen by remember { mutableStateOf(false) }''',
    "chat review UI state")
change(screen,
    '''            ChatInputBar(
                backdrop = page,''',
    '''            if (galleryReviewIds.isNotEmpty() && galleryReviewOwner == state.conversationId) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = inputBottom +
                            with(density) { inputHeight.toDp() } + 8.dp)
                        .background(Color(0xFFE6F0FA), RoundedCornerShape(18.dp))
                        .clickable { galleryReviewOpen = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "AI 建议检查 " + galleryReviewIds.size + " 张照片 · 查看并决定是否删除",
                        fontSize = 13.sp,
                        color = Color(0xFF294765),
                    )
                }
            }
            ChatInputBar(
                backdrop = page,''',
    "in-chat human review entry")
change(screen,
    '''    if (locationPickerOpen) {
        ChatLocationPicker(''',
    '''    if (galleryReviewOpen) {
        com.cleo.cleos.ui.settings.GalleryManagerDialog {
            galleryReviewOpen = false
        }
    }
    if (locationPickerOpen) {
        ChatLocationPicker(''',
    "open approved manual trash manager directly from chat")
gradle=r/"app/build.gradle.kts"
change(gradle,'versionName = "0.38.33"','versionName = "0.38.34"',"version")
change(gradle,'versionCode = 62099','versionCode = 62100',"build number")
print("0.38.34/62100: AI can send image bubbles; delete suggestions reviewed in chat + Android system trash")
