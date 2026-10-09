#!/usr/bin/env python3
"""v0.38.1: connect actual local Moments to normal/group-chat tools; keep existing data and privacy."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
base = Path("app/src/main/java/com/cleo/cleos")

def once(file: str, before: str, after: str, label: str):
    path = root / file
    current = path.read_text(encoding="utf-8")
    hits = current.count(before)
    if hits != 1:
        raise RuntimeError(f"0.38.1 {label}: expected 1 anchor in {file}; got {hits}")
    path.write_text(current.replace(before, after, 1), encoding="utf-8")

bridge = root / base / "ai/MomentsChatBridge.kt"
bridge.parent.mkdir(parents=True, exist_ok=True)
copyfile(here / "MomentsChatBridge_0381.kt", bridge)

ai = str(base / "ai/Tools.kt")
app = str(base / "CleosApp.kt")

declarations = '''    // Internal Cleos Moments, not WeChat. These are real data operations.
    val readFeed = ToolSpec(
        name = "read_feed", groups = setOf(ToolGroup.Messages), action = "查看朋友圈",
        description = "读取 Cleos 应用内真实朋友圈。用户说看看刚发的朋友圈、刚发的动态、去朋友圈看看时必须先调用。默认 author=user；返回真实动态 ID、发布时间、正文、点赞及评论。图片内容未送给模型时不可猜测。",
        parameters = schema(
            "author" to prop("string", "user：用户发的；all：所有可见动态；self：我自己发的。默认 user"),
            "limit" to prop("integer", "读取最近多少条，默认 10；需要更多可再次调用"),
            "post_id" to prop("string", "可选：要读取的具体动态 ID，必须来自工具结果"),
        ),
    )
    val likeFeed = ToolSpec(
        name = "like_feed", groups = setOf(ToolGroup.Messages), action = "点赞朋友圈",
        description = "对真实且当前可见的 Cleos 朋友圈动态执行点赞并持久保存。用户要求点赞时先 read_feed 得到 post_id；绝不能假称已经点赞。",
        parameters = schema(required = listOf("post_id"),
            "post_id" to prop("string", "通过 read_feed 读取到的真实动态 ID")),
    )
    val commentFeed = ToolSpec(
        name = "comment_feed", groups = setOf(ToolGroup.Messages), action = "评论朋友圈",
        description = "对真实且当前可见的 Cleos 朋友圈动态发表评论并保存为当前 AI 角色，非口头模拟。用户要求评论时先 read_feed。评论应符合角色人格并结合真实正文。",
        parameters = schema(required = listOf("post_id", "text"),
            "post_id" to prop("string", "通过 read_feed 读取到的真实动态 ID"),
            "text" to prop("string", "要真实发布的评论正文")),
    )
    val publishFeed = ToolSpec(
        name = "publish_feed", groups = setOf(ToolGroup.Messages), action = "发表朋友圈",
        description = "以当前 AI 身份在 Cleos 内实际发布一条朋友圈。只在用户明确要求发表时使用；自主定时发表使用后台管理器。",
        parameters = schema(required = listOf("text"),
            "text" to prop("string", "要发布的实际正文")),
    )
    val feedNames = setOf(readFeed.name,likeFeed.name,commentFeed.name,publishFeed.name)
    val feedTools = listOf(readFeed,likeFeed,commentFeed,publishFeed)

'''
once(ai, '    val all = listOf(\n', declarations+'    val all = listOf(\n', 'feed tool definitions')
once(ai, '    val all = listOf(\n        ', '    val all = listOf(\n        readFeed, likeFeed, commentFeed, publishFeed,\n        ', 'feed tool registrations')
once(ai, '    fun specs(groups: Set<ToolGroup>): List<ToolSpec> = ToolSpecs.offered(groups)',
     '    fun specs(groups: Set<ToolGroup>): List<ToolSpec> = (ToolSpecs.offered(groups) + ToolSpecs.feedTools).distinctBy { it.name }',
     'make feed available in every chat')
needle = '''        if (spec.groups.none { it in settings.tools }) return failed("对方在设置里关掉了这项功能，现在用不了。", "设置里关着")'''
once(ai, needle,
     '''        // Local feed tools are user-available even when a generic tool group is off.
        // Per-post audience permissions are still revalidated by MomentsStore.
        if (call.name in ToolSpecs.feedNames) {
            val args = ToolArgs.parse(call.arguments)
                ?: return failed("朋友圈工具参数不是合法 JSON。", "参数写错了")
            return MomentsChatBridge.execute(call.name,args,companionId)
        }
'''+needle, 'dispatch real feed calls')

once(app,
     '''    val moments = com.cleo.cleos.data.MomentsStore(context, images)''',
     '''    val moments = com.cleo.cleos.data.MomentsStore(context, images).also {
        com.cleo.cleos.ai.MomentsChatBridge.store = it
    }''', 'register the single on-device feed store')

gradle = "app/build.gradle.kts"
once(gradle, 'versionName = "0.38.0"', 'versionName = "0.38.1"', 'name')
once(gradle, 'versionCode = 62066', 'versionCode = 62067', 'code')
print("v0.38.1 / 62067: in-app Moments feed tools available to chat; real reads/writes; per-post visibility retained")
