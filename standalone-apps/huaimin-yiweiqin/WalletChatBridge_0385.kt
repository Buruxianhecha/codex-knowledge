package com.cleo.cleos.ai

import com.cleo.cleos.data.VirtualWalletStore
import com.cleo.cleos.data.WalletBook
import com.cleo.cleos.data.db.AppDatabase
import java.util.Locale

/** Real local ledger evidence, scoped to the active chat and the actual model speaker. */
object WalletChatBridge {
    @Volatile var store: VirtualWalletStore? = null
    @Volatile var database: AppDatabase? = null

    private fun coins(value: Long): String =
        String.format(Locale.ROOT, "%d.%02d", value / 100L, value % 100L)

    internal fun contextFor(book: WalletBook, chatId: Long, companionId: Long): String {
        val packets = book.packets.asReversed().filter {
            it.conversationId == chatId && it.sender == 0L
        }.take(8)
        val transfers = book.movements.asReversed().filter {
            it.conversationId == chatId && it.kind == "transfer" &&
                it.from == 0L && it.to == companionId
        }.take(8)
        if (packets.isEmpty() && transfers.isEmpty()) return ""
        return buildString {
            append("【本机钱包已核实的会话交易记录】\n")
            append("这些是应用本地账本中的真实虚拟币交易，不是普通文字消息，也不是人民币。")
            append("你已读到当前会话与自己有关的交易。不要说没看到红包或转账；")
            append("不能根据用户口头描述更改余额、假称领取成功，只有领取工具成功才能说已领取。\n")
            for (packet in packets) {
                val count = packet.claims.size
                val claimed = packet.claims.any { it.recipient == companionId }
                val available = companionId in packet.recipients &&
                    !claimed && !packet.returned && count < packet.shares.size &&
                    System.currentTimeMillis() < packet.expiresAt
                val status = when {
                    packet.returned || System.currentTimeMillis() >= packet.expiresAt -> "已到期/已退回"
                    count == packet.shares.size -> "已领完"
                    claimed -> "你已领取"
                    available -> "你有资格领取，尚未领取"
                    else -> "你无资格领取"
                }
                append("红包：id=").append(packet.id)
                    .append("；用户发送；类型=").append(if (packet.random) "拼手气" else "普通")
                    .append("；总额=").append(coins(packet.shares.sum()))
                    .append(" 虚拟币；领取人数=").append(count).append("/").append(packet.recipients.size)
                    .append("；对你状态=").append(status).append("\n")
            }
            for (transfer in transfers) {
                append("转账：id=").append(transfer.id)
                    .append("；用户转给你；金额=").append(coins(transfer.amount))
                    .append(" 虚拟币；已经真实到账\n")
            }
            append("你当前在本机的钱包余额=").append(coins(book.balances[companionId] ?: 0L))
                .append(" 虚拟币。红包领取必须使用 claim_virtual_red_packet 工具，")
            append("工具不可用时只能说明无法操作，不得模拟领取。")
        }
    }

    private suspend fun authorized(chatId: Long, companionId: Long): Boolean {
        val db = database ?: return false
        val chat = db.conversations().get(chatId) ?: return false
        if (companionId <= 0L) return false
        return if (chat.isGroup) companionId in db.groupMembers().idsFor(chatId)
        else chat.companionId == companionId
    }

    suspend fun context(chatId: Long, companionId: Long): String? {
        if (!authorized(chatId, companionId)) return null
        return store?.state?.value?.let { contextFor(it, chatId, companionId).ifBlank { null } }
    }

    suspend fun claim(chatId: Long, companionId: Long, id: String): ToolOutcome {
        if (!authorized(chatId, companionId)) return ToolOutcome(
            "你不是当前会话中的领取角色，不能领取。", "无权领取红包")
        val wallet = store ?: return ToolOutcome("钱包尚未准备好。", "钱包不可用")
        if (id.isBlank() || id.length > 100) return ToolOutcome("红包 ID 不合法。", "红包编号无效")
        val packet = wallet.state.value.packets.firstOrNull { it.id == id && it.conversationId == chatId }
            ?: return ToolOutcome("本会话没有这个真实红包。", "找不到红包")
        if (packet.sender != 0L || companionId !in packet.recipients) return ToolOutcome(
            "这个红包不是发给你的。", "不在领取名单中")
        return try {
            val got = wallet.claimPacket(id, companionId)
            ToolOutcome("实际领取成功：" + coins(got) + " 虚拟币，已经进入你的本机钱包。", "已领取红包")
        } catch (e: Exception) {
            ToolOutcome("红包尚未领取：" + (e.message ?: "领取失败"), "红包领取失败")
        }
    }
}
