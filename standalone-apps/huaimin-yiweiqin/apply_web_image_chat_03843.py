#!/usr/bin/env python3
"""0.38.43: send HTTPS web images as real chat bubbles in one-to-one and group chats."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def change(path,old,new):
    s=path.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1: raise RuntimeError(f"03843 unexpected source {path.name}, got {n}: {old[:100]!r}")
    path.write_text(s.replace(old,new,1),encoding="utf-8")

ai=root/"app/src/main/java/com/cleo/cleos/ai"
copyfile(here/"OnlineAvatar_03843.kt", ai/"OnlineAvatar.kt")
tools=ai/"Tools.kt"
change(tools,
    '    val gallerySendIds: List<Long> = emptyList(),\n',
    '''    val gallerySendIds: List<Long> = emptyList(),
    /** URLs requested for a REAL chat bubble; ChatRepository must import/save first. */
    val webSendUrls: List<String> = emptyList(),
''')
change(tools,
    '    val setMyAvatar = ToolSpec(\n',
    '''    val sendWebImage = ToolSpec(
        name = "send_web_image",
        groups = setOf(ToolGroup.Avatar, ToolGroup.Gallery),
        action = "发送网络图片",
        description = "把已经找到的公网 HTTPS 图片真实发送到当前聊天，显示为可点击打开的图片气泡，而不是只发文字URL或Markdown图片。传网络图片本身的直链，不是网页地址；可使用 search_web_avatar_images 或已授权 Tavily/MCP 查询到的图片链接。下载验证、保存聊天私有图片并写入消息后才算成功。单聊群聊均可，图片无需先保存到系统相册，也不会自动设置头像。用户说‘发出来看看’‘给我看看这张图’时应调用本工具；不能只发网址后宣称图片已发送。",
        parameters = schema(required = listOf("url"),
            "url" to prop("string", "可直接下载 JPG、PNG、WebP 图片的公网 HTTPS 链接，不是网页或搜索结果页面")),
    )

    val setMyAvatar = ToolSpec(
''')
change(tools,'        searchWebAvatar,\n','        searchWebAvatar,\n        sendWebImage,\n')
change(tools,
    '                ToolSpecs.searchWebAvatar.name -> searchWebAvatar(args)\n',
    '                ToolSpecs.searchWebAvatar.name -> searchWebAvatar(args)\n                ToolSpecs.sendWebImage.name -> sendWebImage(args)\n')
change(tools,
    '    private suspend fun setMyAvatar(a: JsonObject, conversationId: Long, companionId: Long): ToolOutcome {',
    '''    /** ChatRepository controls the actual image-message write. */
    suspend fun importWebImageForChat(url: String): OnlineChatImage =
        (onlineAvatar ?: throw ToolFailure("本机没有网络图片服务。", "不能发送网络图片"))
            .importForChat(url)

    private fun sendWebImage(a: JsonObject): ToolOutcome {
        val url = ToolArgs.text(a, "url")?.trim().orEmpty()
        if (!OnlineAvatarRules.safeHttpsUrl(url))
            throw ToolFailure("需要真实可下载的公网 HTTPS 图片直链。请不要传搜索网页、本地地址或仅传图片标题。", "图片地址无效")
        if (onlineAvatar == null) throw ToolFailure("本机网络图片下载工具不可用。", "无法发送网络图片")
        return ToolOutcome("正在下载和保存网络图片。必须等待图片真正写入聊天后才能宣布发送成功。",
            "正在发送网络图片", webSendUrls = listOf(url))
    }

    private suspend fun setMyAvatar(a: JsonObject, conversationId: Long, companionId: Long): ToolOutcome {''')

chat=ai/"ChatRepository.kt"
change(chat,
    '''            // The AI must actually send a photo message, not merely view a thumbnail.''',
    '''            // A web link is not a sent image. Download privately, persist a real
            // MessageImages bubble, then report SUCCESS only after the DB write.
            if (outcome.webSendUrls.isNotEmpty()) {
                outcome = persistWebImages(conversationId, companionId, outcome.webSendUrls, wake, inCall)
            }
            // The AI must actually send a photo message, not merely view a thumbnail.''')
change(chat,
    '''                        val outcome = try {
                            when {''',
    '''                        var outcome = try {
                            when {''')
change(chat,
    '''                        withContext(NonCancellable) {
                            val at = stamp()
                            db.messages().insert(MessageEntity(
                                conversationId = conversationId, role = "tool", content = outcome.result,''',
    '''                        if (outcome.webSendUrls.isNotEmpty()) {
                            outcome = persistWebImages(conversationId, ta.id, outcome.webSendUrls,
                                background, null)
                        }
                        withContext(NonCancellable) {
                            val at = stamp()
                            db.messages().insert(MessageEntity(
                                conversationId = conversationId, role = "tool", content = outcome.result,''')
change(chat,
    '''    private suspend fun runOutside(conversationId: Long, tool: McpTool, call: ToolCall, live: StreamingReply, ai: String): ToolOutcome {''',
    '''    /** Shared by normal chat and group chat. Never claim a URL was sent as a photo. */
    private suspend fun persistWebImages(
        conversationId: Long,
        companionId: Long,
        urls: List<String>,
        wake: Boolean = false,
        inCall: Long? = null,
    ): ToolOutcome {
        val imported = ArrayList<MessageImage>()
        try {
            for (url in urls.take(3)) {
                val stored = tools.importWebImageForChat(url)
                imported += MessageImage(stored.file, stored.width, stored.height)
            }
            if (imported.isEmpty()) throw IllegalStateException("没有成功下载网络图片")
            withContext(NonCancellable) {
                val at = System.currentTimeMillis()
                db.messages().insert(MessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "",
                    images = MessageImages.encode(imported),
                    createdAt = at,
                    proactive = wake,
                    call = inCall,
                    senderCompanionId = companionId,
                ))
                db.conversations().touch(conversationId, at)
            }
            return ToolOutcome("已把${imported.size}张网络图片作为真正的图片消息发送到当前聊天，可以点击打开查看；不是文字链接。",
                "已发送${imported.size}张网络图片")
        } catch (cancel: CancellationException) {
            images.delete(imported.map { it.file })
            throw cancel
        } catch (error: Exception) {
            images.delete(imported.map { it.file })
            return ToolOutcome("网络图片没有发出去，不能声称成功。原因：" +
                (error.message ?: "图片下载、格式或存储失败") +
                "。可换一张可直接下载的 HTTPS 图片链接再试。",
                "发送网络图片失败")
        }
    }

    private suspend fun runOutside(conversationId: Long, tool: McpTool, call: ToolCall, live: StreamingReply, ai: String): ToolOutcome {''')

budget=ai/"GalleryToolBudget.kt"
change(budget,'            "search_web_avatar_images",\n',
    '            "search_web_avatar_images",\n            "send_web_image",\n')
prompt=ai/"Prompt.kt"
change(prompt,
    '        if (ToolGroup.Weather in tools) add("问到天气时用工具查，不要凭印象说。")',
    '''        if (ToolGroup.Avatar in tools || ToolGroup.Gallery in tools)
            add("你能真正发送互联网上的图片！用户说‘发出来看看’‘把刚才那张网络图片发给我’‘给我看这图’时，必须调用 send_web_image(url=图片直链)，它会保存为聊天中可以打开的真实图片气泡；只回文字URL、Markdown链接、截图或表情都不算发送网络图片。若刚刚通过 Tavily 或在线搜图获取了真实HTTPS图片直链，直接复用；无需先保存相册或让对方发给你。设置头像用 set_my_avatar(web_url)，发送到聊天用 send_web_image(url)，是两个独立动作：不能把换头像成功说成图片已发送。网络图片下载失败就真实说明原因并换链接，不能声称已发成功。")
        if (ToolGroup.Weather in tools) add("问到天气时用工具查，不要凭印象说。")''')
unit=root/"app/src/test/java/com/cleo/cleos/ai/OnlineWebChatRulesTest.kt"
unit.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"OnlineWebChatRulesTest_03843.kt",unit)
gradle=root/"app/build.gradle.kts"
change(gradle, 'versionName = "0.38.42"', 'versionName = "0.38.43"')
change(gradle, "versionCode = 62108", "versionCode = 62109")
print("0.38.43/62109: secure network images become real chat bubbles, in private and group chats, not just URL")
