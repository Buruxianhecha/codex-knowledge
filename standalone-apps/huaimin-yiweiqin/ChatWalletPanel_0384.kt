package com.cleo.cleos.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleo.cleos.data.WalletBook
import java.util.Locale

internal enum class ChatMoneyKind { PACKET, TRANSFER }

internal data class ChatWalletItem(
    val id: String,
    val at: Long,
    val kind: ChatMoneyKind,
    val amount: Long,
    val title: String,
    val status: String,
)

/** Render only actual ledger transactions for this conversation, never invented messages. */
internal fun chatWalletItems(book: WalletBook, conversationId: Long?): List<ChatWalletItem> {
    val convo = conversationId ?: return emptyList()
    val packets = book.packets.filter { it.conversationId == convo && it.sender == 0L }.map { p ->
        ChatWalletItem(
            id = "packet:" + p.id, at = p.createdAt, kind = ChatMoneyKind.PACKET,
            amount = p.shares.sum(),
            title = if (p.random) "拼手气红包" else "普通红包",
            status = when {
                p.returned -> "已过期，剩余金额已退回"
                p.claims.size == p.recipients.size -> "已领完"
                else -> "已领取 " + p.claims.size + "/" + p.recipients.size
            },
        )
    }
    val transfers = book.movements.filter {
        it.kind == "transfer" && it.from == 0L && it.conversationId == convo
    }.map { m ->
        ChatWalletItem("transfer:" + m.id, m.at, ChatMoneyKind.TRANSFER,
            m.amount, "转账给 AI", "已到账 · 本机虚拟币")
    }
    return (packets + transfers).sortedWith(compareByDescending<ChatWalletItem> { it.at }.thenBy { it.id })
}

private fun coins(value: Long): String =
    String.format(Locale.ROOT, "%d.%02d", value / 100L, value % 100L)

internal fun parseMoneyCoins(input: String): Long? {
    val raw = input.trim()
    if (!Regex("""(0|[1-9][0-9]{0,3})(\.[0-9]{1,2})?""").matches(raw)) return null
    val parts = raw.split('.')
    val whole = parts[0].toLongOrNull() ?: return null
    val cents = parts.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0L
    return (whole * 100L + cents).takeIf { it in 1L..100_000L }
}

/** Existing controls stay intact. Unsupported options are visibly disabled. */
@Composable
internal fun ChatPlusPanel(
    onAlbum: () -> Unit,
    onCall: () -> Unit,
    onRedPacket: () -> Unit,
    onTransfer: () -> Unit,
) {
    data class Action(val label: String, val icon: ImageVector, val enabled: Boolean, val action: () -> Unit)
    val actions = listOf(
        Action("相册", Icons.Rounded.PhotoLibrary, true, onAlbum),
        Action("拍摄", Icons.Rounded.CameraAlt, false, {}),
        Action("语音通话", Icons.Rounded.Call, true, onCall),
        Action("位置", Icons.Rounded.Place, false, {}),
        Action("红包", Icons.Rounded.CardGiftcard, true, onRedPacket),
        Action("礼物", Icons.Rounded.Redeem, false, {}),
        Action("转账", Icons.Rounded.CurrencyExchange, true, onTransfer),
        Action("语音输入", Icons.Rounded.Mic, false, {}),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
        Text("聊天工具", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().height(214.dp),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(actions) { action ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(57.dp)
                            .clickable(enabled = action.enabled, onClick = action.action),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (action.enabled) 0.9f else 0.4f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(action.icon, contentDescription = action.label,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (action.enabled) 1f else 0.35f),
                                modifier = Modifier.size(27.dp))
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(action.label, fontSize = 11.sp, textAlign = TextAlign.Center)
                    if (!action.enabled) Text("待接入", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("红包与转账仅使用应用内虚拟币，不能提现或购买真实商品。",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
internal fun ChatWalletActionDialog(
    kind: ChatMoneyKind,
    members: List<GroupMemberUi>,
    isGroup: Boolean,
    balance: Long,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>, Long, Boolean) -> Unit,
) {
    val valid = members.filter { it.id > 0L }.distinctBy { it.id }
    var chosen by remember(kind, valid.map { it.id }) {
        mutableStateOf(if (isGroup) emptySet<Long>() else valid.map { it.id }.toSet())
    }
    var amount by remember(kind) { mutableStateOf("") }
    var lucky by remember { mutableStateOf(false) }
    val parsed = parseMoneyCoins(amount)
    val selected = valid.filter { it.id in chosen }.map { it.id }
    val allowed = parsed != null && parsed <= balance && selected.isNotEmpty() &&
        (kind == ChatMoneyKind.PACKET || selected.size == 1) &&
        (kind != ChatMoneyKind.PACKET || parsed >= selected.size)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == ChatMoneyKind.PACKET) "发送虚拟红包" else "向 AI 转账") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("我的余额：" + coins(balance) + " 虚拟币", fontSize = 13.sp)
                if (isGroup) {
                    Text(if (kind == ChatMoneyKind.PACKET) "选择群内领取人（可多选）"
                        else "选择收款 AI（单选）", fontSize = 13.sp)
                    valid.forEach { member ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (kind == ChatMoneyKind.PACKET) {
                                Checkbox(checked = member.id in chosen,
                                    onCheckedChange = { checked ->
                                        chosen = if (checked) chosen + member.id else chosen - member.id
                                    })
                            } else {
                                RadioButton(selected = member.id in chosen,
                                    onClick = { chosen = setOf(member.id) })
                            }
                            Text(member.name.ifBlank { "AI " + member.id })
                        }
                    }
                } else {
                    Text("收款人：" + (valid.firstOrNull()?.name ?: "暂无角色"), fontSize = 13.sp)
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("金额（0.01–1000.00）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (kind == ChatMoneyKind.PACKET && isGroup) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !lucky, onClick = { lucky = false })
                        Text("普通")
                        RadioButton(selected = lucky, onClick = { lucky = true })
                        Text("拼手气")
                    }
                }
                Text(
                    if (kind == ChatMoneyKind.PACKET)
                        "发送即扣款；24小时未领取金额退回。AI 自动抢红包待后续接入。"
                    else "确认后立即转入 AI 本机虚拟账户；不可撤销。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (parsed != null && parsed > balance) {
                    Text("虚拟余额不足", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = allowed, onClick = {
                onConfirm(selected, parsed ?: return@TextButton, lucky)
            }) { Text(if (kind == ChatMoneyKind.PACKET) "发红包" else "确认转账") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
internal fun ChatWalletCard(event: ChatWalletItem) {
    var details by remember(event.id) { mutableStateOf(false) }
    val packet = event.kind == ChatMoneyKind.PACKET
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.77f).clickable { details = true },
            color = if (packet) Color(0xFFCF753A) else Color(0xFFDB9843),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (packet) Icons.Rounded.CardGiftcard else Icons.Rounded.CurrencyExchange,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(event.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(coins(event.amount) + " 虚拟币", color = Color.White, fontSize = 18.sp)
                    }
                }
                Text(event.status, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Text("怀民亦未寝 · 虚拟钱包", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text(event.title) },
        text = { Text(coins(event.amount) + " 虚拟币\n" + event.status +
            "\n本地虚拟交易，不是人民币支付。") },
        confirmButton = { TextButton(onClick = { details = false }) { Text("我知道了") } }
    )
}
