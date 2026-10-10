package com.cleo.cleos.ai

import com.cleo.cleos.data.db.MessageEntity

/**
 * Durable, model-visible event written only after the wallet transaction commits.
 * The visible chat continues to show one genuine ledger-backed orange transaction card,
 * not a duplicate synthetic user bubble. Event text never publishes a private
 * transfer's payee, amount, or wallet balance to other group members.
 */
object WalletReactiveEvents {
    private const val PREFIX = "[怀民钱包已验证事件:v1]"

    fun isEvent(message: MessageEntity): Boolean =
        message.role == "user" && message.note == null && message.content.startsWith(PREFIX)

    fun messageText(packet: Boolean, transactionId: String): String {
        require(transactionId.isNotBlank() && transactionId.length <= 100)
        return PREFIX + " 编号=" + transactionId + "。" +
            (if (packet) "刚刚发送虚拟红包。" else "刚刚完成虚拟转账。") +
            "这是应用在真实扣账成功后生成的系统交易通知，并非用户手动输入。" +
            "请立即自然、简短地回应；只根据你在本机钱包上下文实际有权限看到的金额、领取资格和余额回答。" +
            "不可假装领红包，也不可声称未收到已经到账的转账。" +
            "如果红包尚未领取，你可以表示愿意领取，但只有真实调用工具成功才能说已领取。"
    }

    fun targetIds(raw: String?, members: Set<Long>): Set<Long> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0 && it in members }.toSet()
}
