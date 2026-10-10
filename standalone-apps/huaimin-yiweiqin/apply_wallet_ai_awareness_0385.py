#!/usr/bin/env python3
"""v0.38.5: link real wallet entries to each model's single/group chat context and claim tool."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
base=Path("app/src/main/java/com/cleo/cleos")
def once(path,old,new,label):
    target=root/path
    content=target.read_text(encoding="utf-8")
    count=content.count(old)
    if count!=1: raise RuntimeError(f"{label}: expected 1 anchor, got {count}: {path}")
    target.write_text(content.replace(old,new,1),encoding="utf-8")

bridge=root/base/"ai/WalletChatBridge.kt"
bridge.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"WalletChatBridge_0385.kt",bridge)
test=root/"app/src/test/java/com/cleo/cleos/ai/WalletChatBridgeTest.kt"
test.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"WalletChatBridgeTest_0385.kt",test)

once(base/"CleosApp.kt",
'''    val wallet = com.cleo.cleos.data.VirtualWalletStore(context)''',
'''    val wallet = com.cleo.cleos.data.VirtualWalletStore(context).also {
        com.cleo.cleos.ai.WalletChatBridge.store = it
        com.cleo.cleos.ai.WalletChatBridge.database = db
    }''',
"register wallet store and live membership database")

once(base/"ai/Tools.kt",
'''    val feedNames = setOf(readFeed.name,likeFeed.name,commentFeed.name,publishFeed.name)''',
'''    val claimVirtualRedPacket = ToolSpec(
        name = "claim_virtual_red_packet",
        groups = emptySet(),
        action = "领取虚拟红包",
        description = "你可以领取当前真实单聊/群聊中由用户发给你的应用内虚拟红包。必须用户在本轮明确让你领取时才调用；红包编号须来自本机钱包提供的真实会话记录，不能猜测、跨会话领取或代其他 AI 领取。工具返回实际到账后才能说已领取。不能提现或兑换人民币。",
        parameters = schema(required = listOf("packet_id"),
            "packet_id" to prop("string", "本轮系统上下文中的真实红包 UUID，完整照抄")),
    )
    val walletNames = setOf(claimVirtualRedPacket.name)
    val walletTools = listOf(claimVirtualRedPacket)

    val feedNames = setOf(readFeed.name,likeFeed.name,commentFeed.name,publishFeed.name)''',
"register real wallet claim capability")

once(base/"ai/Tools.kt",
'''        readFeed, likeFeed, commentFeed, publishFeed,
        openPhoneApp,''',
'''        readFeed, likeFeed, commentFeed, publishFeed, claimVirtualRedPacket,
        openPhoneApp,''',
"claim tool registered byName")

once(base/"ai/Tools.kt",
'''fun specs(groups: Set<ToolGroup>): List<ToolSpec> = (ToolSpecs.offered(groups) + ToolSpecs.feedTools).distinctBy { it.name }''',
'''fun specs(groups: Set<ToolGroup>): List<ToolSpec> =
        (ToolSpecs.offered(groups) + ToolSpecs.feedTools + ToolSpecs.walletTools).distinctBy { it.name }''',
"offer wallet tool to private chats")

once(base/"ai/Tools.kt",
'''        if (spec.groups.none { it in settings.tools }) return failed("对方在设置里关掉了这项功能，现在用不了。", "设置里关着")''',
'''        if (call.name in ToolSpecs.walletNames) {
            val args = ToolArgs.parse(call.arguments)
                ?: return failed("红包工具参数格式错误。", "参数写错了")
            return WalletChatBridge.claim(conversationId, companionId,
                ToolArgs.text(args, "packet_id").orEmpty())
        }
        if (spec.groups.none { it in settings.tools }) return failed("对方在设置里关掉了这项功能，现在用不了。", "设置里关着")''',
"execute wallet tool with DB membership and real ledger checks")

once(base/"ai/ChatRepository.kt",
'''        val extra = listOfNotNull(shared, announcement, groupRule).joinToString("\\n\\n")''',
'''        val extra = listOfNotNull(shared, WalletChatBridge.context(conversationId, ta.id),
            announcement, groupRule).joinToString("\\n\\n")''',
"attach per-speaker wallet evidence to each group turn")

once(base/"ai/ChatRepository.kt",
'''            val shared = worldContext(conversationId, lastInput?.content.orEmpty(), s)
            fun build() = Prompt.messages(''',
'''            val shared = worldContext(conversationId, lastInput?.content.orEmpty(), s)
            val walletEvidence = WalletChatBridge.context(conversationId, ta.id)
            fun build() = Prompt.messages(''',
"read actual wallet for private companion")

once(base/"ai/ChatRepository.kt",
'''                extraContext = shared,
            )''',
'''                extraContext = listOfNotNull(shared, walletEvidence).joinToString("\\n\\n").ifBlank { null },
            )''',
"include verified wallet evidence in private model prompt")

once(base/"ai/ChatRepository.kt",
'''val groupSpecs = if (background) emptyList() else (toolPool + ToolSpecs.feedTools).distinctBy { it.name }''',
'''val groupSpecs = if (background) emptyList() else toolPool
                .distinctBy { it.name }''',
"group tools remain bounded by user request and original permission checks")

once("app/build.gradle.kts",'versionName = "0.38.4"','versionName = "0.38.5"',"version name")
once("app/build.gradle.kts",'versionCode = 62070','versionCode = 62071',"version code")
print("v0.38.5/62071: same-chat verified wallet evidence and authentic AI claim tool for direct and group chats")
