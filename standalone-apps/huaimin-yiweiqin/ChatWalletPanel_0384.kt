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
    val sender: Long = 0L,
    val muted: Boolean = false,
    val packetId: String? = null,
    val transferId: String? = null,
    val canClaim: Boolean = false,
    val canReceiveTransfer: Boolean = false,
)

/** Each card reflects real wallet state, including AI outgoing and legacy transfers. */
internal fun chatWalletItems(book: WalletBook, conversationId: Long?): List<ChatWalletItem> {
    val convo = conversationId ?: return emptyList()
    val now = System.currentTimeMillis()
    val packets = book.packets.filter { it.conversationId == convo }.map { p ->
        val claimedByMe = p.claims.any { it.recipient == 0L }
        val expired = p.returned || now >= p.expiresAt
        val done = p.claims.size == p.recipients.size
        ChatWalletItem(
            id = "packet:" + p.id, at = p.createdAt, kind = ChatMoneyKind.PACKET,
            amount = p.shares.sum(),
            title = if (p.random) "拼手气红包" else "普通红包",
            status = when {
                expired -> "已过期／未领金额已退回"
                done -> "已领完 " + p.claims.size + "/" + p.recipients.size
                claimedByMe -> "已领取 · " + p.claims.size + "/" + p.recipients.size
                else -> "已领取 " + p.claims.size + "/" + p.recipients.size
            },
            sender = p.sender, muted = expired || done || claimedByMe,
            packetId = p.id,
            canClaim = !expired && !done && !claimedByMe && 0L in p.recipients &&
                (p.sender != 0L || p.allowSenderClaim)
        )
    }
    val legacy = book.movements.filter {
        it.kind == "transfer" && it.conversationId == convo
    }.map { m ->
        ChatWalletItem("transfer:" + m.id, m.at, ChatMoneyKind.TRANSFER, m.amount,
            if (m.from == 0L) "转账给 AI" else "AI 转账给我",
            "已到账 · 旧版直接转账", sender = m.from, muted = true)
    }
    val current = book.transfers.filter { it.conversationId == convo }.map { t ->
        val timedOut = t.state == "pending" && now >= t.expiresAt
        val status = when {
            timedOut || t.state == "expired" -> "已过期退还"
            t.state == "accepted" -> "已收款"
            t.state == "declined" -> "已退还"
            else -> "待收款 · 24小时内确认"
        }
        ChatWalletItem(
            id = "transfer:" + t.id, at = t.createdAt, kind = ChatMoneyKind.TRANSFER,
            amount = t.amount, title = if (t.from == 0L) "转账给 AI" else "AI 转账给我",
            status = status, sender = t.from, muted = status != "待收款 · 24小时内确认",
            transferId = t.id, canReceiveTransfer = t.to == 0L && !timedOut && t.state == "pending"
        )
    }
    return (packets + legacy + current).sortedWith(
        compareByDescending<ChatWalletItem> { it.at }.thenBy { it.id })
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
        // All items retain their original features; no repetitive payment disclaimer.
    }
}

@Composable
internal fun ChatWalletActionDialog(
    kind: ChatMoneyKind,
    members: List<GroupMemberUi>,
    isGroup: Boolean,
    balance: Long,
    initialized: Boolean,
    onStarter: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>, Long, Boolean, Boolean) -> Unit,
) {
    val valid = members.filter { it.id > 0L }.distinctBy { it.id }
    var chosen by remember(kind, valid.map { it.id }) {
        mutableStateOf(if (isGroup) emptySet<Long>() else valid.map { it.id }.toSet())
    }
    var amount by remember(kind) { mutableStateOf("") }
    var lucky by remember { mutableStateOf(false) }
    var selfJoin by remember { mutableStateOf(false) }
    val parsed = parseMoneyCoins(amount)
    val selected = valid.filter { it.id in chosen }.map { it.id }
    val participants = selected + if (kind == ChatMoneyKind.PACKET && isGroup && lucky && selfJoin) listOf(0L) else emptyList()
    val allowed = parsed != null && parsed <= balance && participants.isNotEmpty() &&
        (kind == ChatMoneyKind.PACKET || selected.size == 1) &&
        (kind != ChatMoneyKind.PACKET || parsed >= participants.size)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == ChatMoneyKind.PACKET) "发送虚拟红包" else "向 AI 转账") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("我的余额：" + coins(balance) + " 星币", fontSize = 13.sp)
                if (!initialized) Text("余额可以在发现页的钱包中自行设置。", fontSize = 12.sp)
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
                    if (lucky) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selfJoin, onCheckedChange = { selfJoin = it })
                        Text("我也参与抢红包（仅拼手气）", fontSize = 12.sp)
                    }
                }
                Text(
                    if (kind == ChatMoneyKind.PACKET)
                        "发出后等待真实领取，24小时未领取金额退回。拼手气群红包可勾选自己参与。"
                    else "发送后等待对方确认收款；24小时未收自动退回。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (parsed != null && parsed > balance) {
                    Text("虚拟余额不足", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = allowed, onClick = {
                onConfirm(participants, parsed ?: return@TextButton, lucky, selfJoin && isGroup && lucky)
            }) { Text(if (kind == ChatMoneyKind.PACKET) "发红包" else "确认转账") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
internal fun ChatWalletCard(
    event: ChatWalletItem,
    onClaim: (String) -> Unit = {},
    onReceiveTransfer: (String, Boolean) -> Unit = { _, _ -> },
) {
    var details by remember(event.id) { mutableStateOf(false) }
    val packet = event.kind == ChatMoneyKind.PACKET
    val color = when {
        event.muted -> Color(0xFF777F87)
        packet -> Color(0xFFCF753A)
        else -> Color(0xFFDB9843)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement =
        if (event.sender == 0L) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.77f).clickable { details = true },
            color = color, shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (packet) Icons.Rounded.CardGiftcard else Icons.Rounded.CurrencyExchange,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(event.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(coins(event.amount) + " 星币", color = Color.White, fontSize = 18.sp)
                    }
                }
                Text(event.status, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Text("怀民亦未寝 · 虚拟钱包", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
            }
        }
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text(event.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(coins(event.amount) + " 星币\n" + event.status)
                if (packet && event.canClaim) {
                    Button(onClick = {
                        details = false
                        event.packetId?.let(onClaim)
                    }) { Text("领取红包") }
                }
                if (!packet && event.canReceiveTransfer) {
                    Button(onClick = {
                        details = false
                        event.transferId?.let { onReceiveTransfer(it, true) }
                    }) { Text("确认收款") }
                    TextButton(onClick = {
                        details = false
                        event.transferId?.let { onReceiveTransfer(it, false) }
                    }) { Text("退还转账") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { details = false }) { Text("关闭") } },
    )
}
