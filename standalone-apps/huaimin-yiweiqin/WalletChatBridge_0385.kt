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
        val now = System.currentTimeMillis()
        val packets = book.packets.asReversed().filter {
            it.conversationId == chatId && (it.sender == companionId || companionId in it.recipients)
        }.take(8)
        val newTransfers = book.transfers.asReversed().filter {
            it.conversationId == chatId && (it.from == companionId || it.to == companionId)
        }.take(8)
        val legacy = book.movements.asReversed().filter {
            it.kind == "transfer" && it.conversationId == chatId &&
                (it.from == companionId || it.to == companionId)
        }.take(4)
        if (packets.isEmpty() && newTransfers.isEmpty() && legacy.isEmpty() &&
            (book.balances[companionId] ?: 0L) == 0L) return ""
        return buildString {
            append("【仅当前会话、当前 AI 身份可见的真实虚拟钱包账本】\\n")
            append("金额均为应用内虚拟币，不是人民币。不能靠聊天文字改变余额，")
            append("不得假称自己已经领红包或已发送转账，必须有相应工具成功回执。\\n")
            packets.forEach { p ->
                val claimed = p.claims.firstOrNull { it.recipient == companionId }
                val expired = p.returned || now >= p.expiresAt
                val completed = p.claims.size == p.recipients.size
                val status = when {
                    claimed != null -> "你已领到 " + coins(claimed.amount) + " 虚拟币"
                    expired -> "过期已退回"
                    completed -> "已领完"
                    companionId in p.recipients -> "你有资格领取，尚未领取"
                    else -> "你是发出方，等待领取"
                }
                append("红包 id=").append(p.id)
                    .append("；发送人=").append(if (p.sender == 0L) "用户" else if (p.sender == companionId) "你" else "群友AI")
                    .append("；").append(if (p.random) "拼手气" else "普通")
                    .append("；总额=").append(coins(p.shares.sum()))
                    .append("；领取 ").append(p.claims.size).append("/").append(p.recipients.size)
                    .append("；").append(status).append("。\\n")
                if (claimed != null && p.random && claimed.amount >= 200L &&
                    claimed.amount == p.claims.maxOfOrNull { it.amount }) {
                    append("你抢到这个红包目前最大的一份，可以自然地表示感谢，")
                    append("如果符合你的人格和余额，也可自愿回一个小红包。")
                    append("回礼不是义务，不能无限循环或超过每日限额。\\n")
                }
            }
            newTransfers.forEach { t ->
                append("转账 id=").append(t.id)
                    .append("；").append(if (t.from == companionId) "你发送" else "你收到待确认的转账")
                    .append("；金额=").append(coins(t.amount))
                    .append("；状态=").append(t.state)
                    .append("。pending 表示尚未到账，必须用 accept_virtual_transfer 实际确认。\\n")
            }
            legacy.forEach { t ->
                append("旧版即时转账 ").append(coins(t.amount))
                    .append(if (t.to == companionId) " 已到账" else " 已发出").append("。\\n")
            }
            append("你当前可支配余额=").append(coins(book.balances[companionId] ?: 0L))
                .append("。允许自愿使用 send_virtual_red_packet / send_virtual_transfer 向用户或本群其他角色实际赠送，")
            append("每次最多10.00、每日最多3次，不准超支。用户给你的红包如果本轮刚发送或明确让你领取，")
            append("可以使用 claim_virtual_red_packet，收到转账可使用 accept_virtual_transfer。")
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
        if (companionId !in packet.recipients || packet.sender == companionId) return ToolOutcome(
            "这个红包不是发给你的。", "不在领取名单中")
        return try {
            val got = wallet.claimPacket(id, companionId)
            ToolOutcome("实际领取成功：" + coins(got) + " 虚拟币，已经进入你的本机钱包。", "已领取红包")
        } catch (e: Exception) {
            ToolOutcome("红包尚未领取：" + (e.message ?: "领取失败"), "红包领取失败")
        }
    }
    private suspend fun allowedRecipients(chatId: Long, companionId: Long): Set<Long>? {
        if (!authorized(chatId, companionId)) return null
        val db = database ?: return null
        val chat = db.conversations().get(chatId) ?: return null
        val group = if (chat.isGroup) db.groupMembers().idsFor(chatId).toSet() else emptySet()
        return (group + 0L).filter { it != companionId }.toSet()
    }

    /** Incoming AI transfers and packets require the model's explicit real tool call. */
    suspend fun sendPacket(chatId: Long, companionId: Long, recipients: String,
                           cents: Long, lucky: Boolean): ToolOutcome {
        val allowed = allowedRecipients(chatId, companionId)
            ?: return ToolOutcome("你不是本会话的角色。", "红包发送失败")
        val ids = recipients.split(',').mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty() || ids.size != ids.distinct().size || ids.any { it !in allowed })
            return ToolOutcome("红包领取人只可选择本会话用户(0)或有效群成员。", "收件人无效")
        val db = database ?: return ToolOutcome("会话数据不可用。", "发送失败")
        val chat = db.conversations().get(chatId)
            ?: return ToolOutcome("会话不存在。", "发送失败")
        if (!chat.isGroup && ids != listOf(0L))
            return ToolOutcome("单聊红包只能发送给用户。", "收件人无效")
        return try {
            val id = store?.sendPacket(companionId, ids, cents, lucky && chat.isGroup, chatId)
                ?: error("钱包未准备好")
            ToolOutcome("真实红包发送成功，编号=" + id + "，金额=" + coins(cents) +
                " 虚拟币，已从你的账户扣款，等待群友或用户领取。", "已发虚拟红包")
        } catch (e: Exception) {
            ToolOutcome("未发出红包：" + (e.message ?: "失败"), "红包发送失败")
        }
    }

    suspend fun sendTransfer(chatId: Long, companionId: Long, recipient: Long, cents: Long): ToolOutcome {
        val allowed = allowedRecipients(chatId, companionId)
            ?: return ToolOutcome("你不属于本会话。", "转账失败")
        if (recipient !in allowed) return ToolOutcome("收款人不在本会话。", "转账失败")
        return try {
            val id = store?.sendTransfer(companionId, recipient, cents, chatId)
                ?: error("钱包不可用")
            ToolOutcome("真实虚拟转账已发送，编号=" + id + "，金额=" + coins(cents) +
                " 虚拟币；等待对方确认收款，24小时未收自动退回。", "已发送待收款转账")
        } catch (e: Exception) {
            ToolOutcome("转账未成功：" + (e.message ?: "失败"), "转账失败")
        }
    }

    suspend fun acceptTransfer(chatId: Long, companionId: Long, id: String): ToolOutcome {
        if (!authorized(chatId, companionId)) return ToolOutcome("你不属于本会话。", "收款失败")
        val wallet = store ?: return ToolOutcome("钱包不可用。", "收款失败")
        val t = wallet.state.value.transfers.firstOrNull { it.id == id && it.conversationId == chatId &&
            it.to == companionId }
            ?: return ToolOutcome("不存在发给你的待收款转账。", "收款失败")
        return try {
            wallet.decideTransfer(id, companionId, true)
            ToolOutcome("真实收款成功：" + coins(t.amount) + " 虚拟币已进入你的钱包。", "已收款")
        } catch (e: Exception) {
            ToolOutcome("未收款：" + (e.message ?: "失败"), "收款失败")
        }
    }

}
