package com.cleo.cleos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleo.cleos.data.VirtualWalletStore
import com.cleo.cleos.glass.*
import com.cleo.cleos.ui.common.appContainer
import com.cleo.cleos.ui.common.GlassPage
import com.cleo.cleos.ui.common.GlassTopBar
import com.cleo.cleos.ui.common.GlassIconButton
import kotlinx.coroutines.launch
import java.util.Locale

private fun coin(cents: Long): String =
    String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100)

private fun parseCoin(text: String): Long? {
    val input = text.trim()
    if (!Regex("""(0|[1-9][0-9]{0,3})(\.[0-9]{1,2})?""").matches(input)) return null
    val units = input.substringBefore('.').toLongOrNull() ?: return null
    val decimals = input.substringAfter('.', "").padEnd(2, '0').take(2).toLongOrNull() ?: return null
    val cents = units * 100L + decimals
    return cents.takeIf { it in 1..VirtualWalletStore.MAX_TRANSACTION_CENTS }
}

/** Wallet lives in the existing app, uses real CompanionEntity ids, never real money. */
@Composable
fun VirtualWalletScreen(onBack: () -> Unit) {
    val app = appContainer()
    val book by app.wallet.state.collectAsStateWithLifecycle()
    val companions by remember { app.companions.all }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val palette = LocalGlassPalette.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var recipient by remember { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var amount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lucky by remember { mutableStateOf(false) }

    fun runWallet(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (error: Exception) {
                message = error.message ?: "钱包操作失败"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { app.wallet.settleExpired() }
            .onFailure { message = it.message ?: "无法结算过期红包" }
    }
    GlassPage(overlay = { page ->
        GlassTopBar(
            title = "钱包", subtitle = "本机虚拟币 · 不是真实支付",
            backdrop = page,
            leading = { GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack,
                "返回发现", onBack, page) }
        )
    }) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = top + TopBarHeight + 18.dp, bottom = 42.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("我的余额", fontSize = 15.sp, color = palette.contentSecondary)
            Text(coin(book.balances[0L] ?: 0L) + " 虚拟币", fontSize = 32.sp,
                color = palette.content)
            Text("虚拟币不能充值、提现、兑换人民币或购买真实商品；只在本机角色间流转。",
                fontSize = 12.sp, color = palette.contentSecondary)
            if (!book.initialized) {
                Button(enabled = !busy, onClick = {
                    runWallet {
                        app.wallet.starter()
                        message = "已领取一次性 1000.00 体验币"
                    }
                }) { Text("领取一次性体验币") }
            }

            HorizontalDivider()
            Text("角色钱包", color = palette.content, fontSize = 18.sp)
            if (companions.isEmpty()) Text("创建 AI 联系人后可互相转账",
                color = palette.contentSecondary)
            companions.forEach { ta ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ta.name.ifBlank { "AI " + ta.id }, color = palette.content)
                    Text(coin(book.balances[ta.id] ?: 0L), color = palette.contentSecondary)
                }
            }

            HorizontalDivider()
            Text("转账给 AI", fontSize = 18.sp, color = palette.content)
            Text("选择收款人", color = palette.contentSecondary, fontSize = 13.sp)
            companions.forEach { ta ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = recipient == ta.id, onClick = { recipient = ta.id })
                    Text(ta.name.ifBlank { "AI " + ta.id }, color = palette.content)
                }
            }
            OutlinedTextField(value = amount, onValueChange = { amount = it },
                label = { Text("金额（虚拟币，例如 8.88）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && recipient != null && parseCoin(amount) != null,
                onClick = {
                    val to = recipient ?: return@Button
                    val cents = parseCoin(amount) ?: return@Button
                    runWallet {
                        app.wallet.transfer(0, to, cents)
                        message = "成功向 " + (companions.firstOrNull { it.id == to }?.name ?: "AI") +
                            " 转账 " + coin(cents) + " 虚拟币"
                        amount = ""
                    }
                }) { Text("确认转账") }

            HorizontalDivider()
            Text("发送红包", fontSize = 18.sp, color = palette.content)
            Text("可选多个 AI；普通红包平均分，拼手气红包金额随机。每人仅可领一次，24 小时后未领取余额自动退回。",
                color = palette.contentSecondary, fontSize = 12.sp)
            companions.forEach { ta ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ta.id in selected,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + ta.id else selected - ta.id
                        })
                    Text(ta.name.ifBlank { "AI " + ta.id }, color = palette.content)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !lucky, onClick = { lucky = false })
                Text("普通红包", color = palette.content)
                Spacer(Modifier.width(12.dp))
                RadioButton(selected = lucky, onClick = { lucky = true })
                Text("拼手气", color = palette.content)
            }
            Button(
                enabled = !busy && selected.isNotEmpty() && parseCoin(amount) != null,
                onClick = {
                    val cents = parseCoin(amount) ?: return@Button
                    val ids = selected.toList()
                    runWallet {
                        val id = app.wallet.sendPacket(0, ids, cents, lucky)
                        message = "红包已创建，编号：" + id.take(8) +
                            "。AI 领取入口将在聊天功能中接入。"
                        selected = emptySet()
                        amount = ""
                    }
                }
            ) { Text("发红包（" + selected.size + " 人）") }

            HorizontalDivider()
            Text("红包记录", fontSize = 18.sp, color = palette.content)
            if (book.packets.isEmpty()) {
                Text("还没有红包", color = palette.contentSecondary)
            }
            book.packets.asReversed().take(30).forEach { packet ->
                val total = packet.shares.sum()
                val finished = packet.claims.size == packet.shares.size
                Text((if (packet.random) "拼手气" else "普通") + "红包 · " +
                    coin(total) + " · 已领 " + packet.claims.size + "/" + packet.recipients.size,
                    color = palette.content)
                Text("编号 " + packet.id.take(8) + " · " +
                    when { packet.returned -> "过期已退回"; finished -> "已领完"; else -> "待领取" },
                    color = palette.contentSecondary, fontSize = 12.sp)
                if (packet.sender != 0L && 0L in packet.recipients &&
                    !packet.returned && !finished &&
                    packet.claims.none { it.recipient == 0L }) {
                    TextButton(enabled = !busy, onClick = {
                        runWallet {
                            val got = app.wallet.claimPacket(packet.id, 0L)
                            message = "领取成功：" + coin(got) + " 虚拟币"
                        }
                    }) { Text("领取红包") }
                }
            }

            HorizontalDivider()
            Text("收支明细", fontSize = 18.sp, color = palette.content)
            book.movements.asReversed().take(30).forEach { entry ->
                val title = when (entry.kind) {
                    "starter" -> "一次性体验币"
                    "transfer" -> "虚拟转账"
                    "packet" -> "发送红包"
                    "claim" -> "领取红包"
                    "refund" -> "过期退款"
                    else -> "余额变化"
                }
                Text(title + " · " + coin(entry.amount), color = palette.content)
            }
            if (message.isNotBlank()) {
                Text(message, color = palette.accentContent, fontSize = 13.sp)
                TextButton(onClick = { message = "" }) { Text("关闭提示") }
            }
        }
    }
}
