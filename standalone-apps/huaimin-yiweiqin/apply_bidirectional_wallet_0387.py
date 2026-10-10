#!/usr/bin/env python3
"""v0.38.7: bidirectional coins, recipient actions, self-claim and wallet AI tools."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base=Path("app/src/main/java/com/cleo/cleos")
def once(path,old,new,label):
    file=root/path
    text=file.read_text(encoding="utf-8")
    n=text.count(old)
    if n!=1: raise RuntimeError(f"{label}: {n} anchors in {path}")
    file.write_text(text.replace(old,new,1),encoding="utf-8")

tools=base/"ai/Tools.kt"
chat=base/"ai/ChatRepository.kt"
ui=base/"ui/chat/ChatScreen.kt"

once(tools,
'''    val walletNames = setOf(claimVirtualRedPacket.name)
    val walletTools = listOf(claimVirtualRedPacket)''',
'''    val sendVirtualRedPacket = ToolSpec(
        name = "send_virtual_red_packet", groups = emptySet(), action = "发送虚拟红包",
        description = "以你自己的本地虚拟币余额真实发红包，可主动感谢用户、回礼或向本群成员分享。仅当前聊天使用，不能跨会话；每次最多10.00虚拟币、每天最多3次，不许强制赠送或无止境循环。如果你领到手气最佳的大红包，可根据人格自愿感谢或适度回礼。",
        parameters = schema(required = listOf("recipients","amount_cents"),
            "recipients" to prop("string", "单聊只能为0（用户）；群聊可用0或当前真实角色编号，多个收件人用逗号分开"),
            "amount_cents" to prop("integer", "总金额，单位为分的整数，例如120代表1.20虚拟币，最大1000"),
            "lucky" to prop("boolean", "群聊拼手气时为true；普通红包false")),
    )
    val sendVirtualTransfer = ToolSpec(
        name = "send_virtual_transfer", groups = emptySet(), action = "发送虚拟转账",
        description = "以你自己的本机虚拟币余额向当前会话用户(0)或真实群成员转账。自愿回礼，不可用人民币；最大10.00虚拟币，每天最多3次。收款人需要确认后才到账，未收款24小时自动退还。",
        parameters = schema(required = listOf("recipient_id","amount_cents"),
            "recipient_id" to prop("integer", "用户的ID为0；群聊其他AI填已知真实角色编号"),
            "amount_cents" to prop("integer", "整数分，例如250代表2.50虚拟币，最多1000")),
    )
    val acceptVirtualTransfer = ToolSpec(
        name = "accept_virtual_transfer", groups = emptySet(), action = "确认虚拟转账",
        description = "仅真实收到待收款转账且你是收款角色时，调用此工具确认入账。不能替别人收款，不能重复领取，24小时超时退回。",
        parameters = schema(required = listOf("transfer_id"),
            "transfer_id" to prop("string", "本机会话钱包记录中的转账UUID")),
    )
    val walletNames = setOf(claimVirtualRedPacket.name, sendVirtualRedPacket.name,
        sendVirtualTransfer.name, acceptVirtualTransfer.name)
    val walletTools = listOf(claimVirtualRedPacket, sendVirtualRedPacket,
        sendVirtualTransfer, acceptVirtualTransfer)''',
"declare real bidirectional tools")
once(tools,
'''        description = "你可以领取当前真实单聊/群聊中由用户发给你的应用内虚拟红包。必须用户在本轮明确让你领取时才调用；红包编号须来自本机钱包提供的真实会话记录，不能猜测、跨会话领取或代其他 AI 领取。工具返回实际到账后才能说已领取。不能提现或兑换人民币。",''',
'''        description = "你可以领取当前真实单聊/群聊中确实发给你的虚拟红包。如果本轮用户刚发了群红包，你可以自然地决定领取；否则只在用户要求你领取时调用。红包编号必须来自本机当前会话的真实记录，不能猜测或替其他角色领取。实际工具成功才能说领到。领取金额大的拼手气红包可自然感谢，并自愿适度回礼，不得无限回礼。",''',
"allow AI to voluntarily claim just-sent group packets")
once(tools,
'''        readFeed, likeFeed, commentFeed, publishFeed, claimVirtualRedPacket,
        openPhoneApp,''',
'''        readFeed, likeFeed, commentFeed, publishFeed, claimVirtualRedPacket,
        sendVirtualRedPacket, sendVirtualTransfer, acceptVirtualTransfer,
        openPhoneApp,''',
"register AI wallet APIs by name")
once(tools,
'''            return WalletChatBridge.claim(conversationId, companionId,
                ToolArgs.text(args, "packet_id").orEmpty())''',
'''            return when (call.name) {
                ToolSpecs.claimVirtualRedPacket.name -> WalletChatBridge.claim(
                    conversationId, companionId, ToolArgs.text(args, "packet_id").orEmpty())
                ToolSpecs.sendVirtualRedPacket.name -> WalletChatBridge.sendPacket(
                    conversationId, companionId, ToolArgs.text(args, "recipients").orEmpty(),
                    (ToolArgs.int(args["amount_cents"]) ?: 0).toLong(),
                    ToolArgs.bool(args, "lucky") == true)
                ToolSpecs.sendVirtualTransfer.name -> WalletChatBridge.sendTransfer(
                    conversationId, companionId, ToolArgs.id(args["recipient_id"]) ?: -1L,
                    (ToolArgs.int(args["amount_cents"]) ?: 0).toLong())
                ToolSpecs.acceptVirtualTransfer.name -> WalletChatBridge.acceptTransfer(
                    conversationId, companionId, ToolArgs.text(args, "transfer_id").orEmpty())
                else -> failed("未知钱包动作。", "不支持")
            }''',
"route AI wallet actions through real ledger")

once(chat,
'''        val target = recipients.toSet()
        require(target.isNotEmpty() && target.size == recipients.size && target.all { it > 0L && it in authorized }) {
            "当前会话收款人不匹配"
        }''',
'''        val target = recipients.filter { it > 0L }.toSet()
        require(target.isNotEmpty() && recipients.distinct().size == recipients.size &&
            target.all { it in authorized }) {
            "当前会话收款人不匹配"
        }''',
"do not summon user AI on self-claim")
once(chat,
'''            require(saved.recipients.toSet() == target) { "红包目标已变化" }''',
'''            require(saved.recipients.toSet() == (target +
                if (saved.allowSenderClaim && room.isGroup && saved.random) setOf(0L) else emptySet())) {
                "红包目标已变化"
            }''',
"validate optional user as lucky group participant")
once(chat,
'''            val saved = wallet.movements.singleOrNull { it.id == receiptId &&
                it.conversationId == conversationId && it.kind == "transfer" && it.from == 0L }
                ?: error("没有找到真实转账流水")''',
'''            val saved = wallet.transfers.singleOrNull { it.id == receiptId &&
                it.conversationId == conversationId && it.from == 0L &&
                it.state == "pending" }
                ?: error("没有找到真实待收款转账")''',
"verified pending transfer wakes recipient")

once(ui,
'''                        is ChatRow.Wallet -> ChatWalletCard(row.event)''',
'''                        is ChatRow.Wallet -> ChatWalletCard(
                            row.event,
                            onClaim = { packetId ->
                                if (!walletBusy) {
                                    walletBusy = true
                                    scope.launch {
                                        try {
                                            val conversation = state.conversationId ?: error("当前会话不存在")
                                            val p = c.wallet.state.value.packets.singleOrNull {
                                                it.id == packetId && it.conversationId == conversation
                                            } ?: error("当前会话没有该红包")
                                            if (p.sender == 0L) {
                                                val live = c.db.conversations().get(conversation)
                                                    ?: error("会话已删除")
                                                require(live.isGroup && p.random && p.allowSenderClaim) {
                                                    "只允许领取自己发的拼手气群红包"
                                                }
                                            }
                                            c.wallet.claimPacket(packetId, 0L)
                                        } catch (error: Exception) {
                                            voiceHint = error.message ?: "红包领取失败"
                                        } finally { walletBusy = false }
                                    }
                                }
                            },
                            onReceiveTransfer = { id, accept ->
                                if (!walletBusy) {
                                    walletBusy = true
                                    scope.launch {
                                        try {
                                            val conversation = state.conversationId ?: error("会话不存在")
                                            require(c.wallet.state.value.transfers.any {
                                                it.id == id && it.conversationId == conversation && it.to == 0L
                                            }) { "该转账不属于当前会话" }
                                            c.wallet.decideTransfer(id, 0L, accept)
                                        } catch (error: Exception) {
                                            voiceHint = error.message ?: "操作失败"
                                        } finally { walletBusy = false }
                                    }
                                }
                            }
                        )''',
"wire user packet claim and incoming AI transfer buttons")
once(ui,
'''            onConfirm = { ids, cents, random ->''',
'''            onConfirm = { ids, cents, random, selfJoin ->''',
"carry user self-claim group flag")
once(ui,
'''                            require(ids.all { it in currentMembers }) { "群成员已变化，请重新选择" }''',
'''                            require(ids.all { it in currentMembers ||
                                (it == 0L && live.isGroup && selfJoin && random) }) {
                                "红包领取对象已变化，请重新选择"
                            }''',
"member and user eligibility recheck")
once(ui,
'''                                c.wallet.sendPacket(0L, ids, cents, random, conversation)''',
'''                                c.wallet.sendPacket(0L, ids, cents, random, conversation,
                                    allowSenderClaim = selfJoin)''',
"self-claim sender flag committed with packet")
once(ui,
'''                                c.wallet.transfer(0L, ids.single(), cents, conversation)''',
'''                                c.wallet.sendTransfer(0L, ids.single(), cents, conversation)''',
"new transfer pending escrow")

once("app/build.gradle.kts", 'versionName = "0.38.6"','versionName = "0.38.7"',"version")
once("app/build.gradle.kts", 'versionCode = 62072','versionCode = 62073',"build")
print("v0.38.7/62073: AI outgoing gifts and transfers, own lucky group claims, dim completed cards")
