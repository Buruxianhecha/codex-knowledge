package com.cleo.cleos.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.security.SecureRandom

/** This is play money local to this installation, not RMB or a payment account. */
@Serializable
data class WalletMovement(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val from: Long,
    val to: Long,
    val amount: Long,
    val packetId: String? = null,
    val conversationId: Long = 0L,
    val at: Long = System.currentTimeMillis()
)
@Serializable
data class WalletClaim(val recipient: Long, val amount: Long, val at: Long = System.currentTimeMillis())
@Serializable
data class WalletPacket(
    val id: String = UUID.randomUUID().toString(),
    val sender: Long,
    val recipients: List<Long>,
    val shares: List<Long>,
    val random: Boolean,
    val conversationId: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + 86_400_000L,
    val claims: List<WalletClaim> = emptyList(),
    val returned: Boolean = false,
    val allowSenderClaim: Boolean = false
) {
    val remaining: Long get() = if (returned) 0L else shares.drop(claims.size).sum()
}
/** Transfer funds are escrowed until the receiving account confirms; legacy transfers stay intact. */
@Serializable
data class WalletPendingTransfer(
    val id: String = UUID.randomUUID().toString(),
    val from: Long,
    val to: Long,
    val amount: Long,
    val conversationId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + 86_400_000L,
    val state: String = "pending",
    val completedAt: Long? = null
)

@Serializable
data class WalletBook(
    val version: Int = 1,
    val initialized: Boolean = false,
    val balances: Map<Long, Long> = emptyMap(),
    val movements: List<WalletMovement> = emptyList(),
    val packets: List<WalletPacket> = emptyList(),
    val transfers: List<WalletPendingTransfer> = emptyList()
)

/**
 * Single-process atomic, serialized balance and escrow ledger.
 * All mutation, including claim/expiry, goes through [Mutex] and [AtomicFile].
 * A damaged book is read-only; it must never be silently reset to a new grant.
 * Account 0 is the user; AI accounts are their stable CompanionEntity IDs.
 */
class VirtualWalletStore(context: Context) {
    private val file = AtomicFile(java.io.File(context.filesDir, "huaimin-wallet-v1.json"))
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    private var unreadable: Throwable? = null
    private val current = MutableStateFlow(load())
    val state: StateFlow<WalletBook> = current

    private fun load(): WalletBook {
        if (!file.baseFile.exists() &&
            !java.io.File(file.baseFile.path + ".bak").exists() &&
            !java.io.File(file.baseFile.path + ".new").exists()) return WalletBook()
        return try {
            json.decodeFromString<WalletBook>(file.openRead().bufferedReader().use { it.readText() })
                .also(::audit)
        } catch (e: Exception) {
            unreadable = e
            WalletBook()
        }
    }

    private fun audit(book: WalletBook) {
        require(book.version == 1 && book.movements.size <= 200000 && book.packets.size <= 20000 && book.transfers.size <= 20000)
        require(book.balances.keys.all { it >= 0L } && book.balances.values.all { it >= 0L })
        require(book.packets.map { it.id }.distinct().size == book.packets.size)
        book.packets.forEach { p ->
            require(p.sender >= 0L && p.recipients.isNotEmpty() && p.recipients.size <= 50)
            require(p.recipients.distinct().size == p.recipients.size)
            require(p.recipients.all { it >= 0L })
            if (p.sender in p.recipients) {
                require(p.allowSenderClaim && p.random && p.conversationId > 0L) { "仅拼手气群红包允许发送人参与" }
            }
            if (p.allowSenderClaim) require(p.random && p.conversationId > 0L && p.sender in p.recipients)
            require(p.shares.size == p.recipients.size && p.shares.all { it > 0L } && p.shares.sum() <= STARTER_CENTS)
            require(p.claims.size <= p.shares.size)
            require(p.claims.map { it.recipient }.distinct().size == p.claims.size)
            require(p.claims.all { it.recipient in p.recipients })
            p.claims.forEachIndexed { index, claim -> require(claim.amount == p.shares[index]) }
        }
        // No generated coins except one deliberately claimed starter allowance.
        require(book.transfers.map { it.id }.distinct().size == book.transfers.size)
        book.transfers.forEach { t ->
            require(t.from >= 0 && t.to >= 0 && t.from != t.to &&
                t.amount in 1..MAX_TRANSACTION_CENTS && t.conversationId > 0 &&
                t.state in setOf("pending","accepted","declined","expired") &&
                t.expiresAt > t.createdAt)
        }
        val liquid = book.balances.values.sum()
        val escrow = book.packets.sumOf { it.remaining } +
            book.transfers.filter { it.state == "pending" }.sumOf { it.amount }
        require(liquid + escrow == if (book.initialized) STARTER_CENTS else 0L) {
            "钱包总账不平，拒绝读取"
        }
    }

    private fun save(book: WalletBook) {
        unreadable?.let { throw IllegalStateException("钱包数据损坏，已保护原文件并禁止写入", it) }
        audit(book)
        val bytes = json.encodeToString(book).toByteArray(Charsets.UTF_8)
        val out = file.startWrite()
        try {
            out.write(bytes)
            out.fd.sync()
            file.finishWrite(out)
            current.value = book
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private fun ensureWritable() {
        unreadable?.let { throw IllegalStateException("本机钱包数据无法读取，请先备份，不能重置余额", it) }
    }
    private fun amount(cents: Long) {
        require(cents in 1L..MAX_TRANSACTION_CENTS) { "每次金额须在 0.01～1000.00 虚拟币之间" }
    }
    private fun debit(b: Map<Long, Long>, id: Long, cents: Long): Map<Long, Long> {
        require(id >= 0 && b.getOrDefault(id, 0L) >= cents) { "虚拟余额不足" }
        return b + (id to (b.getOrDefault(id, 0L) - cents))
    }
    private fun credit(b: Map<Long, Long>, id: Long, cents: Long): Map<Long, Long> =
        b + (id to (b.getOrDefault(id, 0L) + cents))

    /** Anti-loop guard: an AI may send up to three voluntary gifts per local day, max 10.00 each. */
    private fun guardAiSpend(book: WalletBook, from: Long, cents: Long, now: Long) {
        if (from == 0L) return
        require(cents <= 1000L) { "AI 单次主动赠送上限为 10.00 虚拟币" }
        val day = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val daily = book.movements.count { m ->
            m.from == from && m.kind in setOf("packet","transfer_pending") &&
                java.time.Instant.ofEpochMilli(m.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == day
        }
        require(daily < 3) { "这位 AI 今天主动赠送已达 3 次" }
    }

    suspend fun starter(): Unit = mutex.withLock {
        ensureWritable()
        require(!current.value.initialized) { "体验币已领取过，每台设备仅一次" }
        save(WalletBook(initialized = true, balances = mapOf(0L to STARTER_CENTS),
            movements = listOf(WalletMovement(kind = "starter", from = 0, to = 0, amount = STARTER_CENTS))))
    }

    suspend fun transfer(from: Long, to: Long, cents: Long, conversationId: Long = 0L): String = mutex.withLock {
        ensureWritable()
        amount(cents)
        require(from != to && from >= 0 && to >= 0) { "请选择另一位收款人" }
        val old = current.value
        val next = credit(debit(old.balances, from, cents), to, cents)
        val receipt = WalletMovement(kind = "transfer", from = from, to = to, amount = cents,
            conversationId = conversationId)
        save(old.copy(balances = next, movements =
            (old.movements + receipt).takeLast(200000)))
        receipt.id
    }

    /** Split shares exactly once at creation, reserve all coins before exposing packet id. */
    suspend fun sendPacket(from: Long, recipients: List<Long>, cents: Long, random: Boolean,
                           conversationId: Long = 0L, allowSenderClaim: Boolean = false): String = mutex.withLock {
        ensureWritable()
        amount(cents)
        require(recipients.isNotEmpty() && recipients.size <= 50 &&
            recipients.distinct().size == recipients.size &&
            recipients.all { it >= 0 }) { "红包领取人必须是有效角色" }
        require(from !in recipients || (allowSenderClaim && random && conversationId > 0L)) {
            "仅拼手气群红包支持自己领取"
        }
        require(!allowSenderClaim || (from in recipients && random && conversationId > 0L))
        require(cents >= recipients.size) { "每人至少 0.01 虚拟币" }
        val shares = split(cents, recipients.size, random)
        val packet = WalletPacket(sender = from, recipients = recipients,
            shares = shares, random = random, conversationId = conversationId,
            allowSenderClaim = allowSenderClaim)
        val old = current.value
        guardAiSpend(old, from, cents, System.currentTimeMillis())
        save(old.copy(balances = debit(old.balances, from, cents), packets = old.packets + packet,
            movements = (old.movements + WalletMovement(kind = "packet", from = from, to = -1,
                amount = cents, packetId = packet.id, conversationId = conversationId)).takeLast(200000)))
        packet.id
    }

    /** A recipient can claim once. Expires after 24h and refunds unclaimed escrow. */
    suspend fun claimPacket(packetId: String, recipient: Long, now: Long = System.currentTimeMillis()): Long =
        mutex.withLock {
            ensureWritable()
            val old = expire(current.value, now)
            if (old != current.value) save(old)
            val packet = old.packets.singleOrNull { it.id == packetId }
                ?: throw IllegalArgumentException("红包不存在")
            require(recipient in packet.recipients &&
                (recipient != packet.sender || packet.allowSenderClaim)) { "你不在领取名单中" }
            require(!packet.returned && now < packet.expiresAt) { "红包已过期" }
            require(packet.claims.none { it.recipient == recipient }) { "你已经领取过" }
            val share = packet.shares.getOrNull(packet.claims.size)
                ?: throw IllegalStateException("红包已领完")
            val newPacket = packet.copy(claims = packet.claims + WalletClaim(recipient, share, now))
            save(old.copy(balances = credit(old.balances, recipient, share),
                packets = old.packets.map { if (it.id == packet.id) newPacket else it },
                movements = (old.movements + WalletMovement(kind = "claim", from = packet.sender,
                    to = recipient, amount = share, packetId = packetId, at = now)).takeLast(200000)))
            share
        }

    /**
     * New transfers are held in escrow; the receiver must accept or decline within
     * 24 hours. Old v0.38.6 direct transfer movements remain valid, untouched.
     */
    suspend fun sendTransfer(from: Long, to: Long, cents: Long, conversationId: Long): String = mutex.withLock {
        ensureWritable()
        amount(cents)
        require(from >= 0 && to >= 0 && from != to && conversationId > 0)
        val old = current.value
        guardAiSpend(old, from, cents, System.currentTimeMillis())
        val transfer = WalletPendingTransfer(from = from, to = to, amount = cents,
            conversationId = conversationId)
        save(old.copy(balances = debit(old.balances, from, cents),
            transfers = old.transfers + transfer,
            movements = (old.movements + WalletMovement(kind = "transfer_pending", from = from,
                to = to, amount = cents, conversationId = conversationId))
                .takeLast(200000)))
        transfer.id
    }

    suspend fun decideTransfer(id: String, receiver: Long, accept: Boolean,
                               now: Long = System.currentTimeMillis()): Boolean = mutex.withLock {
        ensureWritable()
        val old = expire(current.value, now)
        if (old != current.value) save(old)
        val transfer = old.transfers.singleOrNull { it.id == id }
            ?: throw IllegalArgumentException("转账不存在")
        require(transfer.to == receiver && transfer.state == "pending") { "转账无法领取或已经处理" }
        require(now < transfer.expiresAt) { "转账已过期" }
        val next = transfer.copy(state = if (accept) "accepted" else "declined", completedAt = now)
        save(old.copy(
            balances = credit(old.balances, if (accept) receiver else transfer.from, transfer.amount),
            transfers = old.transfers.map { if (it.id == id) next else it },
            movements = (old.movements + WalletMovement(kind = if (accept) "transfer_accepted" else "transfer_declined",
                from = transfer.from, to = transfer.to, amount = transfer.amount,
                conversationId = transfer.conversationId, at = now)).takeLast(200000)))
        accept
    }

    suspend fun settleExpired(now: Long = System.currentTimeMillis()) = mutex.withLock {
        ensureWritable()
        val next = expire(current.value, now)
        if (next != current.value) save(next)
    }

    private fun expire(book: WalletBook, now: Long): WalletBook {
        var balances = book.balances
        val movements = book.movements.toMutableList()
        val packets = book.packets.map { p ->
            if (p.returned || now < p.expiresAt || p.remaining == 0L) p else {
                val refund = p.remaining
                balances = credit(balances, p.sender, refund)
                movements += WalletMovement(kind = "refund", from = -1, to = p.sender,
                    amount = refund, packetId = p.id, at = now)
                p.copy(returned = true)
            }
        }
        val transfers = book.transfers.map { t ->
            if (t.state != "pending" || now < t.expiresAt) t else {
                balances = credit(balances, t.from, t.amount)
                movements += WalletMovement(kind = "transfer_expired", from = t.from, to = t.to,
                    amount = t.amount, conversationId = t.conversationId, at = now)
                t.copy(state = "expired", completedAt = now)
            }
        }
        return book.copy(balances = balances, packets = packets, transfers = transfers,
            movements = movements.takeLast(200000))
    }

    companion object {
        const val STARTER_CENTS = 100_000L
        const val MAX_TRANSACTION_CENTS = 100_000L
        private val rng = SecureRandom()
        fun split(total: Long, count: Int, random: Boolean): List<Long> {
            require(count in 1..50 && total >= count)
            var remain = total
            val result = ArrayList<Long>(count)
            repeat(count) { index ->
                val left = count - index
                val share = when {
                    left == 1 -> remain
                    !random -> total / count + if (index < total % count) 1L else 0L
                    else -> 1L + rng.nextInt((remain - left + 1L).toInt()).toLong()
                }
                result += share
                remain -= share
            }
            require(remain == 0L && result.all { it > 0 })
            return result
        }
    }
}
